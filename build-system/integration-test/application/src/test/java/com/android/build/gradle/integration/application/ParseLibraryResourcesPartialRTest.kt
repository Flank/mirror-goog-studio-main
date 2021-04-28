package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

/**
 * Tests to verify that library partial-r files are updated appropriately.
 */
class ParseLibraryResourcesPartialRTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(HelloWorldLibraryApp.create())
            .addGradleProperties(
                    "${BooleanOption.ENABLE_PARTIAL_R_INCREMENTAL_BUILDS.propertyName}=true")
            .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test incremental builds do not modify unnecessary partial r files`() {
        val executor = project.executor()
        executor.run("assembleDebug")
        val partialRIntermediateDir = FileUtils.join(project.projectDir,
                "lib", "build", "intermediates", InternalArtifactType
                .LOCAL_ONLY_PARTIAL_SYMBOL_DIRECTORY.getFolderName(), "debug", "partial-r")
        val partialRFiles = partialRIntermediateDir.listFiles()
                ?: error("No partial-r files generated.")
        checkLibPartialRFiles(partialRFiles.toList())
        val originalTimeStamps = partialRFiles.map(File::lastModified)

        //Incremental build with no changes.
        val incrementalBuildResult = executor.run("assembleDebug")
        assertThat(incrementalBuildResult.upToDateTasks).contains(":lib:parseDebugLocalResources")
        assertThat(partialRFiles[0].lastModified()).isEqualTo(originalTimeStamps[0])
        assertThat(partialRFiles[1].lastModified()).isEqualTo(originalTimeStamps[1])
    }

    @Test
    fun `test incremental builds modify partial r files when resource is modified`() {
        project.execute("assembleDebug")
        val partialRIntermediateDir = FileUtils.join(project.projectDir,
                "lib", "build", "intermediates", InternalArtifactType.
        LOCAL_ONLY_PARTIAL_SYMBOL_DIRECTORY.getFolderName(), "debug", "partial-r")
        val partialRFiles =
                partialRIntermediateDir.listFiles() ?: error("No partial-r files generated.")
        checkLibPartialRFiles(partialRFiles.toList())

        // Layout (modified resource)
        val libLayoutPartialR =
                FileUtils.join(partialRIntermediateDir, "layout_main.xml.flat-R.txt")
        val libLayoutFile = FileUtils.join(project.projectDir,
                "lib", "src", "main", "res", "layout", "main.xml")
        val libLayoutPartialROriginalTimestamp = libLayoutPartialR.lastModified()
        val modifiedLibLayout =  libLayoutFile.readText(Charset.defaultCharset())
                .replace("@+id/text", "@+id/changed_text")
        FileUtils.writeToFile(libLayoutFile, modifiedLibLayout)

        // Values (non-modified resource)
        val libValuesPartialR =
                FileUtils.join(partialRIntermediateDir, "values_values.arsc.flat-R.txt")
        val libValuesPartialROriginalTimestamp = libValuesPartialR.lastModified()

        // Incremental build with layout/main.xml changed resource name.
        val result = project.executor().run("assembleDebug")
        assertThat(result.upToDateTasks).doesNotContain(":lib:parseDebugLocalResources")
        assertThat(libLayoutPartialR.lastModified())
                .isGreaterThan(libLayoutPartialROriginalTimestamp)
        assertThat(libLayoutPartialR.readText(Charset.defaultCharset())).contains("changed_text")
        assertThat(libValuesPartialR.lastModified()).isEqualTo(libValuesPartialROriginalTimestamp)
    }

    private fun checkLibPartialRFiles(files : List<File>) =
            assertThat(files.map { it.name }).containsExactly(
            "layout_main.xml.flat-R.txt",
            "values_values.arsc.flat-R.txt"
    )

}
