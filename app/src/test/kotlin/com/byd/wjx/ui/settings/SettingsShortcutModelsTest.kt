package com.byd.wjx.ui.settings

import com.byd.wjx.cluster.KeyShortcutActionType
import com.byd.wjx.cluster.KeyShortcutBinding
import com.byd.wjx.cluster.KeyShortcutPressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsShortcutModelsTest {
    @Test fun `binding editor round trip preserves shortcut definition`() {
        val binding = KeyShortcutBinding(
            id = "shortcut-1",
            keyCode = 313,
            pressType = KeyShortcutPressType.Double,
            actionType = KeyShortcutActionType.PlaySound,
            soundPath = "/tmp/sound.mp3",
            soundName = "sound",
            enabled = false,
        )

        assertEquals(binding, binding.toEditorState().toBinding())
    }

    @Test fun `shortcut validation requires key and sound only when applicable`() {
        assertEquals("请先识别按键", validateKeyShortcutEditor(KeyShortcutEditorState()))
        assertEquals(
            "请选择一个音效",
            validateKeyShortcutEditor(KeyShortcutEditorState(keyCode = 313)),
        )
        assertNull(
            validateKeyShortcutEditor(
                KeyShortcutEditorState(
                    keyCode = 313,
                    actionType = KeyShortcutActionType.RealtimeHailing,
                ),
            ),
        )
    }
}
