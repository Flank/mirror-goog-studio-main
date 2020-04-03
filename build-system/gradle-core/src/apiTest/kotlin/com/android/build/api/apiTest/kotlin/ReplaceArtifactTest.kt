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
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * Test with a buildSrc plugin that replace the manifest file producer task.
 */
class ReplaceArtifactTest: VariantApiBaseTest() {

    @Test
    fun replaceManifest() {

        given {
            addBuildSrc() {

                addSource(
                    "src/main/kotlin/com/build/android/example/ObtainGitVersionTask.kt",
                    // language=kotlin
                    """
                package com.build.android.example

                import org.gradle.api.DefaultTask
                import org.gradle.api.file.RegularFileProperty
                import org.gradle.api.tasks.OutputFile
                import org.gradle.api.tasks.TaskAction

                abstract class ObtainGitVersionTask: DefaultTask() {

                    @get:OutputFile
                    abstract val gitVersionOutputFile: RegularFileProperty

                    @ExperimentalStdlibApi
                    @TaskAction
                    fun taskAction() {
                        println("Git version is running !")
                        val firstProcess = ProcessBuilder("git", "rev-parse --short HEAD").start()
                        var gitVersion = firstProcess.inputStream.readBytes().decodeToString()
                        if (gitVersion.isEmpty()) {
                            gitVersion = "12"
                        }
                        gitVersionOutputFile.get().asFile.writeText(gitVersion)
                    }
                }
                """.trimIndent()
                )

                addSource(
                    "src/main/kotlin/com/build/android/example/ManifestProducerTask.kt",
                    // language=kotlin
                    """
                package com.build.android.example

                import org.gradle.api.DefaultTask
                import org.gradle.api.file.RegularFileProperty
                import org.gradle.api.tasks.InputFile
                import org.gradle.api.tasks.OutputFile
                import org.gradle.api.tasks.TaskAction

                abstract class ManifestProducerTask: DefaultTask() {
                    @get:InputFile
                    abstract val gitInfoFile: RegularFileProperty

                    @get:OutputFile
                    abstract val updatedManifest: RegularFileProperty

                    @TaskAction
                    fun taskAction() {

                        val gitVersion = gitInfoFile.get().asFile.readText()
                        val manifest = ""${'"'}<?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.build.example.minimal"
                        android:versionCode="${'$'}{gitVersion}"
                        android:versionName="1.0" >

                        <application android:label="Minimal">
                            <activity android:name="MainActivity">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                        ""${'"'}
                        println("Writes to " + updatedManifest.get().asFile.absolutePath)
                        updatedManifest.get().asFile.writeText(manifest)
                    }
                }
                """.trimIndent()
                )

                addSource(
                    "src/main/kotlin/com/build/android/example/VerifyManifestTask.kt",
                    // language=kotlin
                    """
                package com.build.android.example

                import org.gradle.api.DefaultTask
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction
                import java.io.ByteArrayOutputStream
                import java.io.PrintStream

                import com.android.tools.apk.analyzer.ApkAnalyzerImpl
                import com.android.build.api.variant.BuiltArtifactsLoader
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Internal
                import java.io.File

                abstract class VerifyManifestTask: DefaultTask() {

                    @get:InputFiles
                    abstract val apkFolder: DirectoryProperty

                    @get:Internal
                    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

                    @TaskAction
                    fun taskAction() {
                        val byteArrayOutputStream = object : ByteArrayOutputStream() {
                            @Synchronized
                            override fun toString(): String =
                                super.toString().replace(System.getProperty("line.separator"), "\n")
                        }
                        val ps = PrintStream(byteArrayOutputStream)
                        val apkAnalyzer = ApkAnalyzerImpl(ps, null)
                        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
                            ?: throw RuntimeException("Cannot load APKs")
                        if (builtArtifacts.elements.size != 1) 
                            throw RuntimeException("Expected one APK !")
                        val apk = File(builtArtifacts.elements.single().outputFile).toPath()
                        apkAnalyzer.resXml(apk, "/AndroidManifest.xml")
                        val manifest = byteArrayOutputStream.toString()
                        println(if (manifest.contains("android:versionCode=\"12\"")) "SUCCESS" else "FAILED")
                    }
                }""".trimIndent()
                )

                addSource(
                    "src/main/kotlin/com/build/android/example/ExamplePlugin.kt",
                    // language=kotlin
                    """
                package com.build.android.example

                import com.android.build.api.artifact.ArtifactTypes
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import java.io.File
                import com.android.build.api.dsl.CommonExtension

                abstract class ExamplePlugin: Plugin<Project> {

                    override fun apply(project: Project) {
                        val gitVersionProvider =
                            project.tasks.register("gitVersionProvider", ObtainGitVersionTask::class.java) {
                                it.gitVersionOutputFile.set(
                                    File(project.buildDir, "intermediates/gitVersionProvider/output")
                                )
                                it.outputs.upToDateWhen { false }
                            }

                        val android = project.extensions.getByType(CommonExtension::class.java)

                        android.onVariantProperties {

                            val manifestUpdater =
                                project.tasks.register(name + "ManifestUpdater", ManifestProducerTask::class.java) {
                                    it.gitInfoFile.set(gitVersionProvider.flatMap { task -> task.gitVersionOutputFile })
                                }
                            operations.replace(manifestUpdater, ManifestProducerTask::updatedManifest)
                                .on(ArtifactTypes.MERGED_MANIFEST)

                            project.tasks.register(name + "Verifier", VerifyManifestTask::class.java) {
                                it.apkFolder.set(operations.get(ArtifactTypes.APK))
                                it.builtArtifactsLoader.set(operations.getBuiltArtifactsLoader())
                            }
                        }
                    }
                }
                """.trimIndent()
                )

                buildFile =
                    """
                dependencies {
                    implementation(kotlin("stdlib"))
                    implementation("com.android.tools.build:gradle-api:${com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION}")
                    implementation("com.android.tools.apkparser:apkanalyzer-cli:${com.android.Version.ANDROID_TOOLS_BASE_VERSION}")
                    gradleApi()
                }
                """.trimIndent()
            }
            addModule(":app") {
                buildFile =
                    """
                    plugins {
                            id("com.android.application")
                            kotlin("android")
                            kotlin("android.extensions")
                    }
                    apply<com.build.android.example.ExamplePlugin>()

                    android {
                        compileSdkVersion(29)
                        buildToolsVersion("29.0.3")
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }
                    }
                    """.trimIndent()

                manifest =
                    // language=xml
                    """<?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.build.example.minimal">
                        <application android:label="Minimal">
                            <activity android:name="MainActivity">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                    """.trimIndent()

                addSource(
                    "src/main/java/com/android/build/example/replaceartifact/MainActivity.kt",
                    //language = kotlin
                    """
                package com.android.build.example.minimal

                import android.app.Activity
                import android.os.Bundle
                import android.widget.TextView

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        val label = TextView(this)
                        label.setText("Hello world!")
                        setContentView(label)
                    }
                }
                """.trimIndent()
                )
            }
        }
        check {
            assertNotNull(this)
            assertThat(output).contains("BUILD SUCCESSFUL")
            val assembleTask = task(":app:assembleDebug")
            assertNotNull(assembleTask)
            assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":app:processDebugMainManifest")).isNull()
        }
    }
}