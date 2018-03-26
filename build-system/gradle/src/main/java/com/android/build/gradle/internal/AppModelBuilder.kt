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

package com.android.build.gradle.internal

import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.ide.BaseModuleModelBuilder
import com.android.build.gradle.internal.model.NativeLibraryFactory
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.builder.core.AndroidBuilder
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.AppPlugin

/**
 * [ModelBuilder] class created by [AppPlugin]. It needs to be put in a separate file to work around
 * https://issuetracker.google.com/73383831.
 */
class AppModelBuilder(
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
) : BaseModuleModelBuilder(
    globalScope,
    androidBuilder,
    variantManager,
    taskManager,
    config,
    extraModelInfo,
    ndkHandler,
    nativeLibraryFactory,
    projectType,
    generation
) {
    override fun isBaseSplit(): Boolean {
        return true
    }
}
