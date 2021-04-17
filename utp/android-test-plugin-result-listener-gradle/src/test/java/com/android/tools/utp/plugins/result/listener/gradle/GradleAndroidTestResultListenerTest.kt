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

package com.android.tools.utp.plugins.result.listener.gradle

import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfigProto.GradleAndroidTestResultListenerConfig
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerServiceGrpc.GradleAndroidTestResultListenerServiceImplBase
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.proto.api.core.ExtensionProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.grpc.testing.GrpcCleanupRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito.mock

/**
 * Unit test for [GradleAndroidTestResultListener].
 */
@RunWith(JUnit4::class)
class GradleAndroidTestResultListenerTest {

    @get:Rule
    val grpcCleanup = GrpcCleanupRule()

    lateinit var testListener: GradleAndroidTestResultListener
    var capturedPortNumber: Int? = null
    val capturedRequests = mutableListOf<TestResultEvent>()
    var requestCompleted = false

    private val serviceImpl = mock(GradleAndroidTestResultListenerServiceImplBase::class.java,
            delegatesTo<GradleAndroidTestResultListenerServiceImplBase>(
                    object: GradleAndroidTestResultListenerServiceImplBase() {
                        override fun recordTestResultEvent(
                                responseObserver: StreamObserver<RecordTestResultEventResponse>):
                                StreamObserver<TestResultEvent> {
                    return object: StreamObserver<TestResultEvent> {
                        override fun onNext(event: TestResultEvent) {
                            capturedRequests.add(event)
                        }

                        override fun onError(error: Throwable) {}

                        override fun onCompleted() {
                            requestCompleted = true
                            responseObserver.onNext(
                                    RecordTestResultEventResponse.getDefaultInstance())
                            responseObserver.onCompleted()
                        }
                    }
                }
            }))

    @Before
    fun setUp() {
        val serverName = InProcessServerBuilder.generateName()
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(serviceImpl)
                .build()
                .start())

        testListener = GradleAndroidTestResultListener { config ->
            capturedPortNumber = config.resultListenerServerPort
            grpcCleanup.register(
                    InProcessChannelBuilder
                            .forName(serverName)
                            .directExecutor()
                            .build())
        }

        val config = Any.pack(GradleAndroidTestResultListenerConfig.newBuilder().apply {
            resultListenerServerPort = 1234
            deviceId = "deviceIdString"
        }.build())
        val protoConfig = object: ProtoConfig {
            override val configProto: Any
                get() = config
            override val configResource: ExtensionProto.ConfigResource?
                get() = null
        }

        testListener.configure(protoConfig)
    }

    @Test
    fun testSuiteFinishedSuccessfully() {
        assertThat(capturedPortNumber).isEqualTo(1234)

        testListener.apply {
            beforeTestSuite(TestSuiteResultProto.TestSuiteMetaData.getDefaultInstance())
            beforeTest(null)
            afterTest(TestResult.getDefaultInstance())
            afterTestSuite(TestSuiteResult.getDefaultInstance())
        }

        assertThat(capturedRequests).containsExactly(
                TestResultEvent.newBuilder().apply {
                    deviceId = "deviceIdString"
                    testSuiteStartedBuilder.apply {
                        testSuiteMetadata = Any.pack(
                                TestSuiteResultProto.TestSuiteMetaData.getDefaultInstance())
                    }
                }.build(),
                TestResultEvent.newBuilder().apply {
                    deviceId = "deviceIdString"
                    testCaseStarted = TestResultEvent.TestCaseStarted.getDefaultInstance()
                }.build(),
                TestResultEvent.newBuilder().apply {
                    deviceId = "deviceIdString"
                    testCaseFinishedBuilder.apply {
                        testCaseResult = Any.pack(TestResult.getDefaultInstance())
                    }
                }.build(),
                TestResultEvent.newBuilder().apply {
                    deviceId = "deviceIdString"
                    testSuiteFinishedBuilder.apply {
                        testSuiteResult = Any.pack(TestSuiteResult.getDefaultInstance())
                    }
                }.build()
        ).inOrder()
        assertThat(requestCompleted).isTrue()
    }
}
