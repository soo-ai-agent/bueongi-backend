package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.safemap.Epsg5174
import io.soo.bueongi.client.safemap.Epsg5179
import io.soo.bueongi.client.safemap.Wgs84Point
import io.soo.bueongi.storage.db.core.facility.CCTV_SECURITY_PURPOSES
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 공공데이터 표준 CSV(헤더 있음)를 [FacilityCsvSpec] 규격으로 파싱해 저장 모델 목록으로 옮긴다.
 *
 * - 인코딩: UTF-8 BOM 이면 UTF-8, 아니면 UTF-8 엄격 디코딩을 시도하고 실패하면 CP949(MS949)로 폴백한다
 *   (표준데이터는 CP949 가 흔하다).
 * - CSV: 따옴표로 감싼 필드(내부 콤마·개행·이스케이프 `""`)를 처리하는 RFC 4180 형 토크나이저를 쓴다.
 * - 매핑: 헤더명→인덱스로 각 필드를 찾고, 좌표는 반드시 [GeoPoint.orNull] 을 통과시켜 불변식을 지킨다.
 *   좌표가 없거나(주소만 있는 행) 불량인 행은 조용히 skip 한다(에러 아님 — skipped 로 집계).
 */
@Component
class FacilityCsvParser {

    /**
     * [bytes] 를 [spec] 규격으로 파싱한다. 헤더에 `위도/경도` 가 있으면 WGS84 로, 없으면 spec 좌표계의 X/Y 로 읽는다.
     * 반환의 received 는 (헤더 제외) 데이터 행 수, points 는 유효 시설, skipped = received - points.size.
     */
    fun parse(bytes: ByteArray, spec: FacilityCsvSpec): FacilityCsvParseResult {
        val text: String = decode(bytes)
        val records: List<List<String>> = tokenizeCsv(text)
        val headerPos: Int = records.indexOfFirst { row -> row.any { it.isNotBlank() } }
        if (headerPos < 0) {
            return FacilityCsvParseResult(spec.coordSystem, received = 0, points = emptyList())
        }

        val headerIndex: Map<String, Int> = buildHeaderIndex(records[headerPos])
        val dataRows: List<List<String>> = records.drop(headerPos + 1)
            .filter { row -> row.any { it.isNotBlank() } }

        val latIdx: Int? = firstIndexOf(headerIndex, spec.latHeaders)
        val lngIdx: Int? = firstIndexOf(headerIndex, spec.lngHeaders)
        val useWgs84: Boolean = latIdx != null && lngIdx != null
        val effectiveCoordSystem: FacilityCoordSystem = if (useWgs84) {
            FacilityCoordSystem.WGS84
        } else {
            spec.coordSystem
        }

        val xIdx: Int? = firstIndexOf(headerIndex, spec.xHeaders)
        val yIdx: Int? = firstIndexOf(headerIndex, spec.yHeaders)
        val nameIdx: Int? = firstIndexOf(headerIndex, spec.nameHeaders)
        val addressIdx: Int? = firstIndexOf(headerIndex, spec.addressHeaders)
        val purposeIdx: Int? = firstIndexOf(headerIndex, spec.purposeHeaders)
        val cameraIdx: Int? = firstIndexOf(headerIndex, spec.cameraCountHeaders)
        val phoneIdx: Int? = firstIndexOf(headerIndex, spec.phoneHeaders)

        val points: List<FacilityPoint> = dataRows.mapNotNull { row ->
            val geo: GeoPoint = if (useWgs84) {
                GeoPoint.orNull(row.field(latIdx)?.toDoubleOrNull(), row.field(lngIdx)?.toDoubleOrNull())
            } else {
                projectXy(row.field(xIdx)?.toDoubleOrNull(), row.field(yIdx)?.toDoubleOrNull(), spec.coordSystem)
            } ?: return@mapNotNull null

            val purpose: String? = row.field(purposeIdx)
            if (spec.securityPurposeFilter && purpose !in CCTV_SECURITY_PURPOSES) {
                return@mapNotNull null
            }

            FacilityPoint(
                type = spec.type,
                lat = geo.lat,
                lng = geo.lng,
                name = row.field(nameIdx),
                purpose = purpose,
                cameraCount = row.field(cameraIdx)?.toIntOrNull()?.takeIf { it > 0 },
                address = row.field(addressIdx),
                phone = row.field(phoneIdx),
            )
        }
        return FacilityCsvParseResult(effectiveCoordSystem, received = dataRows.size, points = points)
    }

