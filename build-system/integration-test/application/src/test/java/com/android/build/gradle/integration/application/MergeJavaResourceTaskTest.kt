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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class MergeJavaResourceTaskTest {

    private val app =
        MinimalSubProject
            .app("com.example.test")
            .withFile(
                "src/main/java/com/example/test/Foo.java",
                """package com.example.test;
                    |public class Foo {
                    |}""".trimMargin()
            )

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(app).create()

    @Test
    fun `test no javac dependency if no annotation processor`() {
        val fullBuild = project.executor().run("assembleDebug")
        assertThat(fullBuild.didWorkTasks)
            .containsAllOf(":compileDebugJavaWithJavac", ":mergeDebugJavaResource")
        TestFileUtils.addMethod(
            File(project.mainSrcDir, "com/example/test/Foo.java"),
            "void foo() {}"
        )
        val incrementalBuild = project.executor().run("assembleDebug")
        assertThat(incrementalBuild.didWorkTasks).contains(":compileDebugJavaWithJavac")
        assertThat(incrementalBuild.upToDateTasks).contains(":mergeDebugJavaResource")
    }

    @Test
    fun `test javac dependency if annotation processor`() {
        val emptyJar = project.file("empty.jar")
        assertThat(emptyJar.createNewFile()).isTrue()
        TestFileUtils.appendToFile(
            project.buildFile,
            "dependencies { annotationProcessor files('empty.jar') }"
        )
        val fullBuild = project.executor().run("assembleDebug")
        assertThat(fullBuild.didWorkTasks)
            .containsAllOf(":compileDebugJavaWithJavac", ":mergeDebugJavaResource")
        TestFileUtils.addMethod(
            File(project.mainSrcDir, "com/example/test/Foo.java"),
            "void foo() {}"
        )
        val incrementalBuild = project.executor().run("assembleDebug")
        assertThat(incrementalBuild.didWorkTasks)
            .containsAllOf(":compileDebugJavaWithJavac", ":mergeDebugJavaResource")
    }

    @Test
    fun `test javac dependency if annotation processor added via defaultDependencies`() {
        val emptyJar = project.file("empty.jar")
        assertThat(emptyJar.createNewFile()).isTrue()
        TestFileUtils.appendToFile(
            project.buildFile,
            """configurations['annotationProcessor'].defaultDependencies { dependencies ->
                |    dependencies.add(owner.project.dependencies.create(files('empty.jar')))
                |}""".trimMargin()
        )
        val fullBuild = project.executor().run("assembleDebug")
        assertThat(fullBuild.didWorkTasks)
            .containsAllOf(":compileDebugJavaWithJavac", ":mergeDebugJavaResource")
        TestFileUtils.addMethod(
            File(project.mainSrcDir, "com/example/test/Foo.java"),
            "void foo() {}"
        )
        val incrementalBuild = project.executor().run("assembleDebug")
        assertThat(incrementalBuild.didWorkTasks)
            .containsAllOf(":compileDebugJavaWithJavac", ":mergeDebugJavaResource")
    }
}
