/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.MessageJsonSerializer
import com.android.ide.common.blame.parser.JsonEncodedGradleMessageParser.STDOUT_ERROR_TAG
import com.android.ide.common.blame.parser.aapt.AbstractAaptOutputParser.AAPT_TOOL_NAME
import com.android.ide.common.resources.CompileResourceRequest
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import org.gradle.api.logging.Logger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class Aapt2ErrorUtilsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var logger: Logger

    val gson = GsonBuilder().disableHtmlEscaping()
        .apply { MessageJsonSerializer.registerTypeAdapters(this) }.create()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testMessageRewriting() {
        val request = CompileResourceRequest(
            inputFile = temporaryFolder.newFile("processed"),
            inputDirectoryName = "values",
            outputDirectory = temporaryFolder.newFolder(),
            originalInputFile = temporaryFolder.newFile("original")
        )

        val aaptException = Aapt2Exception.create(
            description = "desc inputFile=" + request.inputFile.absolutePath,
            cause = null,
            output = "output inputFile=" + request.inputFile.absolutePath,
            processName = "process",
            command = "command inputFile=" + request.inputFile.absolutePath
        )

        val rewritten = rewriteCompileException(
            aaptException,
            request,
            SyncOptions.ErrorFormatMode.HUMAN_READABLE,
            true,
            logger
        )

        assertThat(rewritten.description).contains(request.originalInputFile.absolutePath)
        assertThat(rewritten.output).contains(request.originalInputFile.absolutePath)
        assertThat(rewritten.command).contains(request.inputFile.absolutePath)
    }

    @Test
    fun testMessageLogging() {
        val captor = ArgumentCaptor.forClass(String::class.java)

        val request = CompileResourceRequest(
            inputFile = temporaryFolder.newFile("processed"),
            inputDirectoryName = "values",
            outputDirectory = temporaryFolder.newFolder(),
            originalInputFile = temporaryFolder.newFile("original")
        )

        val aaptException = Aapt2Exception.create(
            description = "desc inputFile=" + request.inputFile.absolutePath,
            cause = null,
            output = "ERROR: " + request.inputFile.absolutePath + ":1 error message",
            processName = "process",
            command = "command inputFile=" + request.inputFile.absolutePath
        )

        val rewritten = rewriteCompileException(
            aaptException,
            request,
            SyncOptions.ErrorFormatMode.MACHINE_PARSABLE,
            true,
            logger
        )

        verify(logger).error(captor.capture())

        assertThat(captor.allValues).hasSize(1)

        val message = getMessage(captor.allValues[0])

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.toolName).isEqualTo(AAPT_TOOL_NAME)
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(request.originalInputFile.absolutePath)
        assertThat(message.sourceFilePositions[0].position.startLine).isEqualTo(0)
        assertThat(message.text).isEqualTo(rewritten.description)
        assertThat(message.rawMessage).isEqualTo(rewritten.output)
    }

    @Test
    fun testMultipleMessagesLogging() {
        val captor = ArgumentCaptor.forClass(String::class.java)

        val request = CompileResourceRequest(
            inputFile = temporaryFolder.newFile("processed"),
            inputDirectoryName = "values",
            outputDirectory = temporaryFolder.newFolder(),
            originalInputFile = temporaryFolder.newFile("original")
        )

        val aaptException = Aapt2Exception.create(
            description = "desc inputFile=" + request.inputFile.absolutePath,
            cause = null,
            output = "ERROR: " + request.inputFile.absolutePath + ":1 error message 1\n" +
                    "ERROR: " + request.inputFile.absolutePath + ":2 error message 2",
            processName = "process",
            command = "command inputFile=" + request.inputFile.absolutePath
        )

        val rewritten = rewriteCompileException(
            aaptException,
            request,
            SyncOptions.ErrorFormatMode.MACHINE_PARSABLE,
            true,
            logger
        )

        verify(logger, times(2)).error(captor.capture())

        assertThat(captor.allValues).hasSize(2)

        var message = getMessage(captor.allValues[0])

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.toolName).isEqualTo(AAPT_TOOL_NAME)
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(request.originalInputFile.absolutePath)
        assertThat(message.sourceFilePositions[0].position.startLine).isEqualTo(0)
        assertThat(message.text).isEqualTo(rewritten.description)
        assertThat(rewritten.output).contains(message.rawMessage)

        message = getMessage(captor.allValues[1])

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.toolName).isEqualTo(AAPT_TOOL_NAME)
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(request.originalInputFile.absolutePath)
        assertThat(message.sourceFilePositions[0].position.startLine).isEqualTo(1)
        assertThat(message.text).isEqualTo(rewritten.description)
        assertThat(rewritten.output).contains(message.rawMessage)
    }

    private fun getMessage(loggedMessage: String): Message {
        assertThat(loggedMessage).startsWith(STDOUT_ERROR_TAG)

        return gson.fromJson(
            loggedMessage.substring(STDOUT_ERROR_TAG.length).trim(),
            Message::class.java
        )
    }
}