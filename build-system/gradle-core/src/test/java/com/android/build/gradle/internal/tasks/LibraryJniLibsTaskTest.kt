/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import java.io.File
import java.io.Serializable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests for [LibraryJniLibsTask].  */
class LibraryJniLibsTaskTest {

    @get:Rule
    var tmpDir = TemporaryFolder()

    private lateinit var outputDirectory: File
    private lateinit var workers: WorkerExecutorFacade

    @Before
    fun setUp() {
        outputDirectory = tmpDir.newFile("out")

        workers = object: WorkerExecutorFacade {
            override fun submit(actionClass: Class<out Runnable>, parameter: Serializable) {
                val configuration =
                    WorkerExecutorFacade.Configuration(
                        parameter, WorkerExecutorFacade.IsolationMode.NONE, listOf()
                    )
                val action =
                    actionClass.getConstructor(configuration.parameter.javaClass)
                        .newInstance(configuration.parameter)
                action.run()
            }

            override fun await() {}

            override fun close() {}
        }
    }

    @Test
    fun testBasic() {
        // Create a "projectNativeLibs" directory.
        val dir1 = File(tmpDir.root, "dir1")
        FileUtils.createFile(File(dir1, "lib/x86/foo.so"), "foo")
        FileUtils.createFile(File(dir1, "lib/x86/notAnSoFile"), "ignore me")

        // Create 2 "localJarsNativsLibs" jars.
        val jarFile1 = File(tmpDir.root, "jarFile1.jar")
        TestInputsGenerator.writeJarWithEmptyEntries(
            jarFile1.toPath(),
            listOf("lib/x86/bar.so", "lib/x86/notAnSoFile")
        )
        val jarFile2 = File(tmpDir.root, "jarFile2.jar")
        TestInputsGenerator.writeJarWithEmptyEntries(jarFile2.toPath(), listOf("lib/x86/baz.so"))

        LibraryJniLibsTask.LibraryJniLibsDelegate(
            dir1,
            listOf(jarFile1, jarFile2),
            outputDirectory,
            workers
        ).copyFiles()

        // Make sure the output is a jar file with expected contents
        assertThat(outputDirectory).isDirectory()
        assertThat(File(outputDirectory, "x86/foo.so")).isFile()
        assertThat(File(outputDirectory, "x86/bar.so")).isFile()
        assertThat(File(outputDirectory, "x86/baz.so")).isFile()
        assertThat(File(outputDirectory, "x86/notAnSoFile")).doesNotExist()
    }
}
