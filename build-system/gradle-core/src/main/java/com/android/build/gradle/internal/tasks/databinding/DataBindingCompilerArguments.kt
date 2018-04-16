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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.CompilerArguments
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * Arguments passed to data binding. This class mimics the [CompilerArguments] class except that it
 * also implements [CommandLineArgumentProvider] for input/output annotations.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataBindingCompilerArguments constructor(
    @get:Input
    val artifactType: CompilerArguments.Type,

    @get:Input
    val modulePackage: String,

    @get:Input
    val minApi: Int,

    // We can't set the sdkDir as an @InputDirectory because it is too large to compute a hash. We
    // can't set it as an @Input either because it would break cache relocatability. Therefore, we
    // annotate it with @Internal, expecting that the directory's contents should be stable and this
    // won't affect correctness.
    @get:Internal
    val sdkDir: File,

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val buildDir: File,

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val layoutInfoDir: File,

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val classLogDir: File,

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val baseFeatureInfoDir: File?,

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val featureInfoDir: File?,

    @get:Optional
    @get:OutputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val aarOutDir: File?,

    @get:Optional
    @get:OutputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val exportClassListOutFile: File?,

    @get:Input
    val enableDebugLogs: Boolean,

    @get:Input
    val printEncodedErrorLogs: Boolean,

    @get:Input
    val isTestVariant: Boolean,

    @get:Input
    val isEnabledForTests: Boolean,

    @get:Input
    val isEnableV2: Boolean
) : CommandLineArgumentProvider {

    fun toMap(): Map<String, String> {
        return CompilerArguments(
            artifactType = artifactType,
            modulePackage = modulePackage,
            minApi = minApi,
            sdkDir = sdkDir,
            buildDir = buildDir,
            layoutInfoDir = layoutInfoDir,
            classLogDir = classLogDir,
            baseFeatureInfoDir = baseFeatureInfoDir,
            featureInfoDir = featureInfoDir,
            aarOutDir = aarOutDir,
            exportClassListOutFile = exportClassListOutFile,
            enableDebugLogs = enableDebugLogs,
            printEncodedErrorLogs = printEncodedErrorLogs,
            isTestVariant = isTestVariant,
            isEnabledForTests = isEnabledForTests,
            isEnableV2 = isEnableV2
        ).toMap()
    }

    override fun asArguments(): Iterable<String> {
        // Don't need to sort the returned list as the order shouldn't matter to Gradle.
        // Also don't need to escape the key and value strings as they will be passed as-is to
        // the Java compiler.
        return toMap().map { entry -> "-A${entry.key}=${entry.value}" }
    }
}