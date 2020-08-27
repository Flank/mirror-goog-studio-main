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

import com.android.build.gradle.internal.fixtures.ExecutionMode
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.tasks.DesugarWorkerItem.DesugarActionParams
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.io.File

class DesugarTaskDelegateTest {
    @JvmField
    @Rule
    var tmp = TemporaryFolder()

    private lateinit var output: File
    private lateinit var task: AndroidVariantTask
    lateinit var executor: FakeGradleWorkExecutor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
        task = project.tasks.create("task", AndroidVariantTask::class.java)
        task.analyticsService.set(FakeNoOpAnalyticsService())
        executor =  FakeGradleWorkExecutor(
            project.objects, tmp.newFolder(), executionMode = ExecutionMode.CAPTURING
        )
        output = tmp.newFolder()
    }

    @Test
    fun testJar() {
        val jar = tmp.newFile("input.jar")

        DesugarTaskDelegate(
            initiator = task,
            projectClasses = setOf(jar),
            subProjectClasses = emptySet(),
            externaLibsClasses = emptySet(),
            desugaringClasspath = emptySet(),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            workerExecutor = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(jar.toString())
        assertThat(getFromArgs("--classpath_entry")).containsExactly(jar.toString())
    }

    @Test
    fun testJarAndDir() {
        val jar = tmp.newFile("input.jar")
        val dir = tmp.newFolder()

        DesugarTaskDelegate(
            initiator = task,
            projectClasses = setOf(jar, dir),
            subProjectClasses = emptySet(),
            externaLibsClasses = emptySet(),
            desugaringClasspath = emptySet(),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            workerExecutor = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(jar.toString(), dir.toString())
    }

    @Test
    fun testProjectSubProjectAndLibs() {
        val projectJar = tmp.newFile("project-input.jar")
        val subProjectJar = tmp.newFile("subproject-input.jar")
        val externalLibsJar = tmp.newFile("external-libsinput.jar")

        DesugarTaskDelegate(
            initiator = task,
            projectClasses = setOf(projectJar),
            subProjectClasses = setOf(subProjectJar),
            externaLibsClasses = setOf(externalLibsJar),
            desugaringClasspath = emptySet(),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            workerExecutor = executor
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
            initiator = task,
            projectClasses = setOf(projectJar),
            subProjectClasses = emptySet(),
            externaLibsClasses = emptySet(),
            desugaringClasspath = setOf(classpath1, classpath2),
            projectOutput = output,
            subProjectOutput = tmp.newFolder(),
            externalLibsOutput = tmp.newFolder(),
            tmpDir = tmp.newFolder(),
            bootClasspath = emptySet(),
            minSdk = 19,
            enableBugFixForJacoco = false,
            verbose = true,
            workerExecutor = executor
        ).doProcess()

        assertThat(getFromArgs("--input")).containsExactly(projectJar.toString())
        assertThat(getFromArgs("--classpath_entry")).containsExactly(
            projectJar.toString(),
            classpath1.toString(),
            classpath2.toString()
        )
    }

    private fun getFromArgs(argName: String): List<String> {
        @Suppress("UNCHECKED_CAST")
        return (executor.capturedParameters as List<DesugarActionParams>).flatMap { argsList ->
            val indices =
                argsList.args.get().mapIndexedNotNull { index, elem -> (index + 1).takeIf { elem == argName } }
            indices.map { argsList.args.get()[it] }
        }
    }
}
