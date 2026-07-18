package com.byd.wjx.xiaozhi.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XiaozhiWakePhraseCompilerTest {
    @Test
    fun validatesTwoToSixChineseCharacters() {
        assertEquals("唤醒词至少需要 2 个汉字", validateLocalWakePhrase("小"))
        assertNull(validateLocalWakePhrase("小智"))
        assertNull(validateLocalWakePhrase("超级玩具箱"))
        assertEquals("为了保证唤醒速度，唤醒词不能超过 6 个汉字", validateLocalWakePhrase("超级玩具箱助手"))
        assertEquals("本地唤醒词目前只支持中文汉字", validateLocalWakePhrase("小智2"))
    }

    @Test
    fun parsesTokenTableLinesAndSkipsControlTokens() {
        assertEquals("zh", parseTokenLine("zh 10"))
        assertEquals("iǎo", parseTokenLine("iǎo    115"))
        assertNull(parseTokenLine("<blk> 0"))
        assertNull(parseTokenLine("missing-id"))
    }

    @Test
    fun segmentsPinyinUsingLongestAvailableTokenFirst() {
        assertEquals(
            listOf("x", "iǎo"),
            segmentPinyinSyllable("xiǎo", setOf("x", "i", "iǎo", "ǎo")),
        )
        assertEquals(
            listOf("zh", "ì"),
            segmentPinyinSyllable("zhì", setOf("z", "h", "zh", "ì")),
        )
    }

    @Test
    fun rejectsSyllablesThatCannotBeFullyTokenized() {
        assertNull(segmentPinyinSyllable("zhì", setOf("zh")))
    }
}
