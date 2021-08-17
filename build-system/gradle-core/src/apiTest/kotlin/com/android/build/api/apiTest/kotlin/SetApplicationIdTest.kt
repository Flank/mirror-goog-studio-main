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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class SetApplicationIdTest: VariantApiBaseTest(
    TestType.Script
) {
    @Test
    fun setCustomApplicationIdFromTask() {
        given {
            tasksToInvoke.add(":app:assembleDebug")

            addModule(":app") {
                buildFile =
                    """
                plugins {
                        id("com.android.application")
                }

                android {
                    ${testingElements.addCommonAndroidBuildLogic()}
                }

                abstract class ApplicationIdProducerTask: DefaultTask() {

                    @get:OutputFile
                    abstract val outputFile: RegularFileProperty

                    @TaskAction
                    fun taskAction() {
                        outputFile.get().asFile.writeText("set.from.task." + name)
                    }
                }

                androidComponents {
                    onVariants(selector().withBuildType("debug")) { variant ->
                        val appIdProducer = tasks.register<ApplicationIdProducerTask>("${'$'}{variant.name}AppIdProducerTask") {
                            File(getBuildDir(), name).also {
                                outputFile.set(File(it, "appId.txt"))
                            }

                        }
                        variant.applicationId.set(appIdProducer.flatMap { task ->
                                task.outputFile.map { it.asFile.readText() }
                        })
                    }
                }
                """.trimIndent()

                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Demonstrate how to set a variant applicationId from a Task.

This sample shows how to create a Task which will output a file containing a single String. The
produced file will then be used to set the variant's applicationId using the file content.
Please note, the applicationId will only be known at execution time.

## To Run
./gradlew assembleDebug
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:debugAppIdProducerTask",
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
            // test the merged manifest and make sure it contains the right application.
            val manifestFile =
                File(testProjectDir.root, "${testName.methodName}/app/build/intermediates/merged_manifests/debug/AndroidManifest.xml")
            println(manifestFile.absolutePath)
            Truth.assertThat(manifestFile.exists()).isTrue()
            Truth.assertThat(manifestFile.readText()).contains("" +
                    """package="set.from.task.debugAppIdProducerTask"""")
        }
    }
}
