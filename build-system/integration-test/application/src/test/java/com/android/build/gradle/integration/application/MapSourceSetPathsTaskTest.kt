package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests for [MapSourceSetPathsTask]
 */
class MapSourceSetPathsTaskTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
            .fromTestProject("flavors")
            .create()

    @Test
    fun `test should write file map`() {
        project.executor()
                .with(BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP, true)
                .run("assembleF1FaDebug")
        val filePathMapsDir = FileUtils.join(project.intermediatesDir, InternalArtifactType
                .SOURCE_SET_PATH_MAP.getFolderName())
        val sourceSetMap = FileUtils.join(filePathMapsDir, "f1FaDebug", "file-map.txt")
        val projectDir = project.projectDir.absolutePath
        val expectedContents = """
            com.android.tests.flavors-f1Fa-0 $projectDir/build/generated/res/pngs/f1Fa/debug
            com.android.tests.flavors-f1Fa-1 $projectDir/build/generated/res/resValues/f1Fa/debug
            com.android.tests.flavors-f1Fa-2 $projectDir/build/generated/res/rs/f1Fa/debug
            com.android.tests.flavors-mergeF1FaDebugResources-3 $projectDir/build/intermediates/incremental/mergeF1FaDebugResources/merged.dir
            com.android.tests.flavors-mergeF1FaDebugResources-4 $projectDir/build/intermediates/incremental/mergeF1FaDebugResources/stripped.dir
            com.android.tests.flavors-merged_res-5 $projectDir/build/intermediates/merged_res/f1FaDebug
            com.android.tests.flavors-debug-6 $projectDir/src/debug/res
            com.android.tests.flavors-f1-7 $projectDir/src/f1/res
            com.android.tests.flavors-f1Fa-8 $projectDir/src/f1Fa/res
            com.android.tests.flavors-f1FaDebug-9 $projectDir/src/f1FaDebug/res
            com.android.tests.flavors-fa-10 $projectDir/src/fa/res
            com.android.tests.flavors-main-11 $projectDir/src/main/res"""
                .trimIndent().replace("/", File.separator)
        assertThat(sourceSetMap.exists()).isTrue()
        assertThat(sourceSetMap.readText()).contains(expectedContents)
    }

    @Test
    fun `test MapSourceSetPathsTask does not run when ENABLE_SOURCE_SET_PATHS_MAP disabled`() {
        val execution = project.executor()
                .with(BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP, false)
                .run("assembleDebug")
        assertThat(execution.didWorkTasks).doesNotContain(":mapDebugSourceSetPaths")
    }
}
