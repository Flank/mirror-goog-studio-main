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

package com.android.build.gradle.internal.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.profile.ProcessProfileWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

/**
 * Additional processing for analytics that must be done during task execution phase.
 *
 * <p>Expected to be executed every run.
 */
public class ProcessAnalyticsTask extends BaseTask {

    private FileCollection processorListFile;

    @InputFiles
    public FileCollection getProcessorListFile() {
        return processorListFile;
    }

    public void init(String variantName, FileCollection processorListFile) {
        this.setVariantName(variantName);
        this.processorListFile = processorListFile;
    }

    @TaskAction
    public void processAnalytics() throws IOException {
        Gson gson = new GsonBuilder().create();
        FileReader reader = new FileReader(processorListFile.getSingleFile());
        List<String> classNames = gson.fromJson(reader, new TypeToken<List<String>>() {}.getType());

        String projectPath = getProject().getPath();
        String variantName = checkNotNull(getVariantName());
        GradleBuildVariant.Builder variant =
                ProcessProfileWriter.getOrCreateVariant(projectPath, variantName);
        for (String processorName : classNames) {
            AnnotationProcessorInfo.Builder builder = AnnotationProcessorInfo.newBuilder();
            builder.setSpec(processorName);
            variant.addAnnotationProcessors(builder);
        }
    }

    public static class ConfigAction implements TaskConfigAction<ProcessAnalyticsTask> {

        private final VariantScope scope;
        private final FileCollection processorListFile;

        public ConfigAction(VariantScope scope, FileCollection processorListFile) {
            this.scope = scope;
            this.processorListFile = processorListFile;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("processAnalytics");
        }

        @NonNull
        @Override
        public Class<ProcessAnalyticsTask> getType() {
            return ProcessAnalyticsTask.class;
        }

        @Override
        public void execute(@NonNull ProcessAnalyticsTask task) {
            task.init(scope.getFullVariantName(), processorListFile);
        }
    }
}
