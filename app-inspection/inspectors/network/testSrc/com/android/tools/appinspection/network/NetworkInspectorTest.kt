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

package com.android.tools.appinspection.network

import androidx.inspection.ArtTooling
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorExecutors
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class NetworkInspectorTest {

    @get:Rule
    val inspectorRule = NetworkInspectorRule()

    @Test
    fun speedDataCollection() = runBlocking<Unit> {
        val speedEventCount = inspectorRule.connection.speedData.size
        delay(1000)
        // Test the speed data is being collected at regular intervals in the background.
        assertThat(inspectorRule.connection.speedData.size).isGreaterThan(speedEventCount)
    }

    @Test
    fun failToAddOkHttp2And3Hooks_doesNotThrowException() {
        // This test simulates an app that does not depend on OkHttp and the
        // inspector can be initialized without problems.
        val environment = object : InspectorEnvironment {
            override fun artTooling(): ArtTooling {
                return object : ArtTooling by inspectorRule.environment.artTooling() {
                    override fun <T : Any?> registerExitHook(
                        originClass: Class<*>,
                        originMethod: String,
                        exitHook: ArtTooling.ExitHook<T>
                    ) {
                        if (originClass.name.endsWith("OkHttpClient")) {
                            throw NoClassDefFoundError()
                        } else {
                            inspectorRule.environment.artTooling()
                                .registerExitHook(originClass, originMethod, exitHook)
                        }
                    }
                }
            }

            override fun executors(): InspectorExecutors {
                return inspectorRule.environment.executors()
            }
        }

        // This test passes if no exception thrown here.
        NetworkInspector(inspectorRule.connection, environment)
    }
}
