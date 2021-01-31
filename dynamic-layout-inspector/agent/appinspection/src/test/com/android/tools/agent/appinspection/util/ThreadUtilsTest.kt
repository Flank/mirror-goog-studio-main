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

package com.android.tools.agent.appinspection.util

import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

class ThreadUtilsTest {

    @get:Rule
    val mainLooperRule = MainLooperRule()

    @Test
    fun canCreateInspectorThreads() {
        val inspectorThreads = listOf(
            ThreadUtils.newThread {
                ThreadUtils.assertOffMainThread()
            },
            ThreadUtils.newThread {
                ThreadUtils.assertOffMainThread()
            }
        )

        // New thread is created each time
        assertThat(inspectorThreads.toSet()).hasSize(2)

        val testThread = Thread.currentThread()
        assertThat(inspectorThreads).doesNotContain(testThread)

        inspectorThreads.forEach { it.start() }
        inspectorThreads.forEach { it.join() }
    }

    @Test
    fun assertsWork() {
        ThreadUtils.assertOffMainThread()
        try {
            ThreadUtils.assertOnMainThread()
            fail()
        }
        catch (ignored: Exception) {
        }

        ThreadUtils.runOnMainThread {
            ThreadUtils.assertOnMainThread()
            try {
                ThreadUtils.assertOffMainThread()
                fail()
            }
            catch (ignored: Exception) {
            }
        }.get() // Wait for main thread to finish
    }

    @Test
    fun canRunOnMainThreadFromBackgroundThread() {
        ThreadUtils.assertOffMainThread()

        val testValue: Any = Random.nextInt() // Just use any value, doesn't matter
        val result = ThreadUtils.runOnMainThread {
            ThreadUtils.assertOnMainThread()
            testValue
        }

        assertThat(result.get()).isEqualTo(testValue)
    }

    @Test
    fun canRunOnMainThreadWithinMainThread() {
        ThreadUtils.assertOffMainThread()
        ThreadUtils.runOnMainThread {
            ThreadUtils.assertOnMainThread()

            val testValue: Any = Random.nextInt() // Just use any value, doesn't matter
            val result = ThreadUtils.runOnMainThread {
                ThreadUtils.assertOnMainThread()
                testValue
            }

            assertThat(result.get()).isEqualTo(testValue)
        }.get() // Force wait until finished
    }
}
