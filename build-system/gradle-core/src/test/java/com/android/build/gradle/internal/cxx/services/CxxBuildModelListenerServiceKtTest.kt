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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.getCxxBuildModel
import com.android.build.gradle.internal.cxx.model.setCxxBuildModel
import com.google.common.truth.Truth.*

import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CxxBuildModelListenerServiceKtTest {

    @Test
    fun `invoke createBuildModelListenerService`() {
        BasicCmakeMock().apply {
            var value = 0
            build.doOnceBeforeJsonGeneration("My Action") {
                ++value
            }
            assertThat(build.executeListenersOnceBeforeJsonGeneration()).isTrue()
            assertThat(build.executeListenersOnceBeforeJsonGeneration()).isTrue()

            assertThat(value).isEqualTo(1)
        }
    }

    @Test
    fun `invoke createBuildModelListenerService with errors`() {
        BasicCmakeMock().apply {
            var value = 0
            build.doOnceBeforeJsonGeneration("My Action") {
                ++value
                errorln("Report an error that should prevent JSON generation")
            }
            assertThat(build.executeListenersOnceBeforeJsonGeneration()).isFalse()
            assertThat(build.executeListenersOnceBeforeJsonGeneration()).isFalse()
            assertThat(value).isEqualTo(1)
        }
    }

    /**
     * This test tries to check for race conditions in [executeListenersOnceBeforeJsonGeneration]
     * and [doOnceBeforeJsonGeneration]. The number of loops and threads was chosen to fairly
     * reliably trigger race conditions when the code isn't properly synchronized but to run fast
     * enough when the code is properly synchronized.
     * When checked it, this test is always supposed to pass and isn't expected to be flaky.
     */
    @Test
    fun `invoke createBuildModelListenerService on multiple threads`() {
        BasicCmakeMock().apply {
            (0..3000).forEach { index ->
                setCxxBuildModel(null)
                val build = getCxxBuildModel(gradle)
                val listenersExecuted = AtomicInteger()
                val threadsExecuted = AtomicInteger()
                build.doOnceBeforeJsonGeneration("My Action") {
                    listenersExecuted.incrementAndGet()
                }
                val threads = (0..3).map {
                    thread(start = false) {
                        val shouldGenerateJson = build.executeListenersOnceBeforeJsonGeneration()
                        assertThat(listenersExecuted.get())
                            .named("All threads must block until listeners execute")
                            .isEqualTo(1)
                        threadsExecuted.incrementAndGet()
                        assertThat(shouldGenerateJson).isTrue()
                    }
                }
                threads.forEach { it.start() }
                threads.forEach { it.join() }

                assertThat(threadsExecuted.get()).named("Index = $index").isEqualTo(threads.size)
            }
        }
    }
}