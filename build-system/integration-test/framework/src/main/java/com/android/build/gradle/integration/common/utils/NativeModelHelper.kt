/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils

import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.google.common.collect.Maps
import com.google.common.io.Files
import java.io.File

/**
 * Return a map of C++ flags for each NativeFile.
 *
 * The key is the filePath of a NativeFile. The value is the list of flags.
 */
private fun getCppFlags(
    project: NativeAndroidProject, artifact: NativeArtifact
): Map<File, List<String>> {
    return getFlags(project, artifact)
}

private fun getFlags(
    project: NativeAndroidProject,
    artifact: NativeArtifact): Map<File, List<String>> {
    val settingsMap = Maps.newHashMap<File, String>()

    // Get extensions for the language.
    val extensions = project.fileExtensions.entries
        .filter { entry -> entry.value == "c++" }
        .map { it.key }

    for (nativeFile in artifact.sourceFiles) {
        if (extensions.contains(Files.getFileExtension(nativeFile.filePath.name))) {
            val setting = nativeFile.settingsName
            settingsMap[nativeFile.filePath] = setting
        }
    }

    val flagsMap = Maps.newHashMap<File, List<String>>()
    for ((key, value) in settingsMap) {
        flagsMap[key] = findFlags(project, value)
    }
    return flagsMap
}

/**
 * Return the C++ flags for all NativeFile. Flags in all NativeFile in the NativeArtifact is
 * flatten into a single list.
 */
fun getFlatCppFlags(
    project: NativeAndroidProject, artifact: NativeArtifact
): List<String> {
    return getCppFlags(project, artifact).values.flatten()
}

private fun findFlags(
    project: NativeAndroidProject,
    settingName: String
): List<String> {
    val settings = project.settings
    val setting = settings.toList().first { s -> s.name == settingName }
    return setting.compilerFlags
}

/**
 * Get all build output files.
 */
fun NativeAndroidProject.buildOutputFiles() : List<File> {
    return artifacts.toList().map { artifact -> artifact.outputFile }
}
