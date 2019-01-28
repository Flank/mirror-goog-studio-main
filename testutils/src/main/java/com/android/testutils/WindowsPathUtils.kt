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
package com.android.testutils

import com.android.SdkConstants
import java.io.File

/**
 * Use this function to get a shorter version of a path on Windows. Converts a file path to
 * Windows 8.3 filename. This is a file name that looks like C:\abc~2\xyz~1.txt where each path
 * segment doesn't exceed eight characters in the base name and three characters in the file
 * extension.
 * - Returns original file on non-Windows
 * - File must exist
 * - This function will also convert relative paths to absolute (as a side-effect of the batch
 *   command that gets executed.
 */
fun getWindowsShortNameFile(file: File): File {
    if (SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS) {
        return file
    }
    val process = Runtime.getRuntime().exec("cmd /c for %I in (\"$file\") do @echo %~fsI")
    process.waitFor()
    val data = ByteArray(65536)
    val size = process.inputStream.read(data)

    if (size <= 0) {
        throw RuntimeException("No windows short path returned for $file")
    }
    val result = String(data, 0, size)
        .replace("\r", "")
        .replace("\n", "")

    return File(result)
}