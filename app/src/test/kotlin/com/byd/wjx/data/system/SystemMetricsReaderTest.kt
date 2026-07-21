package com.byd.wjx.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SystemMetricsReaderTest {

    @Test
    fun `meminfo parser returns capacity and usage details`() {
        val metrics = parseMemInfo(
            """
            MemTotal:        8388608 kB
            MemFree:          524288 kB
            MemAvailable:    2097152 kB
            Buffers:          131072 kB
            Cached:           524288 kB
            Active:          3145728 kB
            Inactive:        1048576 kB
            Dirty:              2048 kB
            Slab:             262144 kB
            SwapTotal:       2097152 kB
            SwapFree:        1048576 kB
            CmaTotal:         262144 kB
            """.trimIndent(),
        )

        requireNotNull(metrics)
        assertEquals(8192L, metrics.totalMb)
        assertEquals(2048L, metrics.availableMb)
        assertEquals(75, metrics.usedPercent)
        assertEquals(512L, metrics.cachedMb)
        assertEquals(128L, metrics.buffersMb)
        assertEquals(2048L, metrics.swapTotalMb)
        assertEquals(1024L, metrics.swapFreeMb)
        assertEquals(256L, metrics.cmaMb)
        assertNull(metrics.zramMb)
    }

    @Test
    fun `cpu usage is calculated from two proc stat samples`() {
        val previous = parseCpuStat(
            """
            cpu  100 0 50 850 0 0 0 0
            cpu0 50 0 25 425 0 0 0 0
            """.trimIndent(),
        )
        val current = parseCpuStat(
            """
            cpu  150 0 100 950 0 0 0 0
            cpu0 75 0 50 475 0 0 0 0
            """.trimIndent(),
        )

        val usage = calculateCpuUsage(previous, current)

        assertEquals(50, usage["cpu"])
        assertEquals(50, usage["cpu0"])
    }

    @Test
    fun `first cpu sample has no synthetic usage`() {
        val current = parseCpuStat("cpu 10 0 10 80 0 0 0 0")

        assertEquals(emptyMap<String, Int>(), calculateCpuUsage(null, current))
    }

    @Test
    fun `cpu parser does not double count guest counters`() {
        val parsed = parseCpuStat("cpu 100 10 20 800 30 5 5 10 40 2")

        assertEquals(980L, parsed.getValue("cpu").total)
        assertEquals(830L, parsed.getValue("cpu").idle)
    }

    @Test
    fun `load average parser reads the three kernel windows`() {
        val load = parseLoadAverage("0.42 1.25 2.50 3/456 789")

        assertEquals(0.42, load[0] ?: -1.0, 0.0001)
        assertEquals(1.25, load[1] ?: -1.0, 0.0001)
        assertEquals(2.50, load[2] ?: -1.0, 0.0001)
    }

    @Test
    fun `diskstats keeps whole devices and calculates throughput`() {
        val previous = parseDiskStats(
            """
            259 0 mmcblk0 100 0 1024 0 200 0 2048 0 0 0 0 0
            259 1 mmcblk0p1 100 0 800 0 100 0 900 0 0 0 0 0
            7 0 loop0 1 0 10 0 1 0 10 0 0 0 0 0
            """.trimIndent(),
        )
        val current = parseDiskStats(
            """
            259 0 mmcblk0 120 0 3072 0 240 0 6144 0 0 0 0 0
            259 1 mmcblk0p1 120 0 1600 0 120 0 1800 0 0 0 0 0
            8 0 sda 10 0 2048 0 20 0 4096 0 0 0 0 0
            8 1 sda1 10 0 1024 0 20 0 2048 0 0 0 0 0
            """.trimIndent(),
        )

        assertFalse(current.containsKey("mmcblk0p1"))
        assertFalse(current.containsKey("sda1"))
        val mmc = calculateDiskIo(previous, current, elapsedMs = 2_000L)
            .first { it.device == "mmcblk0" }

        assertEquals(1L, mmc.readMb)
        assertEquals(3L, mmc.writeMb)
        assertEquals(0.5, mmc.readMbPerSec ?: -1.0, 0.0001)
        assertEquals(1.0, mmc.writeMbPerSec ?: -1.0, 0.0001)
    }
}
