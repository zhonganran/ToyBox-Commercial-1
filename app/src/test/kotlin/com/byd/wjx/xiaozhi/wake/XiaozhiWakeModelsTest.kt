package com.byd.wjx.xiaozhi.wake

import org.junit.Assert.assertEquals
import org.junit.Test

class XiaozhiWakeModelsTest {
    @Test
    fun usesXiaozhiAsDefaultWakePhrase() {
        assertEquals("小智", XiaozhiWakeConfig.DEFAULT_WAKE_PHRASE)
        assertEquals("小智", XiaozhiWakeConfig().phrase)
    }

    @Test
    fun normalizesDisplayTextWithoutDependingOnFullSpeechRecognition() {
        assertEquals("小智", normalizeWakeText(" 小 智！"))
        assertEquals("方宝", normalizeWakeText("方-宝"))
    }

    @Test
    fun retryBackoffIsBounded() {
        assertEquals(1_000L, exponentialBackoffDelay(1, 1_000L, 60_000L))
        assertEquals(4_000L, exponentialBackoffDelay(3, 1_000L, 60_000L))
        assertEquals(60_000L, exponentialBackoffDelay(12, 1_000L, 60_000L))
    }
}
