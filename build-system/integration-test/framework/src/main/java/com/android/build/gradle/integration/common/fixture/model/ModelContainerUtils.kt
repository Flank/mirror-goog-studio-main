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

package com.android.build.gradle.integration.common.fixture.model

import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.VariantDependencies


fun ModelBuilderV2.FetchResult<ModelContainerV2>.getModelVersions(
    projectPath: String?
): Versions = if (projectPath == null) {
    container.singleVersions
} else {
    container.rootInfoMap[projectPath]?.versions
            ?: throw RuntimeException("failed to find model info for $projectPath")
}

fun ModelBuilderV2.FetchResult<ModelContainerV2>.getAndroidProject(
    projectPath: String?
): AndroidProject = if (projectPath == null) {
    container.singleAndroidProject
} else {
    val infoMap = container.rootInfoMap[projectPath]
            ?: throw RuntimeException("failed to find model info for $projectPath")

    infoMap.androidProject
            ?: throw java.lang.RuntimeException("No AndroidProject for $projectPath")
}

fun ModelBuilderV2.FetchResult<ModelContainerV2>.getAndroidDsl(
    projectPath: String?
): AndroidDsl = if (projectPath == null) {
    container.singleAndroidDsl
} else {
    val infoMap = container.rootInfoMap[projectPath]
            ?: throw RuntimeException("failed to find model info for $projectPath")

    infoMap.androidDsl
            ?: throw java.lang.RuntimeException("No AndroidDsl for $projectPath")
}

fun ModelBuilderV2.FetchResult<ModelContainerV2>.getVariantDependencies(
    projectPath: String?
): VariantDependencies = if (projectPath == null) {
    container.singleVariantDependencies
} else {
    val infoMap = container.rootInfoMap[projectPath]
            ?: throw RuntimeException("failed to find model info for $projectPath")

    infoMap.variantDependencies
            ?: throw java.lang.RuntimeException("No VariantDependencies for $projectPath")
}
