/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.google.common.base.Preconditions.checkState
import java.util.regex.Pattern

/**
 * Class that represents an Android Gradle plugin version.
 */
class AndroidGradlePluginVersion(
        val majorVersion: Int,
        val minorVersion: Int,
        val microVersion: Int,
        val previewType: Int, // 1 for alpha, 2 for beta, 3 for dev, 4 for stable
        val previewVersion: Int) : Comparable<AndroidGradlePluginVersion> {

    override fun compareTo(other: AndroidGradlePluginVersion): Int {
        if (this.majorVersion != other.majorVersion) {
            return this.majorVersion - other.majorVersion
        }

        if (this.minorVersion != other.minorVersion) {
            return this.minorVersion - other.minorVersion
        }

        if (this.microVersion != other.microVersion) {
            return this.microVersion - other.microVersion
        }

        if (this.previewType != other.previewType) {
            return this.previewType - other.previewType
        }

        return this.previewVersion - other.previewVersion
    }

    companion object {

        /**
         * Pattern of an Android Gradle plugin version, formatted as
         * majorVersion.minorVersion.microVersion-previewType.previewVersion, in which some parts
         * may be optional.
         */
        private val PATTERN =
                Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)(-(((alpha|beta)([0-9]+))|dev))?")

        /**
         * Returns `true` if the given string represents a valid Android Gradle plugin version.
         */
        @JvmStatic
        fun isPluginVersion(versionString: String): Boolean {
            return PATTERN.matcher(versionString).matches()
        }

        /**
         * Parses the given string into an instance of [AndroidGradlePluginVersion].
         *
         * @return the parsed instance
         * @throws IllegalArgumentException if the given string is not a valid plugin version
         */
        @JvmStatic
        fun parseString(versionString: String): AndroidGradlePluginVersion {
            val matcher = PATTERN.matcher(versionString)
            if (!matcher.matches()) {
                throw IllegalArgumentException(
                        versionString + " is not a valid Android Gradle plugin version")
            }

            val majorVersion = Integer.parseInt(matcher.group(1))
            val minorVersion = Integer.parseInt(matcher.group(2))
            val microVersion = Integer.parseInt(matcher.group(3))

            val previewType: Int
            val previewVersion: Int
            when {
                matcher.group(4) == null -> {
                    previewType = 4 // Stable version
                    previewVersion = 0
                }
                matcher.group(5) == "dev" -> {
                    previewType = 3 // Dev version
                    previewVersion = 0
                }
                else -> {
                    val previewTypeString = matcher.group(7)
                    previewType = if (previewTypeString == "alpha") {
                        1 // Alpha version
                    } else {
                        checkState(previewTypeString == "beta")
                        2 // Beta version
                    }
                    previewVersion = Integer.parseInt(matcher.group(8))
                }
            }

            return AndroidGradlePluginVersion(
                    majorVersion,
                    minorVersion,
                    microVersion,
                    previewType,
                    previewVersion)
        }
    }
}
