/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.tasks.factory.AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import org.gradle.api.Project;

/** Common superclass for all {@link VariantFactory} implementations. */
public abstract class BaseVariantFactory implements VariantFactory {

    @NonNull protected final GlobalScope globalScope;

    public BaseVariantFactory(@NonNull GlobalScope globalScope) {
        this.globalScope = globalScope;
    }

    @Override
    public void preVariantWork(Project project) {
        if (project.getPluginManager().hasPlugin(ANDROID_APT_PLUGIN_NAME)) {
            globalScope
                    .getErrorHandler()
                    .reportError(
                            Type.INCOMPATIBLE_PLUGIN,
                            "android-apt plugin is incompatible with the Android Gradle plugin.  "
                                    + "Please use 'annotationProcessor' configuration "
                                    + "instead.",
                            "android-apt");
        }
    }

    @Override
    public void validateModel(@NonNull VariantModel model) {
        validateBuildConfig(model);
        validateResValues(model);
    }

    void validateBuildConfig(@NonNull VariantModel model) {
        if (!globalScope.getBuildFeatures().getBuildConfig()) {
            EvalIssueReporter issueReporter = globalScope.getErrorHandler();

            if (!model.getDefaultConfig().getProductFlavor().getBuildConfigFields().isEmpty()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "defaultConfig contains custom BuildConfig fields, but the feature is disabled.");
            }

            for (BuildTypeData buildType : model.getBuildTypes().values()) {
                if (!buildType.getBuildType().getBuildConfigFields().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Build Type '%s' contains custom BuildConfig fields, but the feature is disabled.",
                                    buildType.getBuildType().getName()));
                }
            }

            for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
                if (!productFlavor.getProductFlavor().getBuildConfigFields().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Product Flavor '%s' contains custom BuildConfig fields, but the feature is disabled.",
                                    productFlavor.getProductFlavor().getName()));
                }
            }
        }
    }

    void validateResValues(@NonNull VariantModel model) {
        if (!globalScope.getBuildFeatures().getResValues()) {
            EvalIssueReporter issueReporter = globalScope.getErrorHandler();

            if (!model.getDefaultConfig().getProductFlavor().getResValues().isEmpty()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "defaultConfig contains custom resource values, but the feature is disabled.");
            }

            for (BuildTypeData buildType : model.getBuildTypes().values()) {
                if (!buildType.getBuildType().getResValues().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Build Type '%s' contains custom resource values, but the feature is disabled.",
                                    buildType.getBuildType().getName()));
                }
            }

            for (ProductFlavorData productFlavor : model.getProductFlavors().values()) {
                if (!productFlavor.getProductFlavor().getResValues().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Product Flavor '%s' contains custom resource values, but the feature is disabled.",
                                    productFlavor.getProductFlavor().getName()));
                }
            }
        }
    }

}
