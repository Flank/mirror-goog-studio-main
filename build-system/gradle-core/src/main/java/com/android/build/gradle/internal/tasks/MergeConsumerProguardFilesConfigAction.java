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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import java.io.File;
import org.gradle.api.Project;

/** Configuration action for a merge-Proguard-files task. */
public class MergeConsumerProguardFilesConfigAction implements TaskConfigAction<MergeFileTask> {

    @NonNull private final Project project;
    @NonNull private final VariantScope variantScope;
    @NonNull private final File outputFile;

    public MergeConsumerProguardFilesConfigAction(
            @NonNull Project project,
            @NonNull VariantScope variantScope,
            @NonNull File outputFile) {
        this.project = project;
        this.variantScope = variantScope;
        this.outputFile = outputFile;
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
        mergeProguardFiles.setVariantName(variantScope.getVariantConfiguration().getFullName());
        mergeProguardFiles.setInputFiles(
                project.files(variantScope.getConsumerProguardFiles()).getFiles());
        mergeProguardFiles.setOutputFile(outputFile);
    }
}
