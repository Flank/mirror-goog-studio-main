/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.cli

/**
 * Simple wrapper class around command line arguments array
 */
internal class Arguments(private val args: Array<String>) {
    private var index = 0

    fun size() : Int {
        return args.size - index
    }

    /**
     * Returns the current argument, or the empty string if end of argument list has been reached
     */
    fun peek() : String {
        if (!hasMore()) {
            return ""
        } else {
            return args[index]
        }
    }

    /**
     * Returns all arguments where i >= index
     */
    fun remaining() : Arguments {
        return Arguments(args.copyOfRange(index, args.size))
    }

    /**
     * Move cursor to the next argument. If end of list has been reached, [next] returns an empty string.
     */
    fun next() : String {
        if (!hasMore()) {
            return ""
        } else {
            return args[index++]
        }
    }

    fun consumeAll() : Array<String> {
        val array = args.copyOfRange(index, args.size)
        index = args.size
        return array
    }

    fun hasMore() : Boolean {
        return index < args.size
    }

    fun nextIsFlag() : Boolean {
        return peek().startsWith("-")
    }
}
