package com.android.build.gradle.integration.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.getVariantByName
import java.io.IOException
import org.junit.Rule
import org.junit.Test

/** Assemble tests for customArtifactDep.  */
class CustomArtifactDepTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestProject("customArtifactDep").create()

    @Test
    @Throws(IOException::class)
    fun testModel() {
        val appModel = project.model().fetchAndroidProjects().onlyModelMap[":app"]
        assertNotNull("Module app null-check", appModel)

        val variants = appModel!!.variants
        assertEquals("Variant count", 2, variants.size.toLong())

        val mainInfo = appModel.getVariantByName("release").mainArtifact
        assertNotNull("Main Artifact null-check", mainInfo)

        val dependencyGraph = mainInfo.dependencyGraphs
        assertNotNull("Dependencies null-check", dependencyGraph)

        assertEquals("jar dep count", 1, dependencyGraph.compileDependencies.size)
    }
}

