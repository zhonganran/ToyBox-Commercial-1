package com.byd.wjx.clustercard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ClusterCardRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(ClusterCardRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `text appearance settings persist independently for both cards`() {
        val repository = ClusterCardRepository(context)
        repository.setAdaptiveTextColorEnabled(ClusterCardSlot.PRIMARY, false)
        repository.setManualTextColor(ClusterCardSlot.PRIMARY, ClusterCardTextColor.BURGUNDY)
        repository.setTextOutlineEnabled(ClusterCardSlot.SECONDARY, true)
        repository.setBackgroundEnabled(ClusterCardSlot.PRIMARY, true)
        repository.setBackgroundColor(
            ClusterCardSlot.PRIMARY,
            ClusterCardBackgroundColor.MIDNIGHT_BLUE,
        )

        val reloaded = ClusterCardRepository(context)
        val primary = reloaded.current(ClusterCardSlot.PRIMARY)
        val secondary = reloaded.current(ClusterCardSlot.SECONDARY)

        assertFalse(primary.adaptiveTextColorEnabled)
        assertEquals(ClusterCardTextColor.BURGUNDY.id, primary.manualTextColorId)
        assertFalse(primary.textOutlineEnabled)
        assertTrue(primary.backgroundEnabled)
        assertEquals(ClusterCardBackgroundColor.MIDNIGHT_BLUE.id, primary.backgroundColorId)
        assertTrue(secondary.adaptiveTextColorEnabled)
        assertEquals(ClusterCardTextColor.DEFAULT.id, secondary.manualTextColorId)
        assertTrue(secondary.textOutlineEnabled)
        assertFalse(secondary.backgroundEnabled)
        assertEquals(ClusterCardBackgroundColor.DEFAULT.id, secondary.backgroundColorId)
    }
}
