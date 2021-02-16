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

    private lateinit var channel: ManagedChannel
    private lateinit var grpcServiceStub: GradleAndroidTestResultListenerServiceStub
    private lateinit var requestObserver: StreamObserver<TestResultEvent>
    private val finishLatch: CountDownLatch = CountDownLatch(1)

    override fun configure(config: Config) {
        val pluginConfig = GradleAndroidTestResultListenerConfig.parseFrom(
                (config as ProtoConfig).configProto!!.value)

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

    override fun onTestSuiteStarted() {
        val event = TestResultEvent.newBuilder().apply {
            testSuiteStarted = TestSuiteStarted.getDefaultInstance()
        }.build()
        requestObserver.onNext(event)
    }

    override fun beforeTest(testCase: TestCaseProto.TestCase?) {
        val event = TestResultEvent.newBuilder().apply {
            testCaseStarted = TestResultEvent.TestCaseStarted.getDefaultInstance()
        }.build()
        requestObserver.onNext(event)
    }

    override fun onTestResult(testResult: TestResultProto.TestResult) {
        val event = TestResultEvent.newBuilder().apply {
            testCaseFinished = TestResultEvent.TestCaseFinished.getDefaultInstance()
        }.build()
        requestObserver.onNext(event)
    }

    override fun onTestSuiteResult(testSuiteResult: TestSuiteResultProto.TestSuiteResult) {
        val event = TestResultEvent.newBuilder().apply {
            testSuiteFinished = TestResultEvent.TestSuiteFinished.getDefaultInstance()
        }.build()
        requestObserver.onNext(event)
    }

    override fun onTestSuiteFinished() {
        requestObserver.onCompleted()
        finishLatch.await()

        channel.shutdownNow().awaitTermination(1, TimeUnit.MINUTES)
    }
}
