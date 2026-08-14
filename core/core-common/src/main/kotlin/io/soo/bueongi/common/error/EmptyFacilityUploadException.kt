package io.soo.bueongi.common.error

class EmptyFacilityUploadException(
    val received: Int,
) : RuntimeException("업로드에서 유효한 시설 좌표를 찾지 못했습니다(수신 ${received}행).")
