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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkParameters
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import java.io.File
import org.gradle.api.Action
import org.gradle.workers.WorkQueue
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * Unit tests for UtpTestUtils.kt.
 */
class UtpTestUtilsTest {
    @get:Rule
    var mockitoJUnitRule: MockitoRule =
        MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    private lateinit var mockRunnerConfig: File
    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var mockConfigFactory: UtpConfigFactory
    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var mockUtpDependencies: UtpDependencies
    @Mock
    private lateinit var mockWorkQueue: WorkQueue
    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var mockRunUtpWorkParameters: RunUtpWorkParameters

    @Test
    fun testRunUtpTestSuite() {
        runUtpTestSuite(
            mockRunnerConfig,
            mockConfigFactory,
            mockUtpDependencies,
            mockWorkQueue
        )

        lateinit var setRunUtpWorkParametersAction: Action<in RunUtpWorkParameters>
        verify(mockWorkQueue).submit(
            eq(RunUtpWorkAction::class.java),
            argThat {
                setRunUtpWorkParametersAction = it
                true
            })

        setRunUtpWorkParametersAction.execute(mockRunUtpWorkParameters)

        mockRunUtpWorkParameters.run {
            verify(launcherJar).set(mockUtpDependencies.launcher.singleFile)
            verify(coreJar).set(mockUtpDependencies.core.singleFile)
            verify(runnerConfig).set(mockRunnerConfig)
            verify(serverConfig).set(argThat<File> {
                it.exists()
            })
            verify(loggingProperties).set(argThat<File> {
                it.exists()
            })
        }
    }
}
