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

import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.file.DuplicateFileCopyingException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal class FusedLibraryMergeClassesTest {

    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    val build: File by lazy {
        temporaryFolder.newFolder("build")
    }

    @Test
    fun testNoConflict() {
        testWithTask { jar1: File, jar2: File, task: FusedLibraryMergeClasses ->
            createJar(jar1, "source1.class")
            createJar(jar2, "source2.class")

            task.taskAction()
        }

        Truth.assertThat(File(build, "source1.class").exists()).isTrue()
        Truth.assertThat(File(build, "source2.class").exists()).isTrue()
    }

    @Test
    fun testPermittedConflicts() {
        testWithTask { jar1: File, jar2: File, task: FusedLibraryMergeClasses ->
            createJar(jar1, "source1.class", "meta-inf/somedir/module-info.class")
            createJar(jar2, "source2.class", "meta-inf/somedir/module-info.class")

            task.taskAction()
        }
        Truth.assertThat(build.listFiles().toList()).doesNotContain(
                "meta-inf/somedir/module-info.class"
        )
    }

    @Test(expected = DuplicateFileCopyingException::class)
    fun testConflicts() {
        testWithTask { jar1: File, jar2: File, task: FusedLibraryMergeClasses ->
            createJar(jar1, "source1.class")
            createJar(jar2, "source1.class")

            task.taskAction()
        }
    }

    private fun testWithTask(action: (File, File, FusedLibraryMergeClasses) -> Unit) {
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val task = project.tasks.register("mergeClasses", FusedLibraryMergeClasses::class.java).get()
        val jar1 = File(temporaryFolder.newFolder("src1"), "source1.jar")
        val jar2 = File(temporaryFolder.newFolder("src2"), "source2.jar")

        task.incoming.from(jar1, jar2)
        task.outputDirectory.set(build)

        action(jar1, jar2, task)
    }

    private fun createJar(file: File, vararg content: String) {
        JarOutputStream(FileOutputStream(file)).use {
            content.forEach { entryName ->
                it.putNextEntry(JarEntry(entryName))
                it.writer().write("jvm bytecodes")
                it.closeEntry()
            }
        }
    }
}
