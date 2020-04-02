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

package com.android.tools.transport.device

/**
 * Simple wrapper class that represents an Android SDK level plus misc. useful utilities
 */
class SdkLevel(private val value: Int): Comparable<SdkLevel> {
    companion object {
        @JvmField
        val N = SdkLevel(24)

        @JvmField
        val O = SdkLevel(26)

        @JvmField
        val P = SdkLevel(28)

        @JvmField
        val Q = SdkLevel(29)
    }

    override fun toString(): String {
        return value.toString()
    }

    override fun compareTo(other: SdkLevel): Int {
        return value.compareTo(other.value)
    }
}

fun SdkLevel.supportsJvmti() = this >= SdkLevel.O