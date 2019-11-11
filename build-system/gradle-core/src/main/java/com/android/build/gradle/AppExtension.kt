/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

/**
 * AppExtension is used directly by build.gradle.kts when configuring project so adding generics
 * declaration is not possible.
 */
abstract class AppExtension(
        project: Project,
        projectOptions: ProjectOptions,
        globalScope: GlobalScope,
        buildTypes: NamedDomainObjectContainer<BuildType>,
        productFlavors: NamedDomainObjectContainer<ProductFlavor>,
        signingConfigs: NamedDomainObjectContainer<SigningConfig>,
        buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
        sourceSetManager: SourceSetManager,
        extraModelInfo: ExtraModelInfo,
        isBaseModule: Boolean
) : AbstractAppExtension(
        project,
        projectOptions,
        globalScope,
        buildTypes,
        productFlavors,
        signingConfigs,
        buildOutputs,
        sourceSetManager,
        extraModelInfo,
        isBaseModule
)