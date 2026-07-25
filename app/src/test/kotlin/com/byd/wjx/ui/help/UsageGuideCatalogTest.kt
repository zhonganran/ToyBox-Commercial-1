package com.byd.wjx.ui.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuideCatalogTest {

    @Test
    fun sectionIdsAreUniqueAndStepsArePresent() {
        assertEquals(usageGuideSections.size, usageGuideSections.map { it.id }.distinct().size)
        assertTrue(usageGuideSections.all { it.title.isNotBlank() })
        assertTrue(usageGuideSections.all { it.summary.isNotBlank() })
        assertTrue(usageGuideSections.all { it.steps.isNotEmpty() })
        assertTrue(usageGuideSections.all { section -> section.steps.all(String::isNotBlank) })
    }

    @Test
    fun everyCategoryHasAtLeastOneSection() {
        UsageGuideCategory.entries.forEach { category ->
            assertTrue(
                "Missing usage guide section for ${category.name}",
                usageGuideSections.any { it.category == category },
            )
        }
    }

    @Test
    fun searchFindsLogsMirrorAndLyrics() {
        assertTrue(filterUsageGuideSections("wjx.log", null).any { it.id == "data_update_logs" })
        assertTrue(filterUsageGuideSections("电子后视镜", null).any { it.id == "electronic_mirror" })
        assertTrue(filterUsageGuideSections("歌词", null).any { it.id == "lyrics" })
    }

    @Test
    fun categoryFilterOnlyReturnsRequestedCategory() {
        val results = filterUsageGuideSections("", UsageGuideCategory.Mirror)

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.category == UsageGuideCategory.Mirror })
    }

    @Test
    fun searchIgnoresSurroundingWhitespaceAndCase() {
        val results = filterUsageGuideSections("  ADB  ", null)

        assertTrue(results.any { it.id == "first_start" })
    }
}
