package com.byd.wjx.cluster

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyShortcutSettingsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(KeyShortcutSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `automation binding survives persistence`() {
        val settings = KeyShortcutSettings(context)
        settings.save(
            listOf(
                KeyShortcutBinding(
                    id = "automation-1",
                    keyCode = 24,
                    pressType = KeyShortcutPressType.Double,
                    actionType = KeyShortcutActionType.Automation,
                    automationButtonId = 4,
                )
            )
        )

        val restored = KeyShortcutSettings(context).load().single()
        assertEquals(KeyShortcutActionType.Automation, restored.actionType)
        assertEquals(4, restored.automationButtonId)
        assertTrue(restored.isConfigured)
    }

    @Test
    fun `legacy binding without automation slot defaults safely`() {
        context.getSharedPreferences(KeyShortcutSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "bindings_json",
                """[{"id":"legacy","keyCode":25,"pressType":"short","actionType":"play_sound","soundPath":"/a.mp3","enabled":true}]""",
            )
            .commit()

        val restored = KeyShortcutSettings(context).load().single()
        assertEquals(1, restored.automationButtonId)
        assertEquals(KeyShortcutActionType.PlaySound, restored.actionType)
    }

    @Test
    fun `xiaozhi binding survives persistence without extra payload`() {
        val settings = KeyShortcutSettings(context)
        settings.save(
            listOf(
                KeyShortcutBinding(
                    id = "xiaozhi-1",
                    keyCode = 85,
                    pressType = KeyShortcutPressType.Long,
                    actionType = KeyShortcutActionType.Xiaozhi,
                ),
            ),
        )

        val restored = KeyShortcutSettings(context).load().single()
        assertEquals(KeyShortcutActionType.Xiaozhi, restored.actionType)
        assertTrue(restored.isConfigured)
    }
}
