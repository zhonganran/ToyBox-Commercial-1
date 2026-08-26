package com.byd.wjx.cluster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClusterProjectionPlannerTest {
    @Test fun `independent mode keeps configured window geometry and render scale together`() {
        assertEquals(
            ProjectionSurfacePlan(
                geo = ClusterGeometry(width = 960, height = 360, xOffset = 960, yOffset = 180),
                render = RenderPlan(bufferWidth = 960, bufferHeight = 360, densityDpi = 240),
                surfaceWidth = 960,
                surfaceHeight = 360,
                surfaceX = 960,
                surfaceY = 180,
            ),
            ClusterProjectionPlanner.independent(
                mode = ClusterMode.FULLSCREEN,
                clusterWidth = 1920,
                clusterHeight = 720,
                clusterDensityDpi = 320,
                widthPct = 50,
                heightPct = 50,
                offsetXPct = 100,
                offsetYPct = 50,
                scalePct = 75,
            ),
        )
    }

    @Test fun `independent mode has no surface plan while projection is off`() {
        assertNull(
            ClusterProjectionPlanner.independent(
                mode = ClusterMode.OFF,
                clusterWidth = 1280,
                clusterHeight = 480,
                clusterDensityDpi = 320,
                widthPct = 100,
                heightPct = 100,
                offsetXPct = 50,
                offsetYPct = 50,
                scalePct = 100,
            ),
        )
    }

    @Test fun `duplicate mode crop-fills a wide cluster from a taller source`() {
        assertEquals(
            ProjectionSurfacePlan(
                geo = ClusterGeometry(width = 1280, height = 480, xOffset = 0, yOffset = 0),
                render = RenderPlan(bufferWidth = 1280, bufferHeight = 720, densityDpi = 320),
                surfaceWidth = 1280,
                surfaceHeight = 720,
                surfaceX = 0,
                surfaceY = -120,
            ),
            ClusterProjectionPlanner.duplicate(
                clusterWidth = 1280,
                clusterHeight = 480,
                clusterDensityDpi = 320,
                sourceWidth = 1920,
                sourceHeight = 1080,
            ),
        )
    }

    @Test fun `duplicate mode crop-fills a cluster from a wider source`() {
        assertEquals(
            ProjectionSurfacePlan(
                geo = ClusterGeometry(width = 1280, height = 480, xOffset = 0, yOffset = 0),
                render = RenderPlan(bufferWidth = 1707, bufferHeight = 480, densityDpi = 240),
                surfaceWidth = 1707,
                surfaceHeight = 480,
                surfaceX = -213,
                surfaceY = 0,
            ),
            ClusterProjectionPlanner.duplicate(
                clusterWidth = 1280,
                clusterHeight = 480,
                clusterDensityDpi = 240,
                sourceWidth = 2560,
                sourceHeight = 720,
            ),
        )
    }

    @Test fun `duplicate mode falls back to cluster aspect when source size is invalid`() {
        assertEquals(
            ProjectionSurfacePlan(
                geo = ClusterGeometry(width = 1280, height = 480, xOffset = 0, yOffset = 0),
                render = RenderPlan(bufferWidth = 1280, bufferHeight = 480, densityDpi = 320),
                surfaceWidth = 1280,
                surfaceHeight = 480,
                surfaceX = 0,
                surfaceY = 0,
            ),
            ClusterProjectionPlanner.duplicate(
                clusterWidth = 1280,
                clusterHeight = 480,
                clusterDensityDpi = 320,
                sourceWidth = 0,
                sourceHeight = 0,
            ),
        )
    }
}