    /** 투영 X/Y → WGS84(GeoPoint). WGS84 좌표계인데 X/Y 만 있으면 변환 불가(null → skip). */
    private fun projectXy(x: Double?, y: Double?, coordSystem: FacilityCoordSystem): GeoPoint? {
        if (x == null || y == null) {
            return null
        }
        val wgs: Wgs84Point? = when (coordSystem) {
            FacilityCoordSystem.EPSG5179 -> Epsg5179.toWgs84(x, y)
            FacilityCoordSystem.EPSG5174 -> Epsg5174.toWgs84(x, y)
            FacilityCoordSystem.WGS84 -> null
        }
        return GeoPoint.orNull(wgs?.lat, wgs?.lng)
    }

    /** 헤더명(trim)→인덱스. 같은 이름이 여러 번이면 첫 열을 쓴다. */
    private fun buildHeaderIndex(header: List<String>): Map<String, Int> {
        val map: LinkedHashMap<String, Int> = LinkedHashMap<String, Int>()
        header.forEachIndexed { index, raw ->
            val key = raw.trim()
            if (key.isNotEmpty() && !map.containsKey(key)) {
                map[key] = index
            }
        }
        return map
    }

    /** 후보 헤더명 중 먼저 존재하는 것의 열 인덱스. 없으면 null. */
    private fun firstIndexOf(headerIndex: Map<String, Int>, candidates: List<String>): Int? =
        candidates.firstNotNullOfOrNull { headerIndex[it] }

    /** 인덱스 위치의 값(trim), 인덱스가 null/범위 밖/빈 값이면 null. */
    private fun List<String>.field(index: Int?): String? {
        if (index == null || index >= size) {
            return null
        }
        return this[index].trim().takeIf { it.isNotEmpty() }
    }

    /** UTF-8 BOM → UTF-8, 아니면 UTF-8 엄격 시도 후 실패 시 CP949(MS949) 폴백. */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return decodeStrict(bytes, Charsets.UTF_8) ?: String(bytes, CP949)
    }

    /** [charset] 으로 엄격 디코딩(불량 바이트면 예외). 실패하면 null(폴백 유도). */
    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: Exception) {
        null
    }

    /** RFC 4180 형 CSV 토크나이저 — 따옴표 필드(내부 콤마·개행·`""` 이스케이프)를 처리하고 레코드(행) 목록을 만든다. */
    private fun tokenizeCsv(text: String): List<List<String>> {
        val records: ArrayList<List<String>> = ArrayList<List<String>>()
        var record: ArrayList<String> = ArrayList<String>()
        val field: StringBuilder = StringBuilder()
        var inQuotes: Boolean = false
        var i: Int = 0
        val n: Int = text.length
        while (i < n) {
            val c: Char = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        record.add(field.toString())
                        field.setLength(0)
                    }
                    '\n' -> {
                        record.add(field.toString())
                        records.add(record)
                        record = ArrayList()
                        field.setLength(0)
                    }
                    '\r' -> {
                        // \r\n 이면 \r 은 무시(다음 \n 이 행을 끊는다). 단독 \r(구 Mac)이면 여기서 행을 끊는다.
                        if (i + 1 >= n || text[i + 1] != '\n') {
                            record.add(field.toString())
                            records.add(record)
                            record = ArrayList()
                            field.setLength(0)
                        }
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        // 마지막 개행 없는 행 마무리(개행으로 이미 끝났으면 field/record 가 비어 추가하지 않는다).
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record.add(field.toString())
            records.add(record)
        }
        return records
    }

    private companion object {
        /** 표준데이터 흔한 인코딩(CP949=MS949, EUC-KR 윈도 확장 superset). */
        val CP949: Charset = Charset.forName("MS949")
    }
}

/**
 * CSV 파싱 결과. [coordSystem] 은 실제 사용된 좌표계(헤더에 위도/경도가 있으면 WGS84, 없으면 spec 좌표계),
 * [received] 는 데이터 행 수, [points] 는 유효 시설. [skipped] 는 좌표 없음·불량·비방범 CCTV 등으로 버려진 행 수.
 */
data class FacilityCsvParseResult(
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val points: List<FacilityPoint>,
) {
    val skipped: Int get() = received - points.size
}
