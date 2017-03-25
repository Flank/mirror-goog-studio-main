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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.utils.FileUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Extracts Java 8 language support jar. */
public class ExtractJava8LangSupportJar extends DefaultAndroidTask {

    public static final String TASK_NAME = "extractJava8LangSupportJar";

    @OutputFile private ConfigurableFileCollection outputLocation;

    @TaskAction
    public void run() throws IOException {
        try (InputStream in =
                DesugarProcessBuilder.class
                        .getClassLoader()
                        .getResourceAsStream("desugar_deploy.jar")) {
            FileUtils.cleanOutputDir(outputLocation.getSingleFile().getParentFile());
            Files.copy(in, outputLocation.getSingleFile().toPath());
        }
    }

    public static class ConfigAction implements TaskConfigAction<ExtractJava8LangSupportJar> {

        @NonNull private ConfigurableFileCollection outputLocation;
        @NonNull private String taskName;

        public ConfigAction(
                @NonNull ConfigurableFileCollection outputLocation, @NonNull String taskName) {
            this.outputLocation = outputLocation;
            this.taskName = taskName;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<ExtractJava8LangSupportJar> getType() {
            return ExtractJava8LangSupportJar.class;
        }

        @Override
        public void execute(@NonNull ExtractJava8LangSupportJar task) {
            task.outputLocation = outputLocation;
            task.setVariantName("");
        }
    }
}
