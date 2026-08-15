package io.soo.bueongi.core.facility.api

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.common.error.UnknownFacilityTypeException
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** 경로 변수의 시설 종류를 컨트롤러 진입 전에 [FacilityType] 으로 바꾼다. 컨트롤러는 이미 유효한 값만 받는다. */
@Component
class FacilityTypeConverter : Converter<String, FacilityType> {

    override fun convert(source: String): FacilityType =
        FacilityType.entries.find { it.cdnType == source }
            ?: throw UnknownFacilityTypeException(source, FacilityType.entries.map { it.cdnType })
}
