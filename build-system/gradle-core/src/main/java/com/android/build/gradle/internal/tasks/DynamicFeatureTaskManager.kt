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

package com.android.build.gradle.internal.tasks

import android.databinding.tool.DataBindingBuilder
import com.android.build.api.variant.impl.DynamicFeatureVariantPropertiesImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.AbstractAppTaskManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.builder.profile.Recorder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

internal class DynamicFeatureTaskManager(
    globalScope: GlobalScope,
    databindingBuilder: DataBindingBuilder,
    extension: BaseExtension,
    toolingRegistry: ToolingModelBuilderRegistry,
    recorder: Recorder
) : AbstractAppTaskManager<DynamicFeatureVariantPropertiesImpl>(
    globalScope,
    databindingBuilder,
    extension,
    toolingRegistry,
    recorder
) {

    override fun createTasksForVariant(
        variantProperties: DynamicFeatureVariantPropertiesImpl,
        allComponentsWithLint: MutableList<DynamicFeatureVariantPropertiesImpl>
    ) {
        createCommonTasks(variantProperties, allComponentsWithLint)
    }
}
