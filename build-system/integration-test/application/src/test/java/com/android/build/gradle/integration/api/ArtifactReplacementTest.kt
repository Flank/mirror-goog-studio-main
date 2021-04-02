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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class ArtifactReplacementTest {
    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun buildApp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply from: "../commonHeader.gradle"
                buildscript { apply from: "../commonBuildScript.gradle" }

                apply from: "../commonLocalRepo.gradle"

                apply plugin: 'com.android.application'
                android {
                    defaultConfig.minSdkVersion 14
                    compileSdkVersion 30
                    lintOptions.checkReleaseBuilds = false
                    defaultConfig {
                        minSdkVersion rootProject.supportLibMinSdk
                        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
                    }
                }
                androidComponents {
                    onVariants(selector().all(), {
                        TaskProvider produceTwoArtifacts = tasks.register(it.getName() + 'ProduceTwoArtifacts', ProduceTwoArtifacts) {
                            getOutputManifest().set(
                                new File(project.buildDir, "intermediates/produceTwoArtifacts/manifest.xml")
                            )
                            getMappingFile().set(
                                new File(project.buildDir, "intermediates/produceTwoArtifacts/bundle")
                            )
                        }
                        it.artifacts.use(produceTwoArtifacts)
                            .wiredWith({ it.getOutputManifest() })
                            .toCreate(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                        it.artifacts.use(produceTwoArtifacts)
                            .wiredWith({ it.getMappingFile() })
                            .toCreate(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)
                        TaskProvider replaceArtifacts = tasks.register(it.getName() + 'ReplaceArtifacts', ReplaceArtifact) {
                            task -> task.getReplacedManifest().set(
                                new File(project.buildDir, "intermediates/replaceArtifact/replacedManifest.xml")
                            )
                        }
                        it.artifacts.use(replaceArtifacts)
                            .wiredWith({ it.getReplacedManifest() })
                            .toCreate(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                      TaskProvider verify = tasks.register(it.getName() + 'VerifyArtifacts', VerifyArtifacts) {
                            task -> task.getFinalManifest().set(
                                it.artifacts.get(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                            )
                            task.getMappingFile().set(
                                it.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)
                            )
                            task.getProjectBuildDir().set(
                                project.buildDir.toString()
                            )
                        }
                    })
                }
                import com.android.build.api.artifact.impl.ArtifactsImpl
                import com.android.build.api.artifact.SingleArtifact
                import com.android.build.api.artifact.impl.ArtifactContainer

                abstract class ReplaceArtifact extends DefaultTask {
                    @OutputFile
                    abstract RegularFileProperty getReplacedManifest()

                    @TaskAction
                    void taskAction() {
                        FileWriter writer = new FileWriter(getReplacedManifest().get().getAsFile())
                        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android" +
                        "package=\"com.example.test\" android:versionCode=\"67\" android:versionName=\"1.0\" >" +
                        "</manifest>")
                        writer.close()
                    }
                }
                abstract class ProduceTwoArtifacts extends DefaultTask {
                    @OutputFile
                    abstract RegularFileProperty getOutputManifest()

                    @OutputFile
                    abstract RegularFileProperty getMappingFile()

                    @TaskAction
                    void taskAction() {
                        FileWriter writer = new FileWriter(getMappingFile().get().getAsFile())
                        writer.write("this is a mapping file")
                        writer.close()
                        FileWriter writer2 = new FileWriter(getOutputManifest().get().getAsFile())
                        writer2.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android" +
                        "package=\"com.example.test\" android:versionCode=\"6\" android:versionName=\"1.0\" >" +
                        "</manifest>")
                        writer2.close()
                    }
                }
                abstract class VerifyArtifacts extends DefaultTask {
                    @Input
                    abstract Property<String> getProjectBuildDir()

                    @InputFile
                    abstract RegularFileProperty getFinalManifest()

                    @InputFile
                    abstract RegularFileProperty getMappingFile()

                    @TaskAction
                    void taskAction() {
                        assert getFinalManifest().get().asFile.absolutePath.contains("debugReplaceArtifacts")
                        assert getMappingFile().get().asFile.absolutePath.contains("debugProduceTwoArtifacts")
                        assert new File(getProjectBuildDir().get() + "/intermediates/merged_manifest/debug/debugProduceTwoArtifacts/AndroidManifest.xml").exists()
                        System.out.println("Verification finished successfully")
                    }
                }
            """.trimIndent())
        val result = project.executor().run("clean", "debugProduceTwoArtifacts", "debugReplaceArtifacts", "debugVerifyArtifacts")
        Truth.assertThat(result.didWorkTasks).contains(":debugProduceTwoArtifacts")
        Truth.assertThat(result.didWorkTasks).contains(":debugReplaceArtifacts")
    }
}
