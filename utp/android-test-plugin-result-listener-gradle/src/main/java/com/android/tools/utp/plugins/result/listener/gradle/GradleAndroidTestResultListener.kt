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
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent.TestSuiteStarted
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerServiceGrpc
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerServiceGrpc.GradleAndroidTestResultListenerServiceStub
import com.google.protobuf.Any
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.Configurable
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.result.TestResultListener
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import io.grpc.ManagedChannel
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A UTP Android test result listener plugin which reports the results
 * to AGP through gRPC service.
 */
class GradleAndroidTestResultListener(
        private val channelFactory: (GradleAndroidTestResultListenerConfig) -> ManagedChannel = { config ->
            val sslContext = GrpcSslContexts.forClient().apply {
                trustManager(File(config.trustCertCollectionFilePath))
                keyManager(
                        File(config.resultListenerClientCertFilePath),
                        File(config.resultListenerClientPrivateKeyFilePath))
            }.build()
            NettyChannelBuilder
                    .forAddress("localhost", config.resultListenerServerPort)
                    .sslContext(sslContext)
                    .build()
        }) : TestResultListener, Configurable {
    companion object {
        @JvmStatic
        private val logger = getLogger()
    }

    private lateinit var deviceId: String
    private lateinit var channel: ManagedChannel
    private lateinit var grpcServiceStub: GradleAndroidTestResultListenerServiceStub
    private lateinit var requestObserver: StreamObserver<TestResultEvent>
    private val finishLatch: CountDownLatch = CountDownLatch(1)

    override fun configure(config: Config) {
        val pluginConfig = GradleAndroidTestResultListenerConfig.parseFrom(
                (config as ProtoConfig).configProto!!.value)

        deviceId = pluginConfig.deviceId
        channel = channelFactory(pluginConfig)
        grpcServiceStub = GradleAndroidTestResultListenerServiceGrpc.newStub(channel)

        val responseObserver = object: StreamObserver<RecordTestResultEventResponse> {
            override fun onNext(response: RecordTestResultEventResponse) {
            }

            override fun onError(error: Throwable) {
                logger.severe {"recordTestResultEvent failed with an error: $error" }
                finishLatch.countDown()
                throw error
            }

            override fun onCompleted() {
                finishLatch.countDown()
            }
        }

        requestObserver = grpcServiceStub.recordTestResultEvent(responseObserver)
    }

    override fun beforeTestSuite(testSuiteMetaData: TestSuiteResultProto.TestSuiteMetaData?) {
        val suiteStarted = TestSuiteStarted.newBuilder().apply {
            if (testSuiteMetaData != null) {
                this.testSuiteMetadata = Any.pack(testSuiteMetaData)
            }
        }.build()
        val event = createTestResultEvent().apply {
            testSuiteStarted = suiteStarted
        }.build()
        requestObserver.onNext(event)
    }

    override fun beforeTest(testCase: TestCaseProto.TestCase?) {
        val testCaseStarted = TestResultEvent.TestCaseStarted.newBuilder().apply {
            if (testCase != null) {
                this.testCase = Any.pack(testCase)
            }
        }.build()
        val event = createTestResultEvent().apply {
            this.testCaseStarted = testCaseStarted
        }.build()
        requestObserver.onNext(event)
    }

    override fun afterTest(testResult: TestResultProto.TestResult) {
        val testCaseFinished = TestResultEvent.TestCaseFinished.newBuilder().apply {
            testCaseResult = Any.pack(testResult)
        }.build()
        val event = createTestResultEvent().apply {
            this.testCaseFinished = testCaseFinished
        }.build()
        requestObserver.onNext(event)
    }

    override fun afterTestSuite(testSuiteResult: TestSuiteResultProto.TestSuiteResult) {
        val testSuiteFinished = TestResultEvent.TestSuiteFinished.newBuilder().apply {
            this.testSuiteResult = Any.pack(testSuiteResult)
        }.build()
        val event = createTestResultEvent().apply {
            this.testSuiteFinished = testSuiteFinished
        }.build()
        requestObserver.onNext(event)

        requestObserver.onCompleted()
        finishLatch.await()

        channel.shutdownNow().awaitTermination(1, TimeUnit.MINUTES)
    }

    private fun createTestResultEvent(): TestResultEvent.Builder {
        return TestResultEvent.newBuilder().apply {
            deviceId = this@GradleAndroidTestResultListener.deviceId
        }
    }
}
