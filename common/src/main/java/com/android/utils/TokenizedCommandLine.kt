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

package com.android.utils

import com.android.SdkConstants
import com.android.SdkConstants.PLATFORM_WINDOWS

const val ZERO_ALLOC_TOKENIZER_END_OF_TOKEN = Int.MAX_VALUE
const val ZERO_ALLOC_TOKENIZER_END_OF_COMMAND = Int.MIN_VALUE

/**
 * Parse a Windows or POSIX command-line without allocating per-character
 * memory. It does this by amortizing with a shared a buffer from parses of
 * other command lines.
 *
 * The first element of the buffer is a generation counter. If the buffer
 * is shared between multiple parses each parse will increment the generation.
 * If a buffer is used with an older [TokenizedCommandLine] then that's a
 * bug in the calling code so an exception will be thrown.
 *
 * After the first element, the buffer consists of indexes of characters within
 * the original [commandLine] delimited by [ZERO_ALLOC_TOKENIZER_END_OF_TOKEN]
 * and terminated by [ZERO_ALLOC_TOKENIZER_END_OF_COMMAND]
 *
 * @param commandLine the command-line to tokenize
 * @param raw then special characters are sent to the receiver. On Windows,
 *        that's double-quote("), backslash(\), and caret(^). On POSIX, that's
 *        single-quote('), double-quote("), and backslash(\).
 * @param platform optional platform type. Default is the current platform.
 * @param indexes optional IntArray to hold indexes of characters, token
 *        delimiters (ZERO_ALLOC_TOKENIZER_END_OF_TOKEN), and end of string
 *        delimiter (ZERO_ALLOC_TOKENIZER_END_OF_COMMAND).
 */
