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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.TAG_USES_SDK
import com.android.sdklib.SdkVersionInfo.getApiByBuildCode
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener.EventType
import com.android.utils.XmlUtils
import com.google.common.base.Charsets
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

internal class PartialTestMode : TestMode(
    description = "Automatic Partial Analysis and Merging",
    "TestMode.PARTIAL"
) {
    private class State(
        val manifestFile: File?,
        val originalManifest: String?,
        val overrides: Configuration?
    )

    override val folderName: String = "partial"

    override fun applies(context: TestModeContext): Boolean {
        return context.task.incrementalFileName == null
    }

    override fun before(context: TestModeContext): Any {
        val task = context.task
        val projectFolders = context.projectFolders
        val projects = task.projects

        var manifest: File? = null
        var contents: String? = null
        val overrides = task.runner.createClient().configurations.overrides

        if (projects.size == 1) {
            // restored by event listener between the analysis and merging phases.
            // In theory we should push the minSdkVersion *and* the targetSdkVersion
            // all the way down to 1, but that would flag a lot of old lint checks
            // for specific issues linked to really old API levels; at this point,
            // new versions of lint will be running on modern projects where 15
            // is a low bar for minSdkVersion and Play Store policies around
            // keeping targetSdkVersion up to date (see
            // https://developer.android.com/distribute/play-policies and GradleDetector).
            manifest = findManifest(projectFolders.first())
            manifest?.let {
                contents = replaceManifestVersions(it, 1, 28)
            }
        }

        return State(manifest, contents, overrides)
    }

    override val eventListener: ((TestModeContext, EventType, Any?) -> Unit) =
        { context, type, s ->
            // Before merging the report, restore the manifest.
            // This is done as a merge hook rather than using the after hook,
            // because we want to insert ourselves in the middle of the
            // lint check
            @Suppress("UNCHECKED_CAST")
            if (type == EventType.MERGING) {
                val state = s as State
                if (state.manifestFile != null && state.originalManifest != null) {
                    // Restore manifest
                    state.manifestFile.writeText(state.originalManifest, Charsets.UTF_8)
                }
                // Make sure none of the source code is visible at merging time.
                // In fact maybe we should remove the binaries too, and perhaps
                // even everything (right now it might need lint.xml files and
                // project metadata files.)
                deleteCompiledSources(context.projects, context, deleteSourceFiles = true)
            } else if (type == EventType.SCANNING_PROJECT ||
                type == EventType.SCANNING_LIBRARY_PROJECT
            ) {
                // Don't use global configurations when analyzing in merge only
                if (context.task.overrideConfigFile != null &&
                    context.driver?.mode == LintDriver.DriverMode.ANALYSIS_ONLY
                ) {
                    context.driver.client.configurations.overrides = null
                }

                val lintContext = context.lintContext
                if (lintContext != null) {
                    // Figure out what the local project dependencies of the to-be-analyzed project
                    // are, and then delete any source files there. NOT for the current project.
                    val projects = mutableSetOf<ProjectDescription>()

                    for (dependency in lintContext.project.allLibraries.filter { !it.isExternalLibrary }) {
                        val dir = dependency.dir

                        val task = context.task
                        val description = try {
                            task.dirToProjectDescription[dir.canonicalFile]
                        } catch (ignore: IOException) {
                            task.dirToProjectDescription[dir]
                        } ?: continue
                        projects.add(description)
                    }

                    deleteCompiledSources(projects, context)
                }
            }
        }

    override val diffExplanation: String =
        """
        Lint results computed provisionally do
        not match those computed without provisional support enabled. This
        means that the detector is not handling provisional support correctly,
        which means that it will not work correctly as part of incremental
        Gradle builds, where projects are now analyzed separately and the
        results merged to generate the report.

        Alternatively, if this difference is expected, you can set the
        `testModes(...)` to include only one of these two, or turn off
        the equality check altogether via `.expectIdenticalTestModeOutput(false)`.
        You can then check each output by passing in a `testMode` parameter
        to `expect`(...).
        """.trimIndent()

    /**
     * Replaces the minSdkVersion in the given manifest with [minSdk]
     * (unless it's -1) and the targetSdkVersion with [targetSdk]
     * (unless it's -1). The targetSdkVersion will also be limited to be
     * at most the current targetSdkVersion, and at least [minSdk].
     */
    @Suppress("SameParameterValue")
    private fun replaceManifestVersions(
        manifestFile: File,
        minSdk: Int,
        targetSdk: Int
    ): String? {

        val originalManifest = manifestFile.readText()

        var newMinSdk = minSdk
        var newTargetSdk = targetSdk
        var currentMin = 1
        var currentTarget = 26
        val doc = XmlUtils.parseDocumentSilently(originalManifest, true)
        if (doc != null) {
            val usesSdks = doc.getElementsByTagName(TAG_USES_SDK)
            if (usesSdks.length != 1) {
                // We can't mess with the manifest if you have multiple <uses-sdk>
                // elements; this isn't valid in Android, and is used in tests as a
                // shorthand for testing many different values (typically for manifest
                // validation) where we know it won't matter to lint so it's easier
                // than writing multiple tests, one for each value
                return null
            }
            val e = usesSdks.item(0) as Element
            val targetSdkVersion = e.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            val minSdkVersion = e.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            try {
                currentMin = minSdkVersion.toIntOrNull() ?: 1
                currentTarget = targetSdkVersion.toIntOrNull()
                    ?: getApiByBuildCode(targetSdkVersion, false)
                        .let { if (it == -1) currentMin else it }
                // Coerce min to not be higher than current min,
                // and coerce target to fall between new min and current target
                newMinSdk = min(newMinSdk, currentMin)
                newTargetSdk = max(newMinSdk, min(currentTarget, newTargetSdk))
            } catch (e: NumberFormatException) {
                return null
            }
        } else {
            // We don't dare to change the targetSdkVersion since we don't know
            // the current targetSdkVersion, and we should not exceed it
            newTargetSdk = -1
        }

        // Replace the original manifest with our own with a lower number
        // for the lint check
        val minAttribute = ":minSdkVersion=\""
        val targetAttribute = ":targetSdkVersion=\""
        var minStart = originalManifest.indexOf(minAttribute)
        if (minStart == -1) {
            newMinSdk = -1
        }
        var newManifest = originalManifest
        minStart += minAttribute.length
        val minEnd = originalManifest.indexOf('"', minStart)
        if (newMinSdk != -1 && minEnd != -1) {
            // If the current API level >= 10 it's 2 digits whereas our replacement is
            // normally 1 digit; this discrepancy can make unit tests which encode
            // the exact error range be off by one character.
            val padding = if (currentMin >= 10 && newMinSdk < 10) "0" else ""
            val prefix = newManifest.substring(0, minStart)
            val postfix = newManifest.substring(minEnd)
            newManifest = "$prefix$padding$newMinSdk$postfix"
        }

        var targetStart = newManifest.indexOf(targetAttribute)
        targetStart += if (targetStart != -1) targetAttribute.length else 0
        val targetEnd = newManifest.indexOf('"', targetStart)
        if (newTargetSdk != -1 && targetStart != -1 && targetEnd > targetStart &&
            newTargetSdk < currentTarget
        ) {
            // If the old API level >= 10 it's 2 digits whereas our replacement is
            // normally 1 digit; this discrepancy can make unit tests which encode
            // the exact error range be off by one character.
            val padding = if (currentTarget >= 10 && newTargetSdk < 10) "0" else ""
            val prefix = newManifest.substring(0, targetStart)
            val postfix = newManifest.substring(targetEnd)
            newManifest = "$prefix$padding$newTargetSdk$postfix"
        }

        if (newManifest != originalManifest) {
            // TODO: What other safe mutations can I do which will result
            // in additional recorded potential warnings, but won't generate
            // new errors that shouldn't be there?
            // (For example, maybe I can change the manifest to be missing
            // some key things that could be present down stream, such as
            // permissions? intent flags? What could we do here?
            manifestFile.writeText(newManifest)
            return originalManifest
        }
        return null
    }

    private fun findManifest(projectDir: File): File? {
        var manifestFile = File(projectDir, ANDROID_MANIFEST_XML)
        if (!manifestFile.exists()) {
            // Gradle test project? If so the test sources are moved over to src/main/
            manifestFile = File(projectDir, "src/main/$ANDROID_MANIFEST_XML")
        }
        if (!manifestFile.exists()) {
            return null
        }
        return manifestFile
    }
}
