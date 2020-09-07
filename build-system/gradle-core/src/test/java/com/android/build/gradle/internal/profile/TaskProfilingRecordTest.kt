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

package com.android.build.gradle.internal.profile

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class TaskProfilingRecordTest {

    private lateinit var testTaskRecord: TaskProfilingRecord

    @Before
    fun setup() {
        resetClockTo(100)
        testTaskRecord = TaskProfilingRecord(
            AnalyticsResourceManager(
                GradleBuildProfile.newBuilder(),
                ConcurrentHashMap(),
                false,
                null,
                ConcurrentHashMap(),
                null
            ),
            GradleBuildProfileSpan.newBuilder(),
            "dummy", ":dummy", "variant")
        testTaskRecord.setTaskStartTime(100)
    }

    @Test
    fun testAllWorkersFinished() {
        testTaskRecord.addWorker("first")
        testTaskRecord.addWorker("second")
        testTaskRecord.addWorker("third")

        assertThat(testTaskRecord.allWorkersFinished()).isFalse()
        testTaskRecord.get("first")?.executionStarted()
        testTaskRecord.get("first")?.executionFinished()
        assertThat(testTaskRecord.allWorkersFinished()).isFalse()
        testTaskRecord.get("second")?.executionStarted()
        testTaskRecord.get("second")?.executionFinished()
        assertThat(testTaskRecord.allWorkersFinished()).isFalse()
        testTaskRecord.get("third")?.executionStarted()
        testTaskRecord.get("third")?.executionFinished()
        assertThat(testTaskRecord.allWorkersFinished()).isTrue()
    }

    @Test
    fun testCalculateWaitTime() {
        testTaskRecord.addWorker("first")
        assertThat(testTaskRecord.allWorkersFinished()).isFalse()

        resetClockTo(300)
        testTaskRecord.get("first")?.executionStarted()
        assertThat(testTaskRecord.allWorkersFinished()).isFalse()

        resetClockTo(350)
        testTaskRecord.get("first")?.executionFinished()
        assertThat(testTaskRecord.minimumWaitTime()).isEqualTo(Duration.ofMillis(200))
    }

    @Test
    fun testTaskCompletesAfterWorkers() {
        testTaskRecord.addWorker("first")
        testTaskRecord.addWorker("second")

        testTaskRecord.get("second")?.executionStarted()
        testTaskRecord.get("first")?.executionStarted()

        resetClockTo(200)
        testTaskRecord.get("second")?.executionFinished()
        resetClockTo(220)
        testTaskRecord.get("first")?.executionFinished()
        assertThat(testTaskRecord.allWorkersFinished()).isTrue()

        resetClockTo(235)
        resetClockTo(240)
        testTaskRecord.setTaskEndTime(240)

        assertThat(testTaskRecord.minimumWaitTime()).isEqualTo(Duration.ZERO)

        assertThat(testTaskRecord.duration()).isEqualTo(Duration.ofMillis(140))
    }

    private fun resetClockTo(epochMillis: Long) {
        TaskProfilingRecord.clock = Clock.fixed(Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault())
    }
}