class TokenizedCommandLine(
    val commandLine: String,
    val raw: Boolean,
    val platform: Int = SdkConstants.currentPlatform(),
    private val indexes: IntArray = allocateTokenizeCommandLineBuffer(commandLine)) {
    private val generation = ++indexes[0]
    init {
        if (platform == PLATFORM_WINDOWS) {
            zeroAllocTokenizeWindows(raw)
        } else {
            zeroAllocTokenizePOSIX(raw)
        }
    }

    /**
     * Construct and return a list of tokens.
     */
    fun toTokenList() : List<String> {
        checkGeneration()
        val result = mutableListOf<String>()
        val token = StringBuilder()
        for(index in 1 until indexes.size) {
            when (val offset = indexes[index]) {
                ZERO_ALLOC_TOKENIZER_END_OF_COMMAND -> {
                    if (token.isNotEmpty()) {
                        result.add(token.toString())
                    }
                    return result
                }
                ZERO_ALLOC_TOKENIZER_END_OF_TOKEN -> if (token.isNotEmpty()) {
                    result.add(token.toString())
                    token.setLength(0)
                }
                else -> {
                    if (offset < 0)
                        throw IndexOutOfBoundsException("Negative index ($offset) seen tokenizing '$commandLine'")
                    if (offset >= commandLine.length)
                        throw IndexOutOfBoundsException("Index ($offset) out of range seen tokenizing '$commandLine'")
                    token.append(commandLine[offset])
                }
            }
        }
        throw Exception("Unreachable")
    }

    /**
     * Tokenize a string with Windows rules.
     *
     * This is the zero-alloc (per char) part of the tokenizer.
     *
     * This follows Windows tokenization tokenization rules:
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
     *
     * @param raw if true, then special characters double-quote("), backslash(\),
     *   and caret(^) are returned, otherwise they are not returned. Regardless
     *   of this flag, the Windows command line escaping logic is applied and
     *   unquoted whitespace is removed.
     * @return Same as [indexes] for the case that the caller didn't specify it.
     */
    private fun zeroAllocTokenizeWindows(raw: Boolean) : TokenizedCommandLine {
        checkGeneration()
        var quoting = false
        var i = 0
        val length = commandLine.length // Calculate length once
        var c: Char?
        var offset = 1 // One because first element is generation
        fun add(index: Int) {
            indexes[offset++] = index
        }

        while (i < length) {
            c = commandLine[i]
            // Quick path for normal case
            when {
                c == '"' -> {
                    // This is an opening or closing double-quote("). Whichever it is, move to the
                    // opposite quoting state for subsequent iterations. If this is raw mode then
                    // the double-quote(") will also be sent.
                    if (raw) add(i) // The double-quote(").
                    quoting = !quoting
                    ++i
                }
                c == '\\' -> {
                    // Count the number of backslash(\) and then check for double-quote(") following
                    // them.
                    var forward = i + 1
                    var slashCount = 1
                    // Move position to end of backslashes(\)
                    c = commandLine.getOrNull(forward)
                    while (c == '\\') {
                        ++slashCount
                        c = commandLine.getOrNull(++forward)
                    }
                    val odd = slashCount % 2 == 1
                    val quote = c == '\"' // Was there a double-quote(")?
                    // If double-quote("), halve the backslashes(\). If raw, then don't halve.
                    if (!raw && quote) {
                        slashCount /= 2
                    }
                    // Emit the right number of backslashes(\).
                    repeat(slashCount) { j ->
                        add(i + j)
                    }
                    // If odd backslashes(\) then treat a double-quote(") as literal
                    if (odd && quote) {
                        add(forward++)
                    }
                    i = forward
                }
                !quoting && c == '^' -> {
                    // If caret(^) is seen outside of quoting and the next character is a block of
                    // carriage-return(\r) and line-feed(\n) then remove the caret(^) and the
                    // line-feed(\n). If this is raw mode then the caret(^) is sent but no CR/LF
                    // characters.
                    c = commandLine.getOrNull(++i)
                    // If raw or next character was end of command then write caret(^)
                    if (raw || c == null) add(i - 1)
                    // caret(^) is escaped by caret(^)
                    if (c == '^') add(i++)
                    // Move past EOL characters.
                    while (c == '\r' || c == '\n') c = commandLine.getOrNull(++i)
                }
                !quoting && Character.isWhitespace(c) -> {
                    // Whitespace outside of quotes terminates the token. Send a special
                    // Int.MAX_VALUE to indicate the token is ended.
                    add(ZERO_ALLOC_TOKENIZER_END_OF_TOKEN)
                    c = commandLine.getOrNull(++i)
                    // Skip any additional whitespace that follows the initial whitespace
                    while (c != null && Character.isWhitespace(c)) c = commandLine.getOrNull(++i)
                }
                else -> add(i++)
            }
        }
        add(ZERO_ALLOC_TOKENIZER_END_OF_TOKEN)
        add(ZERO_ALLOC_TOKENIZER_END_OF_COMMAND)
        return this
    }

    /**
     * Tokenize a string with POSIX rules. This function should operate in the
     * same manner as the bash command-line.
     *
     * This is the zero-alloc (per char) part of the tokenizer.
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
     *
     * For escaped tokens, this can be validated with a script like this:
     *
     * echo 1=[/$1]
     *
     * echo 2=[/$2]
     *
     * echo 3=[/$3]
     *
     * @param raw if true, then special characters single-quote('), double-quote("), and
     *   backslash(\) are returned, otherwise they are not returned. Regardless of this flag,
     *   the POSIX command line escaping logic is applied.
     */
    private fun zeroAllocTokenizePOSIX(raw: Boolean) : TokenizedCommandLine {
        checkGeneration()
        var quoting = false
        var quote = '\u0000' // POSIX quote can be either " or '
        var escaping = false
        var skipping = true
        var i = 0
        var c: Char
        val length = commandLine.length
        var offset = 1 // One because first element is generation
        fun add(index: Int) {
            indexes[offset++] = index
        }
        while(i < length) {
            c = commandLine[i++]
            if (skipping) {
                skipping = if (Character.isWhitespace(c)) {
                    continue
                } else {
                    false
                }
            }
            if (quoting || !Character.isWhitespace(c)) {
                if (raw) {
                    add(i - 1)
                }
            }
            if (escaping) {
                escaping = false
                if (c != '\n') {
                    if (!raw) {
                        add(i - 1)
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
            if (!quoting && Character.isWhitespace(c)) {
                skipping = true
                add(ZERO_ALLOC_TOKENIZER_END_OF_TOKEN)
                continue
            }
            if (!raw) {
                add(i - 1)
            }
        }
        add(ZERO_ALLOC_TOKENIZER_END_OF_TOKEN) // End of token
        add(ZERO_ALLOC_TOKENIZER_END_OF_COMMAND) // End of command-line
        return this
    }

    private fun checkGeneration() {
        if (generation != indexes[0]) {
            throw Exception("Buffer indexes was shared with another " +
                    "TokenizedCommandLine after this one")
        }
    }
}


/**
 * Allocate a buffer large enough to hold [commandLine] indexes.
 *
 * The +3 is derived as:
 *   one element to hold the generation counter
 *   + one element for each character in the commandLine
 *   + one element to hold the last ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
 *   + one element to hold final ZERO_ALLOC_TOKENIZER_END_OF_COMMAND
 *
 * This relies on the fact that Windows and POSIX parsing can only reduce the
 * number of characters and not increase it. Reduction comes, for example,
 * from combining contiguous blocks of whitespace with a single
 * [ZERO_ALLOC_TOKENIZER_END_OF_TOKEN].
 */
fun allocateTokenizeCommandLineBuffer(commandLine: String) =
    IntArray(commandLine.length + 3)

