# syntax=docker/dockerfile:1
# bueongi 백엔드(Kotlin/Spring Boot 멀티모듈, 포트 8119) 컨테이너 이미지.
#
# 빌드 컨텍스트는 저장소 루트다(cdn/ 정적 데이터를 함께 굽기 위해):
#   docker build -f backend/Dockerfile -t bueongi-service .
#
# 부팅 모듈은 core:core-api 하나뿐이라 루트 `bootJar` 가 정확히 그 모듈만 패키징한다
# (라이브러리 모듈들은 bootJar 비활성). cdn/ 을 이미지에 포함하는 이유: 안심시설 점수 계산
# 입력(FacilityRepository)과 프론트 정적 데이터(/cdn HTTP 서빙)가 같은 디렉터리라서,
# 이미지 하나로 외부 볼륨 없이 자립 동작한다. 월 1회 파이프라인이 cdn/ 을 갱신해
# main 에 커밋하면 docker-publish 워크플로우가 새 데이터로 이미지를 다시 발행한다.

# ---- 빌드 단계: gradle wrapper 로 bootJar 생성 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 한글 테스트/리소스 파일명(CCTV정보.csv 등) 처리에 UTF-8 로케일 필수.
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

# 래퍼·빌드 스크립트만 먼저 복사 → 의존성 레이어 캐시(소스 변경 시 재다운로드 방지).
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
# 모듈 build 파일들(의존 선언)도 캐시 레이어에 포함.
COPY backend/core/core-enum/build.gradle.kts ./core/core-enum/
COPY backend/core/core-api/build.gradle.kts ./core/core-api/
COPY backend/storage/db-core/build.gradle.kts ./storage/db-core/
COPY backend/clients/client-tmap/build.gradle.kts ./clients/client-tmap/
COPY backend/clients/client-kakao/build.gradle.kts ./clients/client-kakao/
COPY backend/clients/client-datagokr/build.gradle.kts ./clients/client-datagokr/
COPY backend/support/logging/build.gradle.kts ./support/logging/
COPY backend/support/monitoring/build.gradle.kts ./support/monitoring/
RUN ./gradlew --no-daemon --version \
    && (./gradlew --no-daemon :core:core-api:dependencies > /dev/null 2>&1 || true)

COPY backend/core ./core
COPY backend/storage ./storage
COPY backend/clients ./clients
COPY backend/support ./support
# 테스트는 CI(backend-ci)가 이미지 발행 전에 게이트한다 — 여기선 패키징만.
RUN ./gradlew --no-daemon bootJar -x test

# ---- 실행 단계: JRE + 애플리케이션 + cdn 데이터 ----
FROM eclipse-temurin:21-jre
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 TZ=Asia/Seoul
# 헬스체크용 curl (temurin JRE 이미지에는 없음).
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --home /app bueongi
WORKDIR /app

COPY --from=build /workspace/core/core-api/build/libs/*.jar app.jar
# 안심시설 정적 데이터(파이프라인 산출) — 점수 계산 입력 + /cdn HTTP 서빙 원본.
COPY cdn /app/cdn
ENV BUEONGI_FACILITY_DATA_DIR=/app/cdn

USER bueongi
EXPOSE 8119
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fs http://localhost:8119/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
