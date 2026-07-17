package com.byd.wjx.xiaozhi.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiWakeModelsTest {
    @Test
    fun usesXiaozhiAsDefaultWakePhrase() {
        assertEquals("小智", XiaozhiWakeConfig.DEFAULT_WAKE_PHRASE)
        assertEquals("小智", XiaozhiWakeConfig().phrase)
    }

    @Test
    fun matchesPhraseIgnoringSpacesAndPunctuation() {
        assertTrue(matchesWakePhrase("小智！", "小智"))
        assertTrue(matchesWakePhrase("方宝", "方宝"))
        assertTrue(matchesWakePhrase("方宝方宝", "方宝"))
        assertFalse(matchesWakePhrase("你好 方宝 帮我开空调", "方宝"))
        assertFalse(matchesWakePhrase("方豹", "方宝"))
    }

    @Test
    fun rejectsLowConfidenceCandidatesWhenEngineProvidesScores() {
        assertEquals(
            "小智",
            selectWakeMatch(listOf("小智"), floatArrayOf(0.82f), "小智"),
        )
        assertNull(selectWakeMatch(listOf("小智"), floatArrayOf(0.21f), "小智"))
    }

    @Test
    fun validatesCustomWakePhraseLength() {
        assertEquals("唤醒词至少需要 2 个字或字符", validateWakePhrase("小"))
        assertNull(validateWakePhrase("方宝"))
        assertEquals("唤醒词不能超过 16 个字或字符", validateWakePhrase("一二三四五六七八九十一二三四五六七八"))
    }

    @Test
    fun retryBackoffIsBounded() {
        assertEquals(1_000L, exponentialBackoffDelay(1, 1_000L, 60_000L))
        assertEquals(4_000L, exponentialBackoffDelay(3, 1_000L, 60_000L))
        assertEquals(60_000L, exponentialBackoffDelay(12, 1_000L, 60_000L))
    }
}
