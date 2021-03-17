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

import com.android.testutils.MockitoKt.eq
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent.TestSuiteStarted
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerServiceGrpc
import com.google.common.truth.Truth.assertThat
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.grpc.testing.GrpcCleanupRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.io.IOException

/**
 * Unit tests for [UtpTestResultListenerServer].
 */
class UtpTestResultListenerServerTest {

    @get:Rule
    val grpcCleanup = GrpcCleanupRule()

    @get:Rule
    var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var mockResultListenerClientCert: File
    @Mock
    lateinit var mockResultListenerClientPrivateKey: File
    @Mock
    lateinit var mockTrustCertCollection: File
    @Mock
    lateinit var mockTestResultListener: UtpTestResultListener

    @Test
    fun startServer() {
        val serverName = InProcessServerBuilder.generateName()

        var capturedPort: Int? = null

        val server = UtpTestResultListenerServer.startServer(
                mockTrustCertCollection,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection,
                mockTestResultListener,
                defaultPort = 1234,
                maxRetryAttempt = 1
        ) { port ->
            capturedPort = port
            InProcessServerBuilder.forName(serverName).directExecutor()
        }

        requireNotNull(server)
        grpcCleanup.register(server.server)

        assertThat(capturedPort).isEqualTo(1234)

        server.close()
    }

    @Test
    fun availablePortNotFound() {
        val server = UtpTestResultListenerServer.startServer(
                mockTrustCertCollection,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection,
                mockTestResultListener,
                defaultPort = 1234,
                maxRetryAttempt = 1
        ) { port ->
            throw IOException("port: ${port} is not available")
        }

        assertThat(server).isNull()
    }

    @Test
    fun availablePortFoundAfterRetry() {
        val serverName = InProcessServerBuilder.generateName()

        var capturedPort: Int? = null

        val server = UtpTestResultListenerServer.startServer(
                mockTrustCertCollection,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection,
                mockTestResultListener,
                defaultPort = 1234,
                maxRetryAttempt = 2
        ) { port ->
            if (port == 1234) {
                throw IOException("port: ${port} is not available")
            }
            capturedPort = port
            InProcessServerBuilder.forName(serverName).directExecutor()
        }

        requireNotNull(server)
        grpcCleanup.register(server.server)

        assertThat(capturedPort).isEqualTo(1235)

        server.close()
    }

    @Test
    fun recordTestResultEvent() {
        val serverName = InProcessServerBuilder.generateName()

        val server = UtpTestResultListenerServer.startServer(
                mockTrustCertCollection,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection,
                mockTestResultListener,
                defaultPort = 1234,
                maxRetryAttempt = 1
        ) {
            InProcessServerBuilder.forName(serverName).directExecutor()
        }

        requireNotNull(server)
        grpcCleanup.register(server.server)

        val stub = GradleAndroidTestResultListenerServiceGrpc.newStub(
                grpcCleanup.register(
                        InProcessChannelBuilder
                                .forName(serverName)
                                .directExecutor()
                                .build()))

        lateinit var response: RecordTestResultEventResponse
        var completed = false
        val requestObserver = stub.recordTestResultEvent(
                object: StreamObserver<RecordTestResultEventResponse>{
                    override fun onNext(res: RecordTestResultEventResponse) {
                        response = res
                    }

                    override fun onError(error: Throwable) {}

                    override fun onCompleted() {
                        completed = true
                    }
                })

        requestObserver.onNext(
                TestResultEvent.newBuilder().apply {
                    testSuiteStarted = TestSuiteStarted.newBuilder().apply {
                        deviceId = "testDeviceId"
                    }.build()
                }.build()
        )
        requestObserver.onCompleted()

        assertThat(completed).isTrue()
        assertThat(response).isEqualTo(RecordTestResultEventResponse.getDefaultInstance())

        inOrder(mockTestResultListener).apply {
            verify(mockTestResultListener).onTestResultEvent(
                    eq(TestResultEvent.newBuilder().apply {
                        testSuiteStarted = TestSuiteStarted.newBuilder().apply {
                            deviceId = "testDeviceId"
                        }.build()
                    }.build()))
        }

        server.close()
    }
}
