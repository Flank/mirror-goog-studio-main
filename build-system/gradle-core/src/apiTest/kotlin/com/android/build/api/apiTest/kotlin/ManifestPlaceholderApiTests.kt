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
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class ManifestPlaceholderApiTests: VariantApiBaseTest(
    TestType.Script
) {
    @Test
    fun addCustomManifestPlaceholder() {
        given {
            tasksToInvoke.add("debugManifestReader")
            addModule(":app") {
                manifest =
                        // language=xml
                    """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.build.example.minimal">
                    <application android:label="Minimal">
                        <activity android:name="${"$"}{MyName}">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent()

                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }

            import com.android.build.api.artifact.SingleArtifact
            abstract class ManifestReaderTask: DefaultTask() {

                @get:InputFile
                abstract val mergedManifest: RegularFileProperty

                @TaskAction
                fun taskAction() {

                    val manifest = mergedManifest.asFile.get().readText()
                    // ensure that merged manifest contains the right activity name.
                    if (!manifest.contains("activity android:name=\"com.android.build.example.minimal.MyRealName\""))
                        throw RuntimeException("Manifest Placeholder not replaced successfully")
                }
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants {
                    val manifestReader = tasks.register<ManifestReaderTask>("${'$'}{it.name}ManifestReader") {
                        mergedManifest.set(it.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    }
                    it.manifestPlaceholders.put("MyName", "MyRealName")
                }
            }
                """.trimIndent()
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Adding a manifest file placeholder in Kotlin.

See [manifest placeholder documentation](https://developer.android.com/studio/build/manifest-build-variables) for details
This sample show how to add a manifest placeholder value through the variant API. The value is
known at configuration time.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun addManifestPlaceholderFromTask() {
        given {
            tasksToInvoke.add("debugManifestReader")
            addModule(":app") {
                manifest =
                        // language=xml
                    """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.build.example.minimal">
                    <application android:label="Minimal">
                        <activity android:name="${"$"}{MyName}">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent()

                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }

            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.artifact.SingleArtifact

            ${testingElements.getStringProducerTask("android.intent.action.MAIN")}

            val androidNameProvider = tasks.register<StringProducerTask>("androidNameProvider") {
                File(project.buildDir, "intermediates/androidNameProvider/output").also {
                    it.parentFile.mkdirs()
                    outputFile.set(it)
                }
                outputs.upToDateWhen { false }
            }

            abstract class ManifestReaderTask: DefaultTask() {

                @get:InputFile
                abstract val mergedManifest: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    val manifest = mergedManifest.asFile.get().readText()
                    // ensure that merged manifest contains the right activity name.
                    if (!manifest.contains("activity android:name=\"android.intent.action.MAIN"))
                        throw RuntimeException("Manifest Placeholder not replaced successfully")
                }
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants {
                    val manifestReader = tasks.register<ManifestReaderTask>("${'$'}{it.name}ManifestReader") {
                        mergedManifest.set(it.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    }
                    it.manifestPlaceholders.put("MyName", androidNameProvider.flatMap { task ->
                        task.outputFile.map { it.asFile.readText(Charsets.UTF_8) }
                    })
                }
            }""".trimIndent()
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Adding a BuildConfig field in Kotlin

This sample show how to add a field in the BuildConfig class for which the value is not known at
configuration time.

The added field is used in the MainActivity.kt file.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

}
