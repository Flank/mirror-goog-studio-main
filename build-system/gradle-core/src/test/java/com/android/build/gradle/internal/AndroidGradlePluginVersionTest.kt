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

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

/** Test cases for [AndroidGradlePluginVersion]. */
class AndroidGradlePluginVersionTest {

    @Test
    fun testIsPluginVersion() {
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-alpha01")).isTrue()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-alpha02")).isTrue()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-beta01")).isTrue()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-beta02")).isTrue()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-dev")).isTrue()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0")).isTrue()

        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.a")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0.0")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-01")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0.alpha01")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-gamma01")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-alpha01-01")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0.dev")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0.dev01")).isFalse()
        assertThat(AndroidGradlePluginVersion.isPluginVersion("3.1.0-dev-01")).isFalse()
    }

    @Test
    fun testParsePluginVersion() {
        var pluginVersion = AndroidGradlePluginVersion.parseString("3.1.0-alpha01")
        assertThat(versionToString(pluginVersion)).isEqualTo("3.1.0-alpha1")

        pluginVersion = AndroidGradlePluginVersion.parseString("3.1.0-alpha02")
        assertThat(versionToString(pluginVersion)).isEqualTo("3.1.0-alpha2")

        pluginVersion = AndroidGradlePluginVersion.parseString("3.1.0-beta01")
        assertThat(versionToString(pluginVersion)).isEqualTo("3.1.0-beta1")

        pluginVersion = AndroidGradlePluginVersion.parseString("3.1.0-beta02")
        assertThat(versionToString(pluginVersion)).isEqualTo("3.1.0-beta2")

        pluginVersion = AndroidGradlePluginVersion.parseString("3.1.0-dev")
        assertThat(versionToString(pluginVersion)).isEqualTo("3.1.0-dev")

        pluginVersion = AndroidGradlePluginVersion.parseString("3.1.0")
        assertThat(versionToString(pluginVersion)).isEqualTo("3.1.0")

        try {
            AndroidGradlePluginVersion.parseString("3.1")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e).hasMessage("3.1 is not a valid Android Gradle plugin version")
        }
    }

    @Test
    fun testComparePluginVersions() {
        assertThat(stringToVersion("3.1.0-alpha01").compareTo(stringToVersion("3.1.0-alpha01")))
                .isEqualTo(0)
        assertThat(stringToVersion("3.1.0-alpha01").compareTo(stringToVersion("3.1.0-alpha02")))
                .isLessThan(0)
        assertThat(stringToVersion("3.1.0-alpha02").compareTo(stringToVersion("3.1.0-beta01")))
                .isLessThan(0)
        assertThat(stringToVersion("3.1.0-beta01").compareTo(stringToVersion("3.1.0-beta02")))
                .isLessThan(0)
        assertThat(stringToVersion("3.1.0-beta02").compareTo(stringToVersion("3.1.0-dev")))
                .isLessThan(0)
        assertThat(stringToVersion("3.1.0-dev").compareTo(stringToVersion("3.1.0")))
                .isLessThan(0)
        assertThat(stringToVersion("3.1.0").compareTo(stringToVersion("3.1.0")))
                .isEqualTo(0)
        assertThat(stringToVersion("3.1.0").compareTo(stringToVersion("3.1.0-alpha01")))
                .isGreaterThan(0)
    }

    private fun stringToVersion(versionString: String) =
            AndroidGradlePluginVersion.parseString(versionString)

    private fun versionToString(version: AndroidGradlePluginVersion): String {
        when (version.previewType) {
            1 -> return String.format(
                    "%d.%d.%d-alpha%d",
                    version.majorVersion,
                    version.minorVersion,
                    version.microVersion,
                    version.previewVersion)
            2 -> return String.format(
                    "%d.%d.%d-beta%d",
                    version.majorVersion,
                    version.minorVersion,
                    version.microVersion,
                    version.previewVersion)
            3 -> return String.format(
                    "%d.%d.%d-dev",
                    version.majorVersion,
                    version.minorVersion,
                    version.microVersion)
            4 -> return String.format(
                    "%d.%d.%d",
                    version.majorVersion,
                    version.minorVersion,
                    version.microVersion)
            else -> throw AssertionError("Unknown preview type: " + version.previewType)
        }
    }
}