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

package com.android.build.gradle.integration.validation

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.testutils.TestInputsGenerator
import com.google.common.reflect.ClassPath

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Runs Gradle's task properties validation task on the Android Gradle Plugin.
 */
class ValidateTaskPropertiesTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(MinimalSubProject.javaLibrary()).create()

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Before
    fun before() {
        val classes = tmp.root.resolve("Classes")
        val classLoader = BaseExtension::class.java.classLoader
        val classesList: List<Class<*>> =
            ClassPath.from(classLoader)
                .getTopLevelClassesRecursive("com.android.build.gradle")
                .map { it.load() }
        TestInputsGenerator.pathWithClasses(classes.toPath(), classesList)

        val classpath =
            System.getProperty("java.class.path").split(System.getProperty("path.separator"))
                .joinToString {
                    "'" + File(it).invariantSeparatorsPath + "'"
                }

        project.buildFile.appendText("\n" +
                """
            apply plugin: 'java-gradle-plugin'

            tasks {
                validatePlugins {
                    failOnWarning.set(true)
                    enableStricterValidation.set(true)
                    classes.setFrom('${classes.invariantSeparatorsPath}')
                    classpath.setFrom($classpath)
                }
             }


        """.trimIndent()
        )
    }

    @Test
    fun validate() {
        project.execute("validatePlugins")
    }
}