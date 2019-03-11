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
package com.android.ide.common.blame.parser

import com.android.ide.common.blame.Message
import com.android.ide.common.blame.SourcePosition
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser
import com.android.ide.common.blame.parser.util.OutputLineReader
import com.android.utils.ILogger
import com.android.utils.StdLogger
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class Aapt2OutputParserTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    lateinit var parser: ToolOutputParser

    @Mock lateinit var reader: OutputLineReader
    @Mock lateinit var logger: ILogger

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        parser = ToolOutputParser(
            Aapt2OutputParser(),
            Message.Kind.SIMPLE,
            StdLogger(StdLogger.Level.INFO)
        )
    }

    @Test
    fun parseErrorWithLineAndColumn() {
        val file = createTempFile("colors", ".xml", temporaryFolder.newFolder())
        val line = "${file.absolutePath}:5:5-49: invalid color."

        val messages = parser.parseToolOutput(line)

        assertThat(messages).hasSize(1)

        val message = messages[0]

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.text).isEqualTo("invalid color.")
        assertThat(message.toolName).isEqualTo("AAPT")
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(file.absolutePath)

        // error is in line 5, columns from 5 to 49
        assertThat(message.sourceFilePositions[0].position).isEqualTo(SourcePosition(4, 4, -1, 4, 48, -1))
    }

    @Test
    fun parseErrorWithPathOnly() {
        val file = createTempFile("foo", ".9.png", temporaryFolder.newFolder())
        val line = "${file.absolutePath}: error: failed to read PNG signature: file does not start with PNG signature.\n"
        val messages = parser.parseToolOutput(line, true)

        assertThat(messages).hasSize(1)

        val message = messages[0]

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.text).isEqualTo("error: failed to read PNG signature: file does not start with PNG signature.")
        assertThat(message.toolName).isEqualTo("AAPT")
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(file.absolutePath)
        assertThat(message.sourceFilePositions[0].position).isEqualTo(SourcePosition.UNKNOWN)
    }

    @Test
    fun testMultipleErrorsParsing() {
        val file1 = createTempFile("ic_launcher", ".xml", temporaryFolder.newFolder())
        val file2 = createTempFile("colors", ".xml", temporaryFolder.newFolder())
        val text = """2 exception was raised by workers:
                        com.android.builder.internal.aapt.v2.Aapt2Exception: Android resource linking failed
                        ${file1.absolutePath}:3: error: attribute android:drawadxble not found.
                        ${file2.absolutePath}:4:5-62: error: resource string/apgfp_name (aka com.example.myapplication:string/apgfp_name) not found.
      """.trimIndent()

        val messages = parser.parseToolOutput(text, true)

        assertThat(messages).hasSize(2)

        var message = messages[0]

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.text).isEqualTo("error: attribute android:drawadxble not found.")
        assertThat(message.toolName).isEqualTo("AAPT")
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(file1.absolutePath)

        // error is in line 3
        assertThat(message.sourceFilePositions[0].position).isEqualTo(SourcePosition(2, -1, -1, 2, -1, -1))

        message = messages[1]

        assertThat(message.kind).isEqualTo(Message.Kind.ERROR)
        assertThat(message.text).isEqualTo("error: resource string/apgfp_name (aka com.example.myapplication:string/apgfp_name) not found.")
        assertThat(message.toolName).isEqualTo("AAPT")
        assertThat(message.sourceFilePositions).hasSize(1)
        assertThat(message.sourceFilePositions[0].file.sourceFile!!.absolutePath).isEqualTo(file2.absolutePath)

        // error is in line 4, columns from 5 to 62
        assertThat(message.sourceFilePositions[0].position).isEqualTo(SourcePosition(3, 4, -1, 3, 61, -1))
    }
}