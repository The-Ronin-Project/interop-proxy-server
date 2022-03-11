package com.projectronin.interop.proxy.server.util

import io.mockk.mockk

inline fun <reified T : Any> relaxedMockk(block: T.() -> Unit = {}) = mockk<T>(relaxed = true, block = block)
