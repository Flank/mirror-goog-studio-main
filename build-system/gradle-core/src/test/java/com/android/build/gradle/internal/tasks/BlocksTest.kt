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

package com.android.build.gradle.internal.tasks

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.junit.Test
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.fail

class BlocksTest {

    private class BlocksTestClass(val called: AtomicBoolean) {

        @Throws(IllegalArgumentException::class)
        fun methodNotThrowing() {
            called.set(true)
        }

        @Throws(IllegalArgumentException::class)
        fun methodThrowing() {
            throw IllegalStateException("Thrown")
        }

        @Throws(IllegalArgumentException::class)
        fun methodWithReturnNotThrowing(): Int {
            called.set(true)
            return 101
        }

        @Throws(IllegalArgumentException::class)
        fun methodWithReturnThrowing(): Int {
            throw IllegalStateException("Thrown")
        }
    }

    private val called= AtomicBoolean(false)
    private val testClass= BlocksTestClass(called)

    @Test
    fun testRecordSpan() {
        Blocks.recordSpan<Exception>("foo", "bar", GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES,
            testClass::methodNotThrowing)
        assertThat(called.get()).isTrue()
    }

    @Test(expected = IllegalStateException::class)
    fun testThrowingBlock() {
        Blocks.recordSpan<Exception>("foo", "bar", GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES,
            testClass::methodThrowing)
        fail("should not reach this statement")
    }

    @Test
    fun testWithReturnRecordSpan() {
        assertThat(testClass.methodWithReturnNotThrowing()).isEqualTo(101)
        Blocks.recordSpan<Int, Exception>("foo", "bar", GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES,
            testClass::methodWithReturnNotThrowing)
        assertThat(called.get()).isTrue()
    }

    @Test(expected = IllegalStateException::class)
    fun testWithReturnThrowingBlock() {
        Blocks.recordSpan<Int, Exception>("foo", "bar", GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES,
            testClass::methodWithReturnThrowing)
        fail("should not reach this statement")

    }
}