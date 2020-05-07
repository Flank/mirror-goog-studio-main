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

package com.android.tools.agent.layoutinspector.testing

import com.android.testutils.TestUtils
import java.io.File

private const val COMPOSE_TEST_DATA_PATH = "tools/base/dynamic-layout-inspector/src/testData/compose"

/**
 * Abstract base class for reading CSV files in tests.
 */
abstract class CsvReader(private val expectedColumnCount: Int) {
    protected var lineNumber = 0
      private set

    abstract fun resetState()

    abstract fun storeState()

    abstract fun parseColumns(columns: List<String>)

    fun read(fileName: String) {
        lineNumber = 0
        resetState()
        val fullFileName = "${TestUtils.getWorkspaceRoot()}/$COMPOSE_TEST_DATA_PATH/$fileName"
        File(fullFileName).forEachLine { readLine(it) }
        storeState()
    }

    private fun readLine(line: String) {
        lineNumber++
        if (line.startsWith("//")) {
            return
        }
        val columns = line.split(",").map { it.trim() }
        if (columns.size != expectedColumnCount) {
            error("Cannot parse line $lineNumber: Has ${columns.size} columns " +
                    "but: $expectedColumnCount was expected.")
        }
        parseColumns(columns)
    }

    protected fun parseIndent(quotedIndent: String): Int {
        if (!isQuoted(quotedIndent)) {
            error("Cannot parse line $lineNumber: Invalid indent in 1st column: Missing quotes.")
        }
        return quotedIndent.length - 2
    }

    protected fun parseQuotedString(str: String): String {
        if (!isQuoted(str)) {
            error("Cannot parse line $lineNumber: Invalid key in 2nd column: Missing quotes.")
        }
        return str.substring(1, str.length - 1)
    }

    protected fun parseInt(value: String): Int {
        try {
            return value.toInt()
        }
        catch (ex: Exception) {
            error("Cannot parse line $lineNumber: \"$value\" is not an integer")
        }
    }

    private fun isQuoted(str: String): Boolean =
        str.length >= 2 && str.first() == '"' && str.last() == '"'
}
