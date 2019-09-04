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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.AarSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/** Test for library module with local file dependencies. */
class LibWithLocalDepsTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("multiproject").create()

    @get:Rule
    val expectedException: ExpectedException = ExpectedException.none()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.getSubproject("baseLibrary").buildFile,
            """
                dependencies {
                    api files("libs/localJavaLib.jar")
                }
                """
        )
    }

    @Test
    fun testLocalJarPackagedWithAar() {
        project.execute("clean", ":baseLibrary:assembleDebug")
        val aar = project.getSubproject("baseLibrary").getAar("debug")
        assertThat(aar).containsClass("Lcom/example/local/Foo;")
        assertThat(aar).containsJavaResourceWithContent(
            "com/example/local/javaRes.txt",
            "local java res"
        )
    }

    @Test
    fun testTransitiveLocalJarNotPackagedWithAar() {
        project.execute("clean", ":library:assembleDebug")
        // library depends on baseLibrary, so library has localJavaLib.jar as a transitive
        // dependency.
        val aar = project.getSubproject("library").getAar("debug")
        assertThat(aar).doesNotContainClass("Lcom/example/local/Foo;")
        assertThat(aar).doesNotContainJavaResource("com/example/local/javaRes.txt")
    }

    @Test
    fun testWarningWhenBuildingAarWithDirectLocalAarDep() {
        TestFileUtils.appendToFile(
            project.getSubproject("baseLibrary").buildFile,
            """
                dependencies {
                    api files("libs/local.aar")
                }
                """
        )
        val result = project.executor().run("clean", ":baseLibrary:assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "The AAR produced by this build for the :baseLibrary project is broken.")
        }
    }

    @Test
    fun testNoWarningWhenBuildingAarWithTransitiveLocalAarDep() {
        TestFileUtils.appendToFile(
            project.getSubproject("baseLibrary").buildFile,
            """
                dependencies {
                    api files("libs/local.aar")
                }
                """
        )
        val result = project.executor().run("clean", ":library:assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).doesNotContain("The AAR produced by this build")
        }
    }
}
