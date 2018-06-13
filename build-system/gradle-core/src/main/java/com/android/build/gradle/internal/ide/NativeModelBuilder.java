/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.profile.ProcessProfileWriter;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * Builder for the custom Native Android model.
 */
public class NativeModelBuilder implements ToolingModelBuilder {

    @NonNull
    private final VariantManager variantManager;

    public NativeModelBuilder(@NonNull VariantManager variantManager) {
        this.variantManager = variantManager;
    }

    @Override
    public boolean canBuild(@NonNull String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName.equals(NativeAndroidProject.class.getName());
    }

    @Nullable
    @Override
    public Object buildAll(String modelName, @NonNull Project project) {
        NativeAndroidProjectBuilder builder = new NativeAndroidProjectBuilder(project.getName());

        for (VariantScope scope : variantManager.getVariantScopes()) {
            ExternalNativeJsonGenerator generator =
                    scope.getTaskContainer().getExternalNativeJsonGenerator();
            if (generator == null) {
                continue;
            }
            builder.addBuildSystem(generator.getNativeBuildSystem().getName());
            GradleBuildVariant.Builder stats =
                    ProcessProfileWriter.getOrCreateVariant(
                            project.getPath(), scope.getFullVariantName());
            GradleBuildVariant.NativeBuildConfigInfo.Builder config =
                    GradleBuildVariant.NativeBuildConfigInfo.newBuilder();

            if (stats.getNativeBuildConfigCount() == 0) {
                // Do not include stats if they were gathered during build.
                stats.addNativeBuildConfig(config);
            }

            try {
                generator.forEachNativeBuildConfiguration(
                        jsonReader -> {
                            try {
                                builder.addJson(jsonReader, generator.getVariantName(), config);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to read native JSON data", e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to read native JSON data", e);
            }
        }
        return builder.buildOrNull();
    }


}
