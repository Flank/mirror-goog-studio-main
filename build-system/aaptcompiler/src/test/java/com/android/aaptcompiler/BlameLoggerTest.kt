/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.aaptcompiler

import com.android.utils.ILogger
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class BlameLoggerTest {

    @Test
    fun rewriteSingleSource() {
        val mockLogger = MockLogger()
        val blameLogger = getMockBlameLogger(mockLogger)

        val result = blameLogger.getOriginalSource(BlameLogger.Source("foo/bar.xml", 3, 5))
        assertThat(result.sourcePath).isEqualTo("foo/bar.xml.rewritten")
        assertThat(result.line).isEqualTo(4)
        assertThat(result.column).isEqualTo(7)
    }

    @Test
    fun testsLoggedMessageRewritten() {
        val mockLogger = MockLogger()
        val blameLogger = getMockBlameLogger(mockLogger)

        blameLogger.error("Failed to read file", BlameLogger.Source("foo/bar.xml", 3, 5))
        assertThat(mockLogger.errors).hasSize(1)
        val loggedError = mockLogger.errors.single()
        assertThat(loggedError.first).contains("bar.xml.rewritten:4:7: Failed to read file")
        assertThat(loggedError.second).isNull()
    }

    @Test
    fun testLogErrorCompatibleWithRelativeResources() {
        val mockLogger = MockLogger()
        val sourceSetMap = mapOf("com.example.test-app-0" to "/baz")
        val blameLogger = BlameLogger(mockLogger, sourceSetMap)
        blameLogger.error("Failed to read file",
                BlameLogger.Source("com.example.test-app-0:/bar.xml", 3, 5))
        assertThat(mockLogger.errors).hasSize(1)
        val loggedError = mockLogger.errors.single()
        assertThat(loggedError.first).contains("/baz/bar.xml:3:5: Failed to read file")
    }

    @Test
    fun testGetOutputSourceChangesRelativeSourceSetPathToAbsolute() {
        val mockLogger = MockLogger()
        val sourceSetMap = mapOf("com.example.test-app-0" to "/baz")
        val blameLogger = BlameLogger(mockLogger, sourceSetMap)
        val relativeSourceFile = BlameLogger.Source("com.example.test-app-0:/foo.xml", 1)
        assertThat(blameLogger.getOutputSource(relativeSourceFile).sourcePath)
                .isEqualTo("/baz/foo.xml")
    }

    @Test
    fun testShouldConvertRelativePathFormatToAbsolutePathFormat() {
        val sourceSetPathMap =
                mapOf("com.foobar.myproject.app-0" to "/usr/a/b/c/d/myproject/src/main")
        val testRelativePath = "com.foobar.myproject.app-0:/res/layout/activity_map_tv.xml"
        val expectedAbsolutePath = "/usr/a/b/c/d/myproject/src/main/res/layout/activity_map_tv.xml"
        val blameLoggerSource = BlameLogger.Source(testRelativePath)

        assertThat(blameLoggerSource.getAbsoluteSourcePath(sourceSetPathMap))
                .isEqualTo(expectedAbsolutePath)
    }

    class MockLogger: ILogger {
        val warnings: MutableList<String> = mutableListOf()
        val infos: MutableList<String> = mutableListOf()
        val errors: MutableList<Pair<String, Throwable?>> = mutableListOf()
        val verboses: MutableList<String> = mutableListOf()

        override fun warning(msgFormat: String, vararg args: Any?) {
            warnings.add(String.format(msgFormat, args))
        }

        override fun info(msgFormat: String, vararg args: Any?) {
            infos.add(String.format(msgFormat, args))
        }

        override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) {
            errors.add(Pair(String.format(msgFormat!!, args), t))
        }

        override fun verbose(msgFormat: String, vararg args: Any?) {
            verboses.add(String.format(msgFormat, args))
        }
    }
}

fun getMockBlameLogger(
        mockLogger: BlameLoggerTest.MockLogger,
        identifiedSourceSetMap : Map<String, String> = emptyMap()) =
    BlameLogger(mockLogger, identifiedSourceSetMap) {
        BlameLogger.Source(it.sourcePath + ".rewritten", it.line + 1, it.column + 2)
    }
