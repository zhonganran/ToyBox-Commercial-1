package com.byd.wjx.ui.pro

import org.junit.Assert.assertEquals
import org.junit.Test

class ProStatusBarTest {
    @Test fun `freshness labels use stable age buckets`() {
        val now = 10_000_000L

        assertEquals("等待更新", freshnessText(null, now))
        assertEquals("刚刚更新", freshnessText(now - 9_999L, now))
        assertEquals("10 秒前", freshnessText(now - 10_000L, now))
        assertEquals("2 分钟前", freshnessText(now - 120_000L, now))
        assertEquals("数据待刷新", freshnessText(now - 3_600_000L, now))
    }

    @Test fun `future timestamps degrade to just updated`() {
        assertEquals("刚刚更新", freshnessText(timestamp = 2_000L, now = 1_000L))
    }
}
