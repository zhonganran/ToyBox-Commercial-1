package com.byd.wjx.xiaozhi.wake

import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiRecognitionServiceResolverTest {
    @Test
    fun `vehicle recognizer is preferred over generic services`() {
        val byd = recognitionServiceRank(
            packageName = "com.byd.autovoice",
            className = "com.byd.autovoice.RecognitionService",
        )
        val google = recognitionServiceRank(
            packageName = "com.google.android.googlequicksearchbox",
            className = "RecognitionService",
        )
        val generic = recognitionServiceRank(
            packageName = "com.example.speech",
            className = "RecognitionService",
        )

        assertTrue(byd < google)
        assertTrue(google < generic)
    }
}
