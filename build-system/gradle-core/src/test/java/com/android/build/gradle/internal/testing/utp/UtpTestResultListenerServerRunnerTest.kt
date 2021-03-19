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

import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File

/**
 * Unit tests for [UtpTestResultListenerServerRunner].
 */
@RunWith(JUnit4::class)
class UtpTestResultListenerServerRunnerTest {

    @get:Rule
    var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var mockServer: UtpTestResultListenerServer

    @Mock
    lateinit var mockTestResultListener: UtpTestResultListener

    @Before
    fun setUp() {
        `when`(mockServer.port).thenReturn(1234)
    }

    @Test
    fun startServer() {
        lateinit var certChainFile: File
        lateinit var privateKeyFile: File
        lateinit var trustCertCollectionFile: File

        val runner = UtpTestResultListenerServerRunner(mockTestResultListener) {
            cert, privateKey, trustCertCollection ->
            certChainFile = cert
            privateKeyFile = privateKey
            trustCertCollectionFile = trustCertCollection
            mockServer
        }

        assertThat(certChainFile).exists()
        assertThat(privateKeyFile).exists()
        assertThat(trustCertCollectionFile).exists()

        assertThat(runner.metadata.serverCert).isEqualTo(certChainFile)
        assertThat(runner.metadata.serverPort).isEqualTo(1234)
        assertThat(runner.metadata.clientCert).isEqualTo(trustCertCollectionFile)
        assertThat(runner.metadata.clientPrivateKey).exists()

        runner.close()

        verify(mockServer).close()

        assertThat(certChainFile).doesNotExist()
        assertThat(privateKeyFile).doesNotExist()
        assertThat(trustCertCollectionFile).doesNotExist()
        assertThat(runner.metadata.clientPrivateKey).doesNotExist()
    }

    @Test
    fun startServerFailed() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            UtpTestResultListenerServerRunner(null) { _, _, _ -> null }
        }

        assertThat(exception.message)
                .contains("Unable to start the UTP test results listener gRPC server.")
    }
}
