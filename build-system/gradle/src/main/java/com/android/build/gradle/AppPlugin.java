/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin extends AbstractAppPlugin {
    @Inject
    public AppPlugin(ToolingModelBuilderRegistry registry) {
        super(registry, true /*isBaseApplication*/);
    }

    @Override
    public void apply(@NonNull Project project) {
        super.apply(project);

        // create the configuration used to declare the feature split in the base split.
        Configuration featureSplit =
                project.getConfigurations().maybeCreate(VariantDependencies.CONFIG_NAME_FEATURE);
        featureSplit.setCanBeConsumed(false);
        featureSplit.setCanBeResolved(false);
    }
}
