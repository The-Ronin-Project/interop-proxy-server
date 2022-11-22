package com.projectronin.interop.fhir.ronin.util

import com.projectronin.interop.common.enums.CodedEnum
import com.projectronin.interop.fhir.r4.datatype.primitive.Code

inline fun <reified T> Code?.asEnum(): T? where T : Enum<T>, T : CodedEnum<T> =
    this?.let { code -> code.value?.let { CodedEnum.byCode<T>(it) } }
