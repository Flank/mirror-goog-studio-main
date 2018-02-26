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

package com.android.build.gradle.internal.ide

import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_FEATURE
import com.android.build.gradle.internal.model.NativeLibraryFactory
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.builder.core.AndroidBuilder
import org.gradle.api.artifacts.ProjectDependency

open class BaseModuleModelBuilder(
    globalScope: GlobalScope,
    androidBuilder: AndroidBuilder,
    variantManager: VariantManager,
    taskManager: TaskManager,
    config: AndroidConfig,
    extraModelInfo: ExtraModelInfo,
    ndkHandler: NdkHandler,
    nativeLibraryFactory: NativeLibraryFactory,
    projectType: Int,
    generation: Int
) : ModelBuilder(
    globalScope, androidBuilder, variantManager, taskManager, config, extraModelInfo,
    ndkHandler, nativeLibraryFactory, projectType, generation
) {

    override fun getDynamicFeatures(): MutableCollection<String> {
        val featureConfig = globalScope.project.configurations.getByName(CONFIG_NAME_FEATURE)
        val dependencies = featureConfig.dependencies

        return dependencies
            .asSequence()
            .filter { it is ProjectDependency }
            .map { (it as ProjectDependency).dependencyProject.path }
            .toMutableList()
    }
}