package com.byd.wjx.xiaozhi.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class XiaozhiWebSocketClientTest {
    @Test
    fun `protocol-only close codes fall back to normal closure`() {
        listOf(1004, 1005, 1006, 1015).forEach { code ->
            assertEquals(1000, normalizeXiaozhiCloseCode(code))
        }
    }

    @Test
    fun `valid close codes are preserved`() {
        listOf(1000, 1001, 1011, 3000, 4999).forEach { code ->
            assertEquals(code, normalizeXiaozhiCloseCode(code))
        }
    }

    @Test
    fun `out of range close codes fall back to normal closure`() {
        listOf(Int.MIN_VALUE, 0, 999, 5000, Int.MAX_VALUE).forEach { code ->
            assertEquals(1000, normalizeXiaozhiCloseCode(code))
        }
    }
}
