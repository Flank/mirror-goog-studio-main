package com.android.build.gradle.integration.application

import com.android.testutils.truth.FileSubject.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry

/** Tests for DSL AAPT options.  */
class AaptOptionsTest {
    @get:Rule  var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

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

        TestFileUtils.searchAndReplace(project.buildFile, additionalParams, "")

        project.executor().run("assembleDebug")

        // Check that ids file is not generated
        Truth.assertThat(tracesFolder.listFiles()).isEmpty()

        // Test the same additional parameters specified via onVariantProperties
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    onVariantProperties {
                        aaptOptions.additionalParameters.addAll(
                            ["--trace-folder", "$windowsFriendlyFilePath"]
                        )
                    }
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
            val entry = ZipArchive.listEntries(apk.file.toFile())["classes.dex"]
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
            val entryMap = ZipArchive.listEntries(apk.file.toFile())
            Truth.assertThat(entryMap).containsKey("assets/kept")
            Truth.assertThat(entryMap).doesNotContainKey("assets/ignored")
        }
    }

    @Test
    fun testIgnoreAssetsPatterns_variantApi() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    onVariantProperties {
                        aaptOptions.ignoreAssetsPatterns.add("ignored")
                    }
                }
                """.trimIndent()
        )

        project.executor().run("clean", "assembleDebug")

        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            val entryMap = ZipArchive.listEntries(apk.file.toFile())
            Truth.assertThat(entryMap).containsKey("assets/kept")
            Truth.assertThat(entryMap).doesNotContainKey("assets/ignored")
        }
    }

    @Test
    fun testTasksRunAfterAaptOptionsChanges_bundleDebug() {
        testTasksRunAfterAaptOptionsChanges(
            "bundleDebug",
            listOf(
                ":bundleDebugResources",
                ":mergeDebugAssets",
                ":mergeDebugJavaResource",
                ":packageDebugBundle",
                ":processDebugResources"
            )
        )
    }

    @Test
    fun testTasksRunAfterAaptOptionsChanges_assembleDebug() {
        testTasksRunAfterAaptOptionsChanges(
            "assembleDebug",
            listOf(
                ":mergeDebugAssets",
                ":mergeDebugJavaResource",
                ":packageDebug",
                ":processDebugResources"
            )
        )
    }

    private fun testTasksRunAfterAaptOptionsChanges(
        assembleTask: String,
        expectedDidWorkTasks: List<String>
    ) {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    aaptOptions {
                        noCompress "foo"
                        ignoreAssetsPattern ".foo"
                    }

                    onVariantProperties {
                        aaptOptions.noCompress.add("bar")
                        aaptOptions.ignoreAssetsPatterns.add(".bar")
                    }
                }
                """.trimIndent()
        )

        project.executor().run("clean", assembleTask)

        // test that tasks run when aapt options changed via the DSL
        TestFileUtils.searchAndReplace(project.buildFile, "foo", "baz")
        val result1 = project.executor().run(assembleTask)
        Truth.assertThat(result1.didWorkTasks).containsAtLeastElementsIn(expectedDidWorkTasks)

        // test that tasks run when aapt options changed via the variant API
        TestFileUtils.searchAndReplace(project.buildFile, "bar", "qux")
        val result2 = project.executor().run(assembleTask)
        Truth.assertThat(result2.didWorkTasks).containsAtLeastElementsIn(expectedDidWorkTasks)
    }
}
