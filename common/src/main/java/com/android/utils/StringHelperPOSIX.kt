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
 * POSIX specific StringHelper that applies the following tokenization rules:
 *
 * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html
 *
 *  - A backslash that is not quoted shall preserve the literal value of the
 *    following character
 *  - Enclosing characters in single-quotes ( '' ) shall preserve the literal
 *    value of each character within the single-quotes.
 *  - Enclosing characters in double-quotes ( "" ) shall preserve the literal
 *    value of all characters within the double-quotes, with the exception of
 *    the characters dollar sign, backquote, and backslash
 */
object StringHelperPOSIX {
    /**
     * Split a single command line into individual commands with POSIX rules.
     *
     * @param commandLine the command line to be split
     * @return the list of individual commands
     */
    @JvmStatic
    fun splitCommandLine(commandLine: String): List<String> {
        val commands: MutableList<String> =
            Lists.newArrayList()
        var quoting = false
        var quote = '\u0000'
        var escaping = false
        var commandStart = 0
        var i = 0
        while (i < commandLine.length) {
            val c = commandLine[i]
            if (escaping) {
                escaping = false
                ++i
                continue
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true
                ++i
                continue
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true
                quote = c
                ++i
                continue
            } else if (quoting && c == quote) {
                quoting = false
                quote = '\u0000'
                ++i
                continue
            }
            if (!quoting) {
                // Match either && or ; separator
                var matched = 0
                if (commandLine.length > i + 1 && commandLine[i] == '&' && commandLine[i + 1] == '&'
                ) {
                    matched = 2
                } else if (commandLine[i] == ';') {
                    matched = 1
                }
                if (matched > 0) {
                    commands.add(commandLine.substring(commandStart, i))
                    i += matched
                    commandStart = i
                }
            }
            ++i
        }
        if (commandStart < commandLine.length) {
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
     * Tokenize a string with POSIX rules. This function should operate in the same manner as the
     * bash command-line.
     *
     * For escaped tokens, this can be validated with a script like this:
     *
     * echo 1=[/$1]
     *
     * echo 2=[/$2]
     *
     * echo 3=[/$3]
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
        var quote = '\u0000'
        var escaping = false
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
            if (escaping) {
                escaping = false
                if (c != '\n') {
                    if (returnEscaped) {
                        token.append(c)
                    }
                }
                continue
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true
                continue
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true
                quote = c
                continue
            } else if (quoting && c == quote) {
                quoting = false
                quote = '\u0000'
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