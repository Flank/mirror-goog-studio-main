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

package com.android.build.gradle.internal.cxx.os

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.utils.StringHelperPOSIX
import com.android.utils.StringHelperWindows

/**
 * File extension to use for executable files for the current host OS.
 * Either ".exe" or "".
 */
val exe get() = os.exe

/**
 * Behavior of the current host platform.
 */
private val os : OsBehavior = createOsBehavior()

/**
 * Encapsulates file systen and shell differences between Windows, MacOS, and Posix.
 */
interface OsBehavior {
    val platform : Int

    /**
     * File extension to use for executable files.
     */
    val exe : String

    /**
     * Split multiple commands separated by && (or other host-specific separator).
     */
    fun splitCommandLine(commandLine: String) : List<String>

    /**
     * Convert a single command into a List<String> following host-specific rules. Does not
     * convert any escaped characters to their evaluated equivalent.
     */
    fun tokenizeCommandLineToRaw(command: String) : List<String>

    /**
     * Convert a single command into a List<String> following host-specific rules. Convert any
     * escaped characters to their evaluated equivalent.
     */
    fun tokenizeCommandLineToEscaped(command: String) : List<String>
}

/**
 * Create a host behavior object that encapsulates differences between Windows, MacOs, and Posix.
 */
fun createOsBehavior(platform : Int) : OsBehavior {
    return if (platform == PLATFORM_WINDOWS) object : OsBehavior {
        override val platform: Int get() = platform
        override val exe = ".exe"
        override fun splitCommandLine(commandLine: String) = StringHelperWindows.splitCommandLine(commandLine)
        override fun tokenizeCommandLineToRaw(command: String) = StringHelperWindows.tokenizeCommandLineToRaw(command)
        override fun tokenizeCommandLineToEscaped(command: String) = StringHelperWindows.tokenizeCommandLineToEscaped(command)
    }
    else object : OsBehavior {
        override val platform: Int get() = platform
        override val exe = ""
        override fun splitCommandLine(commandLine: String) = StringHelperPOSIX.splitCommandLine(commandLine)
        override fun tokenizeCommandLineToRaw(command: String) = StringHelperPOSIX.tokenizeCommandLineToRaw(command)
        override fun tokenizeCommandLineToEscaped(command: String) = StringHelperPOSIX.tokenizeCommandLineToEscaped(command)
    }
}

fun createOsBehavior() = createOsBehavior(CURRENT_PLATFORM)
fun createWindowsBehavior() = createOsBehavior(PLATFORM_WINDOWS)
fun createLinuxBehavior() = createOsBehavior(PLATFORM_LINUX)



