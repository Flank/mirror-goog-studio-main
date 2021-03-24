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

class GetPublicTxtTest: VariantApiBaseTest(TestType.Script){

    @Test
    fun getPublicTxt() {
        given {
            tasksToInvoke.add(":lib:validateDebugPublicResources")
            addModule(":lib") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile = // language=kotlin prefix="import org.gradle.api.*; import org.gradle.api.file.*;import org.gradle.api.provider.*; import org.gradle.api.tasks.*; import org.gradle.workers.*;"
                    """
            plugins {
                id("com.android.library")
                kotlin("android")
            }

            import com.android.build.api.artifact.SingleArtifact
            import com.android.build.api.variant.BuiltArtifactsLoader
            import java.lang.RuntimeException
            import java.util.Locale
            import javax.inject.Inject

            abstract class PublicResourcesValidatorTask: DefaultTask() {

                @get:InputFile
                abstract val publicAndroidResources: RegularFileProperty

                @get:InputFile
                abstract val expectedPublicResources: RegularFileProperty

                @get:OutputDirectory
                abstract val fakeOutput: DirectoryProperty

                @get:Inject
                abstract val workerExecutor: WorkerExecutor

                @TaskAction
                fun taskAction() {
                    workerExecutor.noIsolation().submit(Action::class.java) {
                        actual.set(publicAndroidResources)
                        expected.set(expectedPublicResources)
                    }
                }

                abstract class Action: WorkAction<Action.Parameters> {
                    abstract class Parameters: WorkParameters {
                        abstract val actual: RegularFileProperty
                        abstract val expected: RegularFileProperty
                    }
                    override fun execute() {
                        val actual = parameters.actual.get().asFile.readLines()
                        val expected = parameters.expected.get().asFile.readLines()
                        if (actual != expected) {
                            throw RuntimeException(
                                    "Public Android resources have changed.\n" +
                                    "Please either revert the change or update the API expectation file\n" +
                                    "Expected\n    " + expected.joinToString("\n    ") + "\n" +
                                    "Actual\n    " + actual.joinToString("\n    ")
                                )
                        }
                        println("Public Android resources unchanged.")
                    }
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants { variant ->
                    @OptIn(ExperimentalStdlibApi::class)
                    val capitalizedName = variant.name.capitalize(Locale.US)
                    project.tasks.register<PublicResourcesValidatorTask>("validate${'$'}{capitalizedName}PublicResources") {
                        publicAndroidResources.set(variant.artifacts.get(SingleArtifact.PUBLIC_ANDROID_RESOURCES_LIST))
                        expectedPublicResources.set(project.file("src/test/expectedApi/public-resources.txt"))
                        fakeOutput.set(project.layout.buildDirectory.dir("intermediates/PublicResourcesValidatorTask/${'$'}name"))
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
                addSource(
                    "src/main/res/values/strings.xml",
                    """
                    <resources>
                        <string name="public_string">String</string>
                    </resources>
                    """.trimIndent())
                addSource(
                    "src/test/expectedApi/public-resources.txt",
                    "string public_string"
                )
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Public txt get in Kotlin

This sample show how to obtain the file listing the public artifacts from the Android Gradle Plugin.
The [onVariants] block will wire the [PublicResourcesValidatorTask] input property
(publicAndroidResources) by using
the [Artifacts.get] call with the right [SingleArtifact..

```publicAndroidResources.set(artifacts.get(SingleArtifact.PUBLIC_ANDROID_RESOURCES_LIST))```

For more information about how to mark resources as public see
[Choose resources to make public](https://developer.android.com/studio/projects/android-library.html#PrivateResources)

## To Run
./gradlew validateDebugPublicResources
expected result : "Public Android resources unchanged."
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Public Android resources unchanged")
        }
    }
}
