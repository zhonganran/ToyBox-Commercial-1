package com.byd.wjx.helper

import java.lang.reflect.InvocationTargetException

internal fun throwableSummary(error: Throwable): String {
    val unwrapped = (error as? InvocationTargetException)?.targetException ?: error
    return "${unwrapped.javaClass.simpleName}:${unwrapped.message.orEmpty()}".take(220)
}
