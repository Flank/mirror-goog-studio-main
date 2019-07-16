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

package com.android.build.gradle.internal;


import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.feature.BundleAllClasses;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks for feature variants in an Android feature project. */
public class FeatureTaskManager extends ApplicationTaskManager {

    public FeatureTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                extension,
                variantFactory,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull final VariantScope variantScope,
            @NonNull List<VariantScope> variantScopesForLint) {
        super.createTasksForVariantScope(variantScope, variantScopesForLint);
        // Ensure the compile SDK is at least 26 (O).
        final AndroidVersion androidVersion =
                AndroidTargetHash.getVersionFromHash(
                        variantScope.getGlobalScope().getExtension().getCompileSdkVersion());
        if (androidVersion == null
                || androidVersion.getApiLevel() < AndroidVersion.VersionCodes.O) {
            String message = "Feature modules require compileSdkVersion set to 26 or higher.";
            if (androidVersion != null) {
                message += " compileSdkVersion is set to " + androidVersion.getApiString();
            }
            globalScope.getErrorHandler().reportError(Type.GENERIC, message);
        }

        // FIXME: This is currently disabled due to b/62301277.
        if (extension.getDataBinding().isEnabled() && !extension.getBaseFeature()) {
            String experimentalBinding =
                    BooleanOption.ENABLE_EXPERIMENTAL_FEATURE_DATABINDING.getPropertyName();
            if (projectOptions.get(BooleanOption.ENABLE_EXPERIMENTAL_FEATURE_DATABINDING)) {
                globalScope
                        .getErrorHandler()
                        .reportWarning(
                                Type.GENERIC,
                                "Data binding support for non-base features is experimental "
                                        + "and is not supported.");

            } else {
                globalScope
                        .getErrorHandler()
                        .reportError(
                                Type.GENERIC,
                                "Currently, data binding does not work for non-base features. "
                                        + "Move data binding code to the base feature module.\n"
                                        + "See https://issuetracker.google.com/63814741.\n"
                                        + "To enable data binding with non-base features, set the "
                                        + experimentalBinding
                                        + " properties "
                                        + "to true.");
            }
        }

        // add a warning that the feature module is deprecated and will be removed in the future.
        globalScope
                .getErrorHandler()
                .reportWarning(
                        Type.PLUGIN_OBSOLETE,
                        "The com.android.feature plugin is deprecated and will be removed by the"
                                + " end of 2019. Please switch to using dynamic-features or"
                                + " libraries. For more information on converting your application to using"
                                + " Android App Bundles, please visit"
                                + " https://developer.android.com/topic/google-play-instant/feature-module-migration");
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // Create the classes artifact for use by dependent features.
        taskFactory.register(new BundleAllClasses.CreationAction(scope));
    }
}
