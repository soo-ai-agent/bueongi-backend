package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/** `POST /share/create` 응답: 발급된 토큰 + 외부 메신저로 공유할 URL + 상한 만료 시각. */
data class CreateShareResponse(
    /** 공유 URL에 담기는 읽기 전용 토큰(보호자가 위치를 조회). */
    val token: String,
    /**
     * 위치 쓰기/종료에 필요한 비밀(앱만 보관, 매 전송/종료에 본문으로 전송). 공유 URL에는 포함하지 않는다.
     * 이 분리로 공유 URL이 유출돼도 조회만 가능하고 위치 조작·임의 종료는 불가하다.
     */
    @JsonProperty("owner_secret")
    val ownerSecret: String,
    @JsonProperty("share_url")
    val shareUrl: String,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)
