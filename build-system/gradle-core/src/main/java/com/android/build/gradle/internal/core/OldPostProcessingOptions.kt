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

package com.android.build.gradle.internal.core

import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.getProguardFiles
import com.android.builder.model.CodeShrinker
import org.gradle.api.Project
import java.io.File

/**
 * This is an implementation of PostProcessingOptions interface for the old DSL
 */
class OldPostProcessingOptions(
    private val buildType: BuildType,
    private val project: Project
) : PostProcessingOptions {
    override fun getProguardFiles(type: ProguardFileType): Collection<File> = buildType.getProguardFiles(type)

    override fun getDefaultProguardFiles(): List<File> =
        listOf(ProguardFiles.getDefaultProguardFile(ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName, project))

    override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

    override fun getCodeShrinker() = when {
        !buildType.isMinifyEnabled -> null
        buildType.isUseProguard == true -> CodeShrinker.PROGUARD
        else -> CodeShrinker.R8
    }

    override fun resourcesShrinkingEnabled(): Boolean = buildType.isShrinkResources
}
