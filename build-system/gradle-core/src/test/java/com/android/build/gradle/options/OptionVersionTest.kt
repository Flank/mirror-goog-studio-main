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

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.ide.common.repository.GradleVersion
import org.junit.Test

/** Tests the validity of the Android Gradle plugin versions associated with the [Option]s. */
class OptionVersionTest {

    companion object {

        /**
         * The AGP stable version that is going to be published (ignoring dot releases for the
         * purpose of this test).
         */
        private val AGP_STABLE_VERSION: GradleVersion = getStableVersionIgnoringDotReleases(ANDROID_GRADLE_PLUGIN_VERSION)

        /** Deprecated [Option]s that have invalid deprecation versions and need to be fixed. */
        @Suppress("DEPRECATION")
        private val KNOWN_VIOLATING_DEPRECATION_OPTIONS: Set<Option<*>> = setOf(
                BooleanOption.USE_NEW_LINT_MODEL, // TODO(b/160392650): This will be removed in 7.0 as the previous integration is incompatible with the configuration cache.
                BooleanOption.JETIFIER_SKIP_IF_POSSIBLE,
                BooleanOption.ENABLE_INCREMENTAL_DEXING_TASK_V2,
                BooleanOption.ENABLE_INCREMENTAL_DEXING_TRANSFORM,
                BooleanOption.ENABLE_JVM_RESOURCE_COMPILER,
                BooleanOption.ENABLE_SYMBOL_TABLE_CACHING,
                BooleanOption.ENABLE_RESOURCE_OPTIMIZATIONS,
                BooleanOption.PREFER_CMAKE_FILE_API,
                BooleanOption.ENABLE_NATIVE_CONFIGURATION_FOLDING,
                OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8,
                StringOption.JETIFIER_BLACKLIST,
                StringOption.BUILD_CACHE_DIR
        )

        private fun getStableVersionIgnoringDotReleases(versionString: String): GradleVersion {
            // Normalize the version string first (e.g., "7.0" => "7.0.0")
            val normalizedVersionString = if (versionString.count { it=='.' }==1) {
                "$versionString.0"
            } else {
                versionString
            }
            val gradleVersion = GradleVersion.parseAndroidGradlePluginVersion(normalizedVersionString)
            return GradleVersion(gradleVersion.major, gradleVersion.minor, 0)
        }
    }

    @Test
    fun `check deprecated options have deprecation versions in the future`() {
        val violatingDeprecatedOptions = getAllOptions()
                .filter { it.status is Option.Status.Deprecated }
                .filter {
                    val deprecationVersion = getStableVersionIgnoringDotReleases(
                            (it.status as Option.Status.Deprecated).deprecationTarget.removalTarget.versionString!!)
                    deprecationVersion <= AGP_STABLE_VERSION
                }

        val notYetKnownViolations = violatingDeprecatedOptions - KNOWN_VIOLATING_DEPRECATION_OPTIONS
        assert(notYetKnownViolations.isEmpty()) {
            "Deprecated options must have deprecation versions in the future." +
                    " The following options do not meet that requirement:\n" +
                    "```\n" +
                    violatingDeprecatedOptions.joinToString("") { "${it.javaClass.simpleName}.${it},\n" } +
                    "```\n" +
                    "Please either fix them now or copy the above code snippet to" +
                    " `OptionVersionTest.KNOWN_VIOLATING_DEPRECATION_OPTIONS` to fix them later."
        }

        val fixedViolations = KNOWN_VIOLATING_DEPRECATION_OPTIONS - violatingDeprecatedOptions
        assert(fixedViolations.isEmpty()) {
            "The following options no longer violate the requirement checked by this test:\n" +
                    "```\n" +
                    fixedViolations.joinToString("") { "${it.javaClass.simpleName}.${it},\n" } +
                    "```\n" +
                    "Please remove them from `OptionVersionTest.KNOWN_VIOLATING_DEPRECATION_OPTIONS`."
        }
    }

    @Test
    fun `check removed options do not have removed versions in the future`() {
        val violatingRemovedOptions = getAllOptions()
                .filter { it.status is Option.Status.Removed }
                .filter { option ->
                    val removedVersion = (option.status as Option.Status.Removed).removedVersion.versionString?.let {
                        getStableVersionIgnoringDotReleases(it)
                    }
                    removedVersion?.let { removedVersion > AGP_STABLE_VERSION } ?: false
                }

        assert(violatingRemovedOptions.isEmpty()) {
            "Removed options must not have removed versions in the future:\n" +
                    "```\n" +
                    violatingRemovedOptions.joinToString("") { "${it.javaClass.simpleName}.${it},\n" } +
                    "```\n"
        }
    }

    private fun getAllOptions(): List<Option<Any>> =
            (BooleanOption.values().toList() as List<Option<Boolean>>) +
                    OptionalBooleanOption.values() +
                    StringOption.values() +
                    IntegerOption.values()
}

