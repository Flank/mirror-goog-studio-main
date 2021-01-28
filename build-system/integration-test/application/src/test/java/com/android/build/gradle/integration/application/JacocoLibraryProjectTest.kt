/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JacocoLibraryProjectTest {

    @Rule
    @JvmField
    val project = builder().fromTestApp(HelloWorldApp.forPlugin("com.android.library")).create()

    @Before
    fun enableCodeCoverage() {
        project.buildFile.appendText("""
            android.buildTypes.debug.testCoverageEnabled true
            dependencies {
                testImplementation "junit:junit:4.12"
            }

        """.trimIndent())

        project.projectDir.resolve("src/test/java/example/MyTest.java").also {
            it.parentFile.mkdirs()
            it.writeText("""
                package example;
                import org.junit.Test;

                public class MyTest {
                    @Test
                    public void foo() {
                        System.out.println(com.example.helloworld.HelloWorld.class);
                    }
                }

            """.trimIndent())
        }
    }

    @Test
    fun testUnitTests() {
        project.executor().run("testDebugUnitTest")
        val coverageData = project.buildDir.walk().filter { it.extension=="exec" }.toList()
        assertThat(coverageData).isEmpty()
    }

    @Test
    fun testUnitTestsWithJacocoPlugin() {
        val buildFile = project.buildFile.readText()
        project.buildFile.writeText("""
            apply plugin: 'jacoco'

            $buildFile
        """.trimIndent())
        val result = project.executor().withLoggingLevel(LoggingLevel.INFO).run("testDebugUnitTest")

        assertThat(result.stdout).doesNotContain("Cannot process instrumented class")

        val coverageData = project.buildDir.walk().filter { it.extension=="exec" }.toList()
        assertThat(coverageData).hasSize(1)
    }
}
