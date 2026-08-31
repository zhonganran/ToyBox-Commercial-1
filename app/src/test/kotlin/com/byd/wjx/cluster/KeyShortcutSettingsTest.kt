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
    fun `unknown action binding is discarded`() {
        context.getSharedPreferences(KeyShortcutSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "bindings_json",
                """[{"id":"retired-1","keyCode":24,"pressType":"double","actionType":"retired_action","enabled":true}]""",
            )
            .commit()

        assertTrue(KeyShortcutSettings(context).load().isEmpty())
    }

    @Test
    fun `sound binding without optional name loads safely`() {
        context.getSharedPreferences(KeyShortcutSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "bindings_json",
                """[{"id":"sound","keyCode":25,"pressType":"short","actionType":"play_sound","soundPath":"/a.mp3","enabled":true}]""",
            )
            .commit()

        val restored = KeyShortcutSettings(context).load().single()
        assertEquals(KeyShortcutActionType.PlaySound, restored.actionType)
    }

    @Test
    fun `rear stream toggle binding round trips`() {
        val settings = KeyShortcutSettings(context)
        settings.save(
            listOf(
                KeyShortcutBinding(
                    id = "rear-stream",
                    keyCode = 313,
                    actionType = KeyShortcutActionType.RearStream,
                ),
            ),
        )

        val restored = settings.loadEnabled().single()
        assertEquals(KeyShortcutActionType.RearStream, restored.actionType)
        assertTrue(restored.isConfigured)
    }

}
