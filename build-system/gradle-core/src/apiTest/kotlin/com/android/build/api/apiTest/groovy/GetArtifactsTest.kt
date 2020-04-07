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

package com.android.build.api.apiTest.groovy

import com.android.build.api.apiTest.TestingElements
import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class GetArtifactsTest: VariantApiBaseTest(ScriptingLanguage.Groovy) {

    private val testingElements= TestingElements(scriptingLanguage)

    override fun tasksToInvoke(): Array<String> = arrayOf(":app:debugDisplayApks")

    @Test
    fun getApksTest() {
        given {
            addModule(":app") {
                buildFile =
                    """
                    plugins {
                        id 'com.android.application'
                    }

                    ${testingElements.getDisplayApksTask()}

                    android {
                        compileSdkVersion(29)
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }

                        onVariantProperties {
                            project.tasks.register(it.getName() + "DisplayApks", DisplayApksTask.class) {
                                it.apkFolder.set(operations.get(ArtifactTypes.APK.INSTANCE))
                                it.builtArtifactsLoader.set(operations.getBuiltArtifactsLoader())
                            }
                        }
                    }
                """.trimIndent()

                testingElements.addManifest(this)
            }
        }

        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got an APK")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}