/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.apk.analyzer.ApkAnalyzerImpl
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Locale
import kotlin.test.assertNotNull

class LibraryManifestTest: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Kotlin) {
    @Test
    fun libraryManifestTransformerTest() {
        given {
            addModule(":module") {
                buildFile =
                    // language=kotlin
                    """
            plugins {
                    id("com.android.library")
                    kotlin("android")
                    kotlin("android.extensions")
            }

            ${testingElements.getSimpleManifestTransformerTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants { variant ->
                    val manifestUpdater = tasks.register<ManifestTransformerTask>("${'$'}{variant.name}ManifestUpdater") {
                        activityName.set("ManuallyAdded")
                    }
                    variant.artifacts.use(manifestUpdater)
                        .wiredWithFiles(
                            ManifestTransformerTask::mergedManifest,
                            ManifestTransformerTask::updatedManifest)
                        .toTransform(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
                }
            }
            """.trimIndent()
                testingElements.addLibraryManifest("module", this)
            }
            addModule(":app") {
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            android {
                    ${testingElements.addCommonAndroidBuildLogic()}
            }
            dependencies {
                api(project(":module"))
            }
            """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        check {
            assertNotNull(this)
            assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:processDebugMainManifest",
                ":module:debugManifestUpdater"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
        }

        // post build activity, find the APK, load its merged manifest file and ensure that
        // the manually added permission made to the final merged manifest.
        val apkFolder = File(super.testProjectDir.root,
            "libraryManifestTransformerTest/app/build/"
                    + Artifact.Category.OUTPUTS.name.toLowerCase(Locale.US)
                    + "/"
                    + SingleArtifact.APK.getFolderName()
                    + "/debug")
        val byteArrayOutputStream = object : ByteArrayOutputStream() {
            @Synchronized
            override fun toString(): String =
                super.toString().replace(System.getProperty("line.separator"), "\n")
        }
        val ps = PrintStream(byteArrayOutputStream)
        val apkAnalyzer = ApkAnalyzerImpl(ps, Mockito.mock(AaptInvoker::class.java))
        val builtArtifacts = BuiltArtifactsLoaderImpl.loadFromFile(
            File(apkFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        )
            ?: throw RuntimeException("Cannot load APKs")
        if (builtArtifacts.elements.size != 1)
            throw RuntimeException("Expected one APK !")
        val apk = File(builtArtifacts.elements.single().outputFile).toPath()
        apkAnalyzer.resXml(apk, "/AndroidManifest.xml")
        val manifest = byteArrayOutputStream.toString()
        assertThat(manifest).contains("android:name=\"android.permission.INTERNET\"")
    }
}
