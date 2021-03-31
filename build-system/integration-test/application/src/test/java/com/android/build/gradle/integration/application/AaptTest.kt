package com.android.build.gradle.integration.application

import com.android.testutils.truth.PathSubject.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.createHelloWorldAppGradleProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry

/** Tests for DSL AAPT options.  */
class AaptTest {
    @get:Rule  var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = createHelloWorldAppGradleProject()

    @Before
    fun setUp() {
        FileUtils.createFile(project.file("src/main/assets/ignored"), "ignored")
        FileUtils.createFile(project.file("src/main/assets/kept"), "kept")
    }

    @Test
    fun testAaptOptionsFlagsWithAapt2() {
        val tracesFolder = temporaryFolder.newFolder()

        val traceFolderPath = tracesFolder.absolutePath
        val windowsFriendlyFilePath = traceFolderPath.replace("\\", "\\\\")
        val additionalParams = "additionalParameters \"--trace-folder\", \"$windowsFriendlyFilePath\""

        TestFileUtils.appendToFile(
        project.buildFile,
            """
            android {
                aaptOptions {
                    $additionalParams
                }
            }
            """.trimIndent()
        )

        project.executor().run("clean", "assembleDebug")

        // Check that ids file is generated
        assertThat(tracesFolder).exists()
        Truth.assertThat(tracesFolder.listFiles()!!.size).isEqualTo(1)
        FileUtils.deleteDirectoryContents(tracesFolder)

        searchAndReplace(project.buildFile, additionalParams, "")

        project.executor().run("assembleDebug")

        // Check that ids file is not generated
        Truth.assertThat(tracesFolder.listFiles()).isEmpty()

        // Test the same additional parameters specified via onVariants
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                androidComponents {
                    onVariants(selector().all(), {
                        androidResources.aaptAdditionalParameters.addAll(
                            ["--trace-folder", "$windowsFriendlyFilePath"]
                        )
                    })
                }
                """.trimIndent()
        )

        project.executor().run("assembleDebug")
        assertThat(tracesFolder).exists()
        Truth.assertThat(tracesFolder.listFiles()!!.size).isEqualTo(1)
    }

    @Test
    fun emptyNoCompressList() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android {
                aaptOptions {
                    noCompress ""
                }
            }
            """.trimIndent()
        )

        project.executor().run("clean", "assembleDebug")

        // Check that APK entries are uncompressed
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            // TODO (Issue 70118728) res/layout/main.xml should be uncompressed too.
            val entry = ZipArchive.listEntries(apk.file)["classes.dex"]
            Truth.assertThat(entry?.compressionFlag).isEqualTo(ZipEntry.STORED)
        }
    }

    @Test
    fun testIgnoreAssetsPatterns_dsl() {
        TestFileUtils.appendToFile(
            project.buildFile,
            "android.aaptOptions.ignoreAssetsPattern 'ignored'"
        )

        project.executor().run("clean", "assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file)
            Truth.assertThat(entryMap).containsKey("assets/kept")
            Truth.assertThat(entryMap).doesNotContainKey("assets/ignored")
        }
    }

    @Test
    fun testIgnoreAssetsPatterns_variantApi() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                androidComponents {
                    onVariants(selector().all(), {
                        androidResources.ignoreAssetsPatterns.add("ignored")
                    })
                }
                """.trimIndent()
        )

        project.executor().run("clean", "assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file)
            Truth.assertThat(entryMap).containsKey("assets/kept")
            Truth.assertThat(entryMap).doesNotContainKey("assets/ignored")
        }
    }

    @Test
    fun testTasksRunAfterAaptOptionsChanges_bundleDebug() {
        testTasksRunAfterAaptOptionsChanges(
            assembleTask = "bundleDebug",
            expectedTasksThatDidWorkOnANoCompressChange = listOf(
                ":bundleDebugResources",
                ":mergeDebugJavaResource",
                ":packageDebugBundle",
                ":processDebugResources"
            ),
            expectedTasksThatDidWorkOnANoIgnoreAssetsChange = listOf(
                ":mergeDebugAssets"
            )
        )
    }

    @Test
    fun testTasksRunAfterAaptOptionsChanges_assembleDebug() {
        testTasksRunAfterAaptOptionsChanges(
            assembleTask = "assembleDebug",
            expectedTasksThatDidWorkOnANoCompressChange = listOf(
                ":mergeDebugJavaResource",
                ":packageDebug",
                ":processDebugResources"
            ),
            expectedTasksThatDidWorkOnANoIgnoreAssetsChange = listOf(
                ":mergeDebugAssets"
            )
        )
    }

    private fun testTasksRunAfterAaptOptionsChanges(
        assembleTask: String,
        expectedTasksThatDidWorkOnANoCompressChange: List<String>,
        expectedTasksThatDidWorkOnANoIgnoreAssetsChange: List<String>
    ) {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    aaptOptions {
                        noCompress "noCompressDsl"
                        ignoreAssetsPattern ".ignoreAssetsPatternDsl"
                    }
                }
                androidComponents {
                    onVariants(selector().all(), {
                        androidResources.ignoreAssetsPatterns.add(".ignoreAssetsPatternApi")
                    })
                }
                """.trimIndent()
        )

        project.executor().run("clean", assembleTask)

        // test that tasks run when aapt options changed via the DSL
        searchAndReplace(project.buildFile, "noCompressDsl", "noCompressDsl2")
        project.executor().run(assembleTask).let { result ->
            Truth.assertThat(result.didWorkTasks)
                .containsAtLeastElementsIn(expectedTasksThatDidWorkOnANoCompressChange)
        }
        searchAndReplace(project.buildFile, "ignoreAssetsPatternDsl", "ignoreAssetsPatternDsl2")
        project.executor().run(assembleTask).let { result ->
            Truth.assertThat(result.didWorkTasks)
                .containsAtLeastElementsIn(expectedTasksThatDidWorkOnANoIgnoreAssetsChange)
        }
        // test that tasks run when aapt options changed via the variant API
        searchAndReplace(project.buildFile, "ignoreAssetsPatternApi", "ignoreAssetsPatternApi2")
        project.executor().run(assembleTask).let { result ->
            Truth.assertThat(result.didWorkTasks)
                .containsAtLeastElementsIn(expectedTasksThatDidWorkOnANoIgnoreAssetsChange)
        }
    }
}
