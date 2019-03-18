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

import com.android.build.gradle.BaseExtension
import com.android.testutils.GoldenFile
import com.android.testutils.TestInputsGenerator
import com.android.utils.PathUtils
import com.google.common.reflect.ClassPath
import org.gradle.plugin.devel.tasks.ValidateTaskProperties
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files

/**
 * Runs Gradle's task properties validation task on the Android Gradle Plugin.
 *
 * The expected result file can be updated by running [ValidateTaskPropertiesUpdater.main]
 */
class ValidateTaskPropertiesTest {

    @Test
    fun validate() {
        taskPropertiesGoldenFile.assertUpToDate(updater = ValidateTaskPropertiesUpdater::class.java)
    }

    companion object {
        internal val taskPropertiesGoldenFile = GoldenFile(
            resourceRootWorkspacePath = "tools/base/build-system/gradle-core/src/test/resources",
            resourcePath = "com/android/build/gradle/internal/tasks/validateTaskProperties.txt",
            actualCallable = {
                val temporaryDirectory =
                    Files.createTempDirectory("validateTaskProperties")
                try {
                    val classes = temporaryDirectory.resolve("Classes")
                    val classLoader = BaseExtension::class.java.classLoader
                    val classesList: List<Class<*>> =
                        ClassPath.from(classLoader)
                            .getTopLevelClassesRecursive("com.android.build.gradle")
                            .map { it.load() }
                    TestInputsGenerator.pathWithClasses(classes, classesList)
                    // This duplicates the program classes put in the test inputs generator,
                    // but that doesn't seem to matter.
                    val classpath = (classLoader as URLClassLoader).urLs.map { File(it.toURI()) }

                    val project = ProjectBuilder.builder()
                        .withProjectDir(temporaryDirectory.resolve("project").toFile())
                        .withName("fakeProject").build()
                    val outputFile = temporaryDirectory.resolve("output.txt")
                    val task =
                        project.tasks.register(
                            "validateTaskProperties",
                            ValidateTaskProperties::class.java
                        ) {
                            it.enableStricterValidation = true
                            it.outputFile.set(outputFile.toFile())
                            it.classes.setFrom(classes)
                            it.classpath.setFrom(classpath)
                        }
                    task.get().validateTaskClasses()
                    return@GoldenFile Files.readAllLines(outputFile)
                } finally {
                    PathUtils.deleteRecursivelyIfExists(temporaryDirectory)
                }
            })
    }
}
