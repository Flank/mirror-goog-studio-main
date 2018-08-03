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

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.FeaturePlugin;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;

/** Configuration action for a merge-Proguard-files task. */
public class MergeConsumerProguardFilesConfigAction extends TaskConfigAction<MergeFileTask> {

    @NonNull private final VariantScope variantScope;

    public MergeConsumerProguardFilesConfigAction(@NonNull VariantScope variantScope) {
        this.variantScope = variantScope;
    }

    @NonNull
    @Override
    public String getName() {
        return variantScope.getTaskName("merge", "ConsumerProguardFiles");
    }

    @NonNull
    @Override
    public Class<MergeFileTask> getType() {
        return MergeFileTask.class;
    }

    @Override
    public void execute(@NonNull MergeFileTask mergeProguardFiles) {
        Project project = variantScope.getGlobalScope().getProject();
        mergeProguardFiles.setVariantName(variantScope.getVariantConfiguration().getFullName());
        mergeProguardFiles.setOutputFile(
                variantScope
                        .getArtifacts()
                        .appendArtifact(
                                InternalArtifactType.CONSUMER_PROGUARD_FILE,
                                mergeProguardFiles,
                                SdkConstants.FN_PROGUARD_TXT));
        final boolean hasFeaturePlugin = project.getPlugins().hasPlugin(FeaturePlugin.class);
        // We include proguardFiles if we're in a dynamic-feature or feature module. For feature
        // modules, we check for the presence of the FeaturePlugin, because we want to include
        // proguardFiles even when we're in the library variant.
        final boolean includeProguardFiles =
                hasFeaturePlugin || variantScope.getType().isDynamicFeature();
        final boolean isBaseFeature =
                hasFeaturePlugin && variantScope.getGlobalScope().getExtension().getBaseFeature();
        final Collection<File> consumerProguardFiles = variantScope.getConsumerProguardFiles();
        if (includeProguardFiles) {
            consumerProguardFiles.addAll(variantScope.getExplicitProguardFiles());
        }
        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseFeature) {
            checkForDefaultFiles(consumerProguardFiles);
        }
        ConfigurableFileCollection inputFiles = project.files(consumerProguardFiles);
        if (variantScope.getType().isFeatureSplit()) {
            inputFiles.from(
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.CONSUMER_PROGUARD_RULES));
        }
        mergeProguardFiles.setInputFiles(inputFiles);
    }

    /** Check that we're not trying to ship one of the default files as a consumer file. */
    private void checkForDefaultFiles(@NonNull Collection<File> consumerProguardFiles) {
        Map<File, String> defaultFiles = new HashMap<>();
        for (String knownFileName : ProguardFiles.KNOWN_FILE_NAMES) {
            defaultFiles.put(
                    ProguardFiles.getDefaultProguardFile(
                            knownFileName, variantScope.getGlobalScope().getProject()),
                    knownFileName);
        }

        EvalIssueReporter issueReporter = variantScope.getGlobalScope().getErrorHandler();

        for (File consumerProguardFile : consumerProguardFiles) {
            if (defaultFiles.containsKey(consumerProguardFile)) {
                final String errorMessage;
                if (variantScope.getType().isDynamicFeature()
                        || variantScope
                                .getGlobalScope()
                                .getProject()
                                .getPlugins()
                                .hasPlugin(FeaturePlugin.class)) {
                    errorMessage =
                            "Default file "
                                    + defaultFiles.get(consumerProguardFile)
                                    + " should not be specified in this module."
                                    + " It can be specified in the base module instead.";

                } else {
                    errorMessage =
                            "Default file "
                                    + defaultFiles.get(consumerProguardFile)
                                    + " should not be used as a consumer configuration file.";
                }

                issueReporter.reportError(Type.GENERIC, new EvalIssueException(errorMessage));
            }
        }
    }
}
