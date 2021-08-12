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

package com.android.tools.appinspection.common

val APPINSPECTION_PACKAGE_PREFIX = "com.android.tools.appinspection"

/**
 * Obtains the current stack trace ignoring the first [offset] lines.
 *
 * If [packagePrefix] is specified, starting from the top all lines containing
 * the prefix will be ignored until encountering a line that does not contain it.
 * Then [offset] logic is applied.
 */
fun getStackTrace(offset: Int, packagePrefix: String? = APPINSPECTION_PACKAGE_PREFIX): String {
    return Throwable().stackTrace.let { stacks ->
        val trimmed = packagePrefix?.let { prefix ->
            stacks.dropWhile { element -> element.className.startsWith(prefix) }
        } ?: stacks.toList()
        trimmed.drop(offset).fold("") { acc, stackTraceElement -> "$acc$stackTraceElement\n" }
    }
}
