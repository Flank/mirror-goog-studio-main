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
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;

/** Configuration action for a merge-Proguard-files task. */
public class MergeConsumerProguardFilesConfigAction implements TaskConfigAction<MergeFileTask> {

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
                        .getBuildArtifactsHolder()
                        .appendArtifact(
                                InternalArtifactType.CONSUMER_PROGUARD_FILE,
                                mergeProguardFiles,
                                SdkConstants.FN_PROGUARD_TXT));
        mergeProguardFiles.setInputFiles(
                project.files(variantScope.getConsumerProguardFiles()).getFiles());

        // Check that the library is not trying to ship one of the default files as a consumer file.
        Map<File, String> defaultFiles = new HashMap<>();
        for (String knownFileName : ProguardFiles.KNOWN_FILE_NAMES) {
            defaultFiles.put(
                    ProguardFiles.getDefaultProguardFile(knownFileName, project), knownFileName);
        }

        EvalIssueReporter issueReporter = variantScope.getGlobalScope().getErrorHandler();

        for (File consumerFile : mergeProguardFiles.getInputFiles()) {
            if (defaultFiles.containsKey(consumerFile)) {
                issueReporter.reportError(
                        Type.GENERIC,
                        new EvalIssueException(
                                String.format(
                                        "Default file %s should not be used as a consumer configuration file.",
                                        defaultFiles.get(consumerFile))));
            }
        }
    }
}
