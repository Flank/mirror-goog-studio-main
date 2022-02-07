/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.analytics

import com.android.build.api.variant.AbstractSourceDirectories
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

abstract class AnalyticsEnabledAbstractSourceDirectories @Inject constructor(
    open val delegate: AbstractSourceDirectories,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory,
): AbstractSourceDirectories  {

    override fun <T : Task> add(taskProvider: TaskProvider<T>, wiredWith: (T) -> Provider<Directory>) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SOURCES_DIRECTORIES_ADD_VALUE
        delegate.add(taskProvider, wiredWith)
    }

    override fun getName(): String {
        return delegate.name
    }

    override fun addSrcDir(srcDir: String) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SOURCES_DIRECTORIES_SRC_DIR_VALUE
        delegate.addSrcDir(srcDir)
    }

}
