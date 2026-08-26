package com.byd.wjx.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupDestinationProviderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `backup destination uses requested directory and timestamped zip name`() {
        val directory = temporaryFolder.newFolder("WJX")

        val destination = BackupDestinationProvider.destinationIn(directory, 0L)

        assertEquals(directory, destination.parentFile)
        assertTrue(destination.name.matches(Regex("ToyBox_backup_\\d{8}_\\d{6}\\.zip")))
    }

    @Test
    fun `backup destination adds a suffix instead of overwriting an existing backup`() {
        val directory = temporaryFolder.newFolder("WJX")
        val first = BackupDestinationProvider.destinationIn(directory, 0L)
        assertTrue(first.createNewFile())

        val second = BackupDestinationProvider.destinationIn(directory, 0L)

        assertEquals(directory, second.parentFile)
        assertEquals(first.name.removeSuffix(".zip") + "_2.zip", second.name)
    }
}
