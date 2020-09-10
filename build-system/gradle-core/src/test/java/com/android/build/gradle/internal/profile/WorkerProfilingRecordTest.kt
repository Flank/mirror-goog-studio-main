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

import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class WorkerProfilingRecordTest {

    private val testTaskRecord =
        object: TaskProfilingRecord(
            AnalyticsResourceManager(
                GradleBuildProfile.newBuilder(),
                ConcurrentHashMap(),
                false,
                null,
                ConcurrentHashMap(),
                null
            ),
            GradleBuildProfileSpan.newBuilder(),
            "dummy",
            ":dummy",
            "variant") {}

    @Test
    fun testNormalDuration() {
        TaskProfilingRecord.clock = Clock.fixed(Instant.ofEpochMilli(100), ZoneId.systemDefault())
        testTaskRecord.addWorker("first")
        val workerRecord = testTaskRecord.get("first")
        Truth.assertThat(workerRecord?.isStarted()).isFalse()
        TaskProfilingRecord.clock = Clock.fixed(Instant.ofEpochMilli(123), ZoneId.systemDefault())
        workerRecord?.executionStarted()
        Truth.assertThat(workerRecord?.waitTime()?.toMillis()).isEqualTo(23)
        TaskProfilingRecord.clock = Clock.fixed(Instant.ofEpochMilli(156), ZoneId.systemDefault())
        workerRecord?.executionFinished()
        Truth.assertThat(workerRecord?.duration()).isEqualTo(Duration.ofMillis(33))
    }

    @Test
    fun testUnStartedWorker() {
        testTaskRecord.addWorker("first")
        val workerRecord = testTaskRecord.get("first")
        Truth.assertThat(workerRecord?.isStarted()).isFalse()
        Truth.assertThat(workerRecord?.isFinished()).isFalse()
        Truth.assertThat(workerRecord?.waitTime()).isEqualTo(Duration.ZERO)
        Truth.assertThat(workerRecord?.duration()).isEqualTo(Duration.ZERO)
    }

    @Test
    fun testUnFinishedWorker() {
        TaskProfilingRecord.clock = Clock.fixed(Instant.ofEpochMilli(100), ZoneId.systemDefault())
        testTaskRecord.addWorker("first")
        val workerRecord = testTaskRecord.get("first")
        Truth.assertThat(workerRecord?.isStarted()).isFalse()
        TaskProfilingRecord.clock = Clock.fixed(Instant.ofEpochMilli(134), ZoneId.systemDefault())
        workerRecord?.executionStarted()
        Truth.assertThat(workerRecord?.isStarted()).isTrue()
        Truth.assertThat(workerRecord?.isFinished()).isFalse()
        Truth.assertThat(workerRecord?.waitTime()).isEqualTo(Duration.ofMillis(34))
        Truth.assertThat(workerRecord?.duration()).isEqualTo(Duration.ZERO)
    }

    @Test
    fun testFinishedWithoutStarting() {
        testTaskRecord.addWorker("first")
        val workerRecord = testTaskRecord.get("first")
        workerRecord?.executionFinished()
        Truth.assertThat(workerRecord?.duration()).isEqualTo(Duration.ZERO)
    }
}
