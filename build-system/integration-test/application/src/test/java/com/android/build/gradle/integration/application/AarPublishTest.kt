/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

/*
* Tests to verify that AARs produced from library modules in build/output/aar are in a state
* which can be published e.g to public repository like Maven.
*/
class AarPublishTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(
        MultiModuleTestProject.builder()
            .subproject(
                ":library", MinimalSubProject.lib("com.example.library")
                    .appendToBuild("android.buildTypes.debug.testCoverageEnabled true")
            ).build()
    ).create()

    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    /* Test to verify that AARs do not include Jacoco dependencies when published. */
    @Test
    fun canPublishLibraryAarWithCoverageEnabled() {
        val librarySubproject = project.getSubproject(":library")
        project.execute("library:assembleDebug")

        val libraryPublishedAar =
            FileUtils.join(librarySubproject.outputDir, "aar", "library-debug.aar")
        val tempTestData = temporaryDirectory.newFolder("testData")
        val buildConfigClasspath = "com/example/library/BuildConfig.class"
        val extractedJar = File(tempTestData, "classes.jar")
        // Extracts the zipped BuildConfig.class in library-debug.aar/classes.jar to
        // the extractedBuildConfigClass temporary file, so it can be later loaded
        // into a classloader.
        ZipFile(libraryPublishedAar).use { libraryAar ->
            libraryAar.getInputStream(libraryAar.getEntry("classes.jar")).use { stream ->
                extractedJar.writeBytes(stream.readBytes())
            }
        }
        URLClassLoader.newInstance(arrayOf(extractedJar.toURI().toURL())).use {
            val buildConfig =
                it.loadClass("com.example.library.BuildConfig")
            try {
                // Invoking the constructor will throw a ClassNotFoundException for
                // Jacoco agent classes if the classes contain a call to Jacoco.
                // If there is no issues with invoking the constructor,
                // then there are no Jacoco dependencies in the AAR classes.
                buildConfig.getConstructor().newInstance()
            } catch (e: NoClassDefFoundError) {
                throw AssertionError(
                    "This AAR is not publishable as it contains a dependency on Jacoco.", e
                )
            }
        }
    }
}
