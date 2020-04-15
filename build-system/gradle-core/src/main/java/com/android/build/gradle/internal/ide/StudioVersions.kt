/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("StudioVersions")

package com.android.build.gradle.internal.ide

import com.android.build.gradle.options.BooleanOption
import com.google.common.annotations.VisibleForTesting
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.Version
import com.google.common.base.Splitter
import org.gradle.api.InvalidUserDataException

/** Throws if the Intellij Android Support plugin version used has a lower major/minor version than the current Android Gradle plugin */
fun verifyIDEIsNotOld(projectOptions: ProjectOptions) {
    if (!projectOptions[BooleanOption.ENABLE_STUDIO_VERSION_CHECK]) {
        return
    }
    verifyIDEIsNotOld(
        projectOptions.get(StringOption.IDE_ANDROID_STUDIO_VERSION),
        ANDROID_GRADLE_PLUGIN_VERSION
    )
}

@VisibleForTesting
internal fun verifyIDEIsNotOld(
    injectedVersion: String?,
    androidGradlePluginVersion: MajorMinorVersion
) {
    if (injectedVersion == null) {
        // Be lenient when the version is not injected.
        return
    }

    val parsedInjected = parseVersion(injectedVersion)
        ?: throw InvalidUserDataException("Invalid injected android support version '$injectedVersion', expected to be of the form 'w.x.y.z'")

    if (parsedInjected < androidGradlePluginVersion) {
        throw RuntimeException(
            "This version of the Android Support plugin for IntelliJ IDEA (or Android Studio) cannot open this project, please retry with version $androidGradlePluginVersion or newer."
        )
    }
}

/** This will accept some things that are not valid versions as it ignores everything after the
 * second. */
@VisibleForTesting
internal fun parseVersion(version: String): MajorMinorVersion? {
    val segments = SPLITTER.split(version).iterator()
    if (!segments.hasNext()) {
        return null
    }
    val majorVersion = segments.next().toIntOrNull() ?: return null
    if (!segments.hasNext()) {
        return null
    }
    val minorVersion = segments.next().toIntOrNull() ?: return null
    if (majorVersion < 0 || minorVersion < 0) {
        return null
    }
    return MajorMinorVersion(majorVersion, minorVersion)
}

@VisibleForTesting
internal data class MajorMinorVersion(
    val majorVersion: Int,
    val minorVersion: Int
) : Comparable<MajorMinorVersion> {
    override fun compareTo(other: MajorMinorVersion): Int {
        val diff = this.majorVersion - other.majorVersion
        if (diff != 0) {
            return diff
        }
        return minorVersion - other.minorVersion
    }

    override fun toString(): String {
        return "$majorVersion.$minorVersion"
    }
}

private val SPLITTER = Splitter.on('.')

private val ANDROID_GRADLE_PLUGIN_VERSION =
    parseVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)!!