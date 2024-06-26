package com.projectronin.interop.proxy.server.util

import com.projectronin.interop.common.enums.CodedEnum
import com.projectronin.interop.fhir.r4.datatype.primitive.Code

inline fun <reified T> Code?.asEnum() where T : Enum<T>, T : CodedEnum<T> = this?.value?.let { CodedEnum.byCode<T>(it) }
