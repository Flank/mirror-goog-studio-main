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

package com.android.build.gradle.internal.plugins;

import com.android.annotations.NonNull;
import com.android.build.gradle.AppExtension;
import com.android.builder.model.AndroidProject;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on an optional APK module */
public class DynamicFeaturePlugin extends AbstractAppPlugin {
    @Inject
    public DynamicFeaturePlugin(
            ToolingModelBuilderRegistry registry, SoftwareComponentFactory componentFactory) {
        super(registry, componentFactory, false /*isBaseApplication*/);
    }

    @Override
    protected int getProjectType() {
        return AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.DYNAMIC_FEATURE;
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
        // do nothing
    }

    @NonNull
    @Override
    protected Class<? extends AppExtension> getExtensionClass() {
        return AppExtension.class;
    }
}
