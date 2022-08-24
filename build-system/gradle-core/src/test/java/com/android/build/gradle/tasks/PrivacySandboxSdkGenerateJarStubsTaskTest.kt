/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks

import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.jar.JarFile

internal class PrivacySandboxSdkGenerateJarStubsTaskTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    val build: File by lazy {
        temporaryFolder.newFolder("build")
    }

    @Test
    fun testStubJarContainsExpectedEntries() {
        testWithTask { dir: File, outputStubJar: File, task: PrivacySandboxSdkGenerateJarStubsTask ->
            FileUtils.createFile(FileUtils.join(dir,
                    "com",
                    "example",
                    "library",
                    "MyClassFromLibrary1.class"),
                    """
            package com.example.library1;

            class MyClassFromLibrary1 {
                public MyClassFromLibrary1() {}
            }
        """.trimIndent())
            FileUtils.createFile(FileUtils.join(dir,
                    "com", "example", "library2", "MyClassFromLibrary2.class"),
                    """
            package com.example.library2;

            class MyClassFromLibrary2 {
                public MyClassFromLibrary2() {}
            }
        """.trimIndent())
            FileUtils.createFile(FileUtils.join(dir,
                    "com", "example", "library2", "MyClassFromLibrary2\$InnerClass.class"),
                    "")
            FileUtils.createFile(
                    FileUtils.join(dir, "META-INF", "MANIFEST.mf"),
                    "Manifest-Version: 1.0")

            task.doTaskAction()

            JarFile(outputStubJar).use {
                val entries = it.entries().toList().map { it.name }
                assertThat(entries).containsExactlyElementsIn(
                        listOf(
                                "com/example/library/MyClassFromLibrary1.class",
                                "com/example/library2/MyClassFromLibrary2.class",
                                "com/example/library2/MyClassFromLibrary2\$InnerClass.class"
                        )
                )
            }
        }

    }

    private fun testWithTask(action: (File, File, PrivacySandboxSdkGenerateJarStubsTask) -> Unit) {
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val task = project.tasks.register("privacySandboxClassesJarStubs",
                PrivacySandboxSdkGenerateJarStubsTask::class.java).get()
        val classesDir = temporaryFolder.newFolder("src")

        task.mergedClasses.from(classesDir)
        val outJar =
                FileUtils.join(build,
                        PrivacySandboxSdkGenerateJarStubsTask.privacySandboxSdkStubJarFilename)
                        .also { FileUtils.createFile(it, "") }
        task.outputStubJar.set(outJar)
        action(classesDir, outJar, task)
    }
}
