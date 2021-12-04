package com.android.build.gradle.integration.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectInfo
import com.google.common.truth.Truth
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
        val modelContainer = project.modelV2().fetchModels(variantName = "debug").container.getProject(":app")
        val androidProject = modelContainer.androidProject
        assertNotNull("Module app null-check", androidProject)

        val variants = androidProject!!.variants
        assertEquals("Variant count", 2, variants.size.toLong())

        val mainInfo = androidProject.getVariantByName("release").mainArtifact
        assertNotNull("Main Artifact null-check", mainInfo)

        val variantDependencies = modelContainer.variantDependencies
        assertNotNull("Dependencies null-check", variantDependencies)

        val mainArtifactDependencies = variantDependencies!!.mainArtifact

        Truth.assertThat(mainArtifactDependencies.compileDependencies).hasSize(1)
        val graphItem = mainArtifactDependencies.compileDependencies.single()
        val dependency =
            variantDependencies.libraries[graphItem.key]
                ?: throw RuntimeException("Failed to find Library instead for key '${graphItem.key}'")

        Truth.assertThat(dependency.type).isEqualTo(LibraryType.PROJECT)
        Truth.assertThat(dependency.projectInfo).isNotNull()
        Truth.assertThat(dependency.projectInfo!!.projectPath).isEqualTo(":util")
    }
}

