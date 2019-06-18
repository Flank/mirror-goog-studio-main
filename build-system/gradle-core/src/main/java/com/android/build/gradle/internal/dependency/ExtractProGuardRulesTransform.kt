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

package com.android.build.gradle.internal.dependency

import com.android.builder.dexing.isProguardRule
import com.android.builder.dexing.isToolsConfigurationFile
import com.android.utils.FileUtils
import com.android.utils.FileUtils.mkdirs
import com.google.common.io.ByteStreams
import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

fun isProguardRule(entry: ZipEntry): Boolean {
    return !entry.isDirectory && isProguardRule(entry.name)
}

fun isToolsConfigurationFile(entry: ZipEntry): Boolean {
    return !entry.isDirectory && isToolsConfigurationFile(entry.name)
}

class ExtractProGuardRulesTransform @Inject constructor() : ArtifactTransform() {

    override fun transform(jarFile: File): List<File> {
        return performTransform(jarFile, outputDirectory)
    }

    companion object {
        @JvmStatic
        fun performTransform(
            jarFile: File,
            outputDirectory: File,
            extractLegacyProguardRules: Boolean = true
        ): List<File> {
            ZipFile(jarFile, StandardCharsets.UTF_8).use { zipFile ->
                zipFile
                    .stream()
                    .filter { zipEntry ->
                        isToolsConfigurationFile(zipEntry)
                                || (extractLegacyProguardRules && isProguardRule(zipEntry))
                    }
                    .forEach { zipEntry ->
                        val outPath = zipEntry.name.replace('/', File.separatorChar)
                        val outFile = FileUtils.join(outputDirectory, "lib", outPath)
                        mkdirs(outFile.parentFile)
                        BufferedInputStream(zipFile.getInputStream(zipEntry)).use { inFileStream ->
                            BufferedOutputStream(outFile.outputStream()).use {
                                ByteStreams.copy(inFileStream, it)
                            }
                        }
                    }

                return if (outputDirectory.listFiles().isNotEmpty()) {
                    listOf(outputDirectory)
                } else {
                    emptyList()
                }
            }
        }
    }
}