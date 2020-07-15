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

import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesugarTaskDelegateTest {
    @JvmField
    @Rule
    var tmp = TemporaryFolder()

    private lateinit var output: File
    private val submittedConfigurations = mutableSetOf<WorkerExecutorFacade.Configuration>()
    private val executor =
        object : ExecutorServiceAdapter("test", ":test", MoreExecutors.newDirectExecutorService()) {
            override fun submit(
                actionClass: Class<out Runnable>,
                configuration: WorkerExecutorFacade.Configuration
            ) {
                submittedConfigurations.add(configuration)
            }
        }

    @Before
    fun setUp() {
        output = tmp.newFolder()
    }

    @Test
    fun testJar() {
        val jar = tmp.newFile("input.jar")

        DesugarTaskDelegate(
            projectClasses = setOf(jar),
            subProjectClasses = emptySet(),
            externaLibsClasses = emptyList(),
            desugaringClasspath = emptySet(),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            executorFacade = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(jar.toString())
        assertThat(getFromArgs("--classpath_entry")).containsExactly(jar.toString())
    }

    @Test
    fun testJarAndDir() {
        val jar = tmp.newFile("input.jar")
        val dir = tmp.newFolder()

        DesugarTaskDelegate(
            projectClasses = setOf(jar, dir),
            subProjectClasses = emptySet(),
            externaLibsClasses = emptyList(),
            desugaringClasspath = emptySet(),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            executorFacade = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(jar.toString(), dir.toString())
    }

    @Test
    fun testProjectSubProjectAndLibs() {
        val projectJar = tmp.newFile("project-input.jar")
        val subProjectJar = tmp.newFile("subproject-input.jar")
        val externalLibsJar = tmp.newFile("external-libsinput.jar")

        DesugarTaskDelegate(
            projectClasses = setOf(projectJar),
            subProjectClasses = setOf(subProjectJar),
            externaLibsClasses = listOf(externalLibsJar),
            desugaringClasspath = emptySet(),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            executorFacade = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(
            projectJar.toString(),
            subProjectJar.toString(),
            externalLibsJar.toString()
        )
    }

    @Test
    fun testJarWithClasspath() {
        val projectJar = tmp.newFile("project-input.jar")
        val classpath1 = tmp.newFile("classpath1.jar")
        val classpath2 = tmp.newFile("classpath2.jar")

        DesugarTaskDelegate(
            projectClasses = setOf(projectJar),
            subProjectClasses = emptySet(),
            externaLibsClasses = emptyList(),
            desugaringClasspath = setOf(classpath1, classpath2),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            executorFacade = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(projectJar.toString())
        assertThat(getFromArgs("--classpath_entry")).containsExactly(
            projectJar.toString(),
            classpath1.toString(),
            classpath2.toString()
        )
    }

    private fun getFromArgs(argName: String): List<String> {
        return submittedConfigurations.map {
            it.parameter as DesugarWorkerItem.DesugarActionParams
        }.flatMap { argsList ->
            val indices =
                argsList.args.mapIndexedNotNull { index, elem -> (index + 1).takeIf { elem == argName } }
            indices.map { argsList.args[it] }
        }
    }
}
