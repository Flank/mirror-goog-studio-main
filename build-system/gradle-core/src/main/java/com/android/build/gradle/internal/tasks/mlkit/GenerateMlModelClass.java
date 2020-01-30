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

package com.android.build.gradle.internal.tasks.mlkit;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

@CacheableTask
public abstract class GenerateMlModelClass extends NonIncrementalTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract DirectoryProperty getModelFileDir();

    @OutputDirectory
    public abstract DirectoryProperty getSourceOutDir();

    @Override
    protected void doTaskAction() {
        // TODO(b/146015231): generate model classes to #getSourceOutDir for model files in
        //  #getModelFileDir.
    }

    public static class CreationAction
            extends VariantTaskCreationAction<GenerateMlModelClass, ComponentPropertiesImpl> {
        public CreationAction(@NonNull ComponentPropertiesImpl componentProperties) {
            super(componentProperties);
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends GenerateMlModelClass> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.MLKIT_SOURCE_OUT.INSTANCE,
                            taskProvider,
                            GenerateMlModelClass::getSourceOutDir,
                            "out");
        }

        @Override
        public void configure(@NonNull GenerateMlModelClass task) {
            super.configure(task);

            // TODO(b/146015231): consider to use a new type here, rather than MERGED_ASSETS.
            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.MERGED_ASSETS.INSTANCE, task.getModelFileDir());
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("generate", "MlModelClass");
        }

        @NonNull
        @Override
        public Class<GenerateMlModelClass> getType() {
            return GenerateMlModelClass.class;
        }
    }
}
