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

package com.android.build.api

import com.android.Version
import com.android.build.api.dsl.Lint
import com.android.testutils.ApiTester
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import org.junit.Test

class DeprecatedApiTest {

    @Test
    fun `deprecated and removed APIs need to be announced publicly`() {
        getApiTester().checkApiElements()
    }

    companion object {
        private val snapshotFileUrl =
            Resources.getResource(DeprecatedApiTest::class.java, "deprecated-api.txt")
        private val currentAgpVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION.removeSuffix("-dev")

        private fun transformFinalFileContent(currentSnapshotContent: List<String>):
                Collection<String> {
            val expectedSnapshotContent = Splitter.on("\n")
                .omitEmptyStrings()
                .splitToList(Resources.toString(snapshotFileUrl, Charsets.UTF_8))

            val knownDeprecatedApis = mutableMapOf<String, String>()
            var agpVersion: String? = null
            expectedSnapshotContent.subList(5, expectedSnapshotContent.size).forEach {
                if (it.startsWith("Deprecated from AGP ")) {
                    agpVersion = it.removePrefix("Deprecated from AGP ")
                } else {
                    knownDeprecatedApis[it.removePrefix("  * ")] = agpVersion!!
                }
            }

            val currentDeprecatedApis = mutableMapOf<String, MutableList<String>>()
            currentSnapshotContent.subList(5, currentSnapshotContent.size).forEach { api ->
                currentDeprecatedApis.getOrPut(
                    knownDeprecatedApis[api] ?: currentAgpVersion
                ) { mutableListOf() }.add(api)
            }

            val deprecatedApiList = mutableListOf<String>()
            currentDeprecatedApis.keys.forEach { version ->
                deprecatedApiList.add("Deprecated from AGP $version")
                currentDeprecatedApis[version]!!.sorted().forEach { apiSignature ->
                    deprecatedApiList.add("  * $apiSignature")
                }
            }

            return currentSnapshotContent.subList(0, 3) +
                    listOf("are announced on go/as-release-notes.", currentSnapshotContent[4]) +
                    deprecatedApiList
        }

        internal fun getApiTester(): ApiTester {
            val classes = ClassPath.from(
                Lint::class.java.classLoader
            ).getTopLevelClassesRecursive("com.android.build.api")
                .filter(::filterNonApiClasses)
            return ApiTester(
                "Deprecated Android Gradle Plugin API.",
                classes,
                ApiTester.Filter.DEPRECATED_ONLY,
                """
                The deprecated API has changed, if you're removing a previously deprecated API or
                deprecating an API, make sure that you announce these updates on http://go/as-release-notes
                to be added on https://developer.android.com/studio/preview/features#android_gradle_plugin_api_updates,
                then run DeprecatedApiUpdater.main[] from the IDE to update the API file.

                DeprecatedApiUpdater will apply the following changes if run:

                """.trimIndent(),
                snapshotFileUrl,
                this::transformFinalFileContent,
                ApiTester.Flag.OMIT_HASH
            )
        }
    }
}
