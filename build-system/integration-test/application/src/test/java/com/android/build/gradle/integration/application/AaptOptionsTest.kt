package com.android.build.gradle.integration.application

import com.android.testutils.truth.FileSubject.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests for DSL AAPT options.  */
class AaptOptionsTest {
    @get:Rule  var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUp() {
        FileUtils.createFile(project.file("src/main/res/raw/ignored"), "ignored")
        FileUtils.createFile(project.file("src/main/res/raw/kept"), "kept")
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

        // Should execute without failure.
        project.executor().run("clean", "assembleDebug")
    }

    @Test
    fun testTasksRunAfterAaptOptionsChanges_bundleDebug() {
        testTasksRunAfterAaptOptionsChanges(
            "bundleDebug",
            listOf(
                ":bundleDebugResources",
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
            listOf(":mergeDebugJavaResource", ":packageDebug", ":processDebugResources")
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
                    }

                    onVariantProperties {
                        aaptOptions.noCompress.add("bar")
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
