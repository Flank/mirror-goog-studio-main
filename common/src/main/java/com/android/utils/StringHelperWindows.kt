/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.utils

import com.google.common.collect.Lists
import java.lang.Character.isWhitespace

/**
 * Windows specific StringHelper that applies the following tokenization rules:
 *
 * https://msdn.microsoft.com/en-us/library/17w5ykft.aspx
 *
 *  - A string surrounded by double quotation marks ("string") is interpreted
 *    as a single argument, regardless of white space contained within. A
 *    quoted string can be embedded in an argument.
 *  - A double quotation mark preceded by a backslash (\") is interpreted as a
 *    literal double quotation mark character (").
 *  - Backslashes are interpreted literally, unless they immediately precede a
 *    double quotation mark.
 *  - If an even number of backslashes is followed by a double quotation mark,
 *    one backslash is placed in the argv array for every pair of backslashes,
 *    and the double quotation mark is interpreted as a string delimiter.
 *  - If an odd number of backslashes is followed by a double quotation mark,
 *    one backslash is placed in the argv array for every pair of backslashes,
 *    and the double quotation mark is "escaped" by the remaining backslash.
 */
object StringHelperWindows {
    /**
     * Split a single command line into individual commands with Windows rules.
     *
     * @param commandLine the command line to be split
     * @return the list of individual commands
     */
    @JvmStatic
    fun splitCommandLine(commandLine: String): List<String> {
        val commands: MutableList<String> =
            Lists.newArrayList()
        var quoting = false
        var escapingQuotes = false
        var escapingOthers = false
        var commandStart = 0
        val length = commandLine.length
        var i = 0
        while (i < length) {
            val c = commandLine[i]
            if (c == '"' && !escapingQuotes) {
                quoting = !quoting
                ++i
                continue
            }
            if (escapingQuotes) {
                escapingQuotes = false
            } else if (c == '\\') {
                escapingQuotes = true
                ++i
                continue
            }
            if (escapingOthers) {
                escapingOthers = false
                ++i
                continue
            } else if (c == '^') {
                escapingOthers = true
                ++i
                continue
            }
            if (!quoting) {
                // Check for separators & and &&
                if (commandLine[i] == '&') {
                    commands.add(commandLine.substring(commandStart, i))
                    i++
                    if (commandLine.length > i && commandLine[i] == '&') {
                        i++
                    }
                    commandStart = i
                }
            }
            ++i
        }
        if (commandStart < length) {
            commands.add(commandLine.substring(commandStart))
        }
        return commands
    }

    @JvmStatic
    fun tokenizeCommandLineToEscaped(commandLine: String): List<String> {
        return tokenizeCommandLine(commandLine, true)
    }

    @JvmStatic
    fun tokenizeCommandLineToRaw(commandLine: String): List<String> {
        return tokenizeCommandLine(commandLine, false)
    }

    /**
     * Tokenize a string with Windows rules.
     *
     * @param commandLine the string to be tokenized
     * @param returnEscaped if true then return escaped, otherwise return original
     * @return the list of tokens
     */
    private fun tokenizeCommandLine(
        commandLine: String, returnEscaped: Boolean
    ): List<String> {
        val tokens: MutableList<String> =
            Lists.newArrayList()
        val token = StringBuilder()
        var quoting = false
        var escapingQuotes = false
        var escapingOthers = false
        var skipping = true
        for (c in commandLine) {
            if (skipping) {
                skipping = if (isWhitespace(c)) {
                    continue
                } else {
                    false
                }
            }
            if (quoting || !isWhitespace(c)) {
                if (!returnEscaped) {
                    token.append(c)
                }
            }
            if (c == '"') {
                // delete one slash for every pair of preceding slashes
                if (returnEscaped) {
                    var j = token.length - 2
                    while (j >= 0 && token[j] == '\\' && token[j + 1] == '\\') {
                        token.deleteCharAt(j)
                        j -= 2
                    }
                }
                if (escapingQuotes) {
                    if (returnEscaped) {
                        token.deleteCharAt(token.length - 1)
                    }
                } else {
                    quoting = !quoting
                    continue
                }
            }
            if (escapingQuotes) {
                escapingQuotes = false
            } else if (c == '\\') {
                escapingQuotes = true
            }
            if (escapingOthers) {
                escapingOthers = false
                if (c == '\n') {
                    continue
                }
            } else if (!quoting && c == '^') {
                escapingOthers = true
                continue
            }
            if (!quoting && isWhitespace(c)) {
                skipping = true
                if (token.isNotEmpty()) {
                    tokens.add(token.toString())
                }
                token.setLength(0)
                continue
            }
            if (returnEscaped) {
                token.append(c)
            }
        }
        if (token.isNotEmpty()) {
            tokens.add(token.toString())
        }
        return tokens
    }
}