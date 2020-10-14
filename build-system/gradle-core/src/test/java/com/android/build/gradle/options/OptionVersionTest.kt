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

package com.android.build.gradle.options

import com.android.ide.common.repository.GradleVersion
import org.junit.Test

/** Tests the validity of the Android Gradle plugin versions associated with the [Option]s. */
class OptionVersionTest {

    companion object {

        /**
         * This constant should be the same as [com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION].
         *
         * The reason we don't use [com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION] directly is
         * to make upgrading [com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION] easier without
         * having to fix any AGP issues detected by this test during an upgrade.
         *
         * Instead, we will upgrade this constant separately, ideally shortly after upgrading
         * [com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION] (tracked by bug 162495697).
         */
        private val ANDROID_GRADLE_PLUGIN_VERSION =
            GradleVersion.parseAndroidGradlePluginVersion("4.2.0")
    }

    @Test
    fun `check deprecated options have deprecation targets in the future`() {
        val deprecatedOptions = getAllOptions().filter { it.status is Option.Status.Deprecated }
        for (option in deprecatedOptions) {
            val status = option.status as Option.Status.Deprecated
            status.deprecationTarget.removalTarget.getVersion()?.let { removalTargetVersion ->
                assert(removalTargetVersion > ANDROID_GRADLE_PLUGIN_VERSION) {
                    "Deprecated option ${option.propertyName} does not have deprecation target in the future: $removalTargetVersion"
                }
            }
        }
    }

    @Test
    fun `check removed options do not have removed versions in the future`() {
        val removedOptions = getAllOptions().filter { it.status is Option.Status.Removed }
        for (option in removedOptions) {
            val status = option.status as Option.Status.Removed
            status.removedVersion.getVersion()?.let { removedVersion ->
                assert(removedVersion <= ANDROID_GRADLE_PLUGIN_VERSION) {
                    "Removed option ${option.propertyName} has removed version in the future: $removedVersion"
                }
            }
        }
    }

    private fun getAllOptions(): List<Option<Any>> =
        (BooleanOption.values().toList() as List<Option<Boolean>>) +
                OptionalBooleanOption.values() +
                StringOption.values() +
                IntegerOption.values()

    private fun Version.getVersion(): GradleVersion? {
        return versionString?.let { versionString ->
            // Normalize the version string (e.g., "7.0" => "7.0.0")
            val normalizedVersionString = if (versionString.count { it == '.' } == 1) {
                "$versionString.0"
            } else {
                versionString
            }
            GradleVersion.parseAndroidGradlePluginVersion(normalizedVersionString)
        }
    }
}

