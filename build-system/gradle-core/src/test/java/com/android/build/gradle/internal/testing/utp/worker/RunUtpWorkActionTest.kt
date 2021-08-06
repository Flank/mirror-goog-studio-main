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

package com.android.build.gradle.internal.testing.utp.worker

import com.android.build.gradle.internal.testing.utp.UtpDependency
import com.android.testutils.MockitoKt.any
import java.io.File
import org.gradle.api.Action
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * Unit tests for [RunUtpWorkAction].
 */
class RunUtpWorkActionTest {
    @get:Rule
    var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockExecOperations: ExecOperations
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockRunUtpWorkParameters: RunUtpWorkParameters
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockJavaExecSpec: JavaExecSpec
    @Mock
    private lateinit var mockExecResult: ExecResult

    @Before
    fun setupMocks() {
        `when`(mockRunUtpWorkParameters.launcherJar.asFile.get().absolutePath)
            .thenReturn("launcherJar")
        `when`(mockRunUtpWorkParameters.coreJar.asFile.get().absolutePath)
            .thenReturn("coreJar")
        `when`(mockRunUtpWorkParameters.runnerConfig.asFile.get().absolutePath)
            .thenReturn("runnerConfig")
        `when`(mockRunUtpWorkParameters.serverConfig.asFile.get().absolutePath)
            .thenReturn("serverConfig")
        `when`(mockRunUtpWorkParameters.loggingProperties.asFile.get().absolutePath)
            .thenReturn("loggingProperties")
        `when`(mockExecOperations.javaexec(any())).then {
            it.getArgument<Action<JavaExecSpec>>(0).execute(mockJavaExecSpec)
            mockExecResult
        }
    }

    private fun createRunUtpWorkAction(): RunUtpWorkAction {
        val action = mock(RunUtpWorkAction::class.java, CALLS_REAL_METHODS)
        doReturn(mockExecOperations).`when`(action).execOperations
        doReturn(mockRunUtpWorkParameters).`when`(action).parameters
        return action
    }

    @Test
    fun execute() {
        createRunUtpWorkAction().execute()

        verify(mockJavaExecSpec).classpath(File("launcherJar"))
        verify(mockJavaExecSpec.mainClass).set(UtpDependency.LAUNCHER.mainClass)
        verify(mockJavaExecSpec).args(listOf(
            "coreJar", "--proto_config=runnerConfig", "--proto_server_config=serverConfig"))
        verify(mockJavaExecSpec).jvmArgs(listOf(
            "-Djava.util.logging.config.file=loggingProperties"))

        verify(mockExecResult).rethrowFailure()
    }
}
