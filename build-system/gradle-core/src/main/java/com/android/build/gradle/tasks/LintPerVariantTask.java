/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.utils.StringHelper;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public class LintPerVariantTask extends LintBaseTask {

    private VariantInputs variantInputs;
    private boolean fatalOnly;

    @InputFiles
    @Optional
    public FileCollection getVariantInputs() {
        return variantInputs.getAllInputs();
    }

    @TaskAction
    public void lint() {
        runLint(new LintPerVariantTaskDescriptor());
    }

    private class LintPerVariantTaskDescriptor extends LintBaseTaskDescriptor {
        @Nullable
        @Override
        public String getVariantName() {
            return LintPerVariantTask.this.getVariantName();
        }

        @Nullable
        @Override
        public VariantInputs getVariantInputs(@NonNull String variantName) {
            assert variantName.equals(getVariantName());
            return variantInputs;
        }

        @Override
        public boolean isFatalOnly() {
            return fatalOnly;
        }
    }

    public static class ConfigAction extends BaseConfigAction<LintPerVariantTask> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            super(scope.getGlobalScope());
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("lint");
        }

        @Override
        @NonNull
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void execute(@NonNull LintPerVariantTask lint) {
            super.execute(lint);

            lint.setVariantName(scope.getVariantConfiguration().getFullName());

            lint.variantInputs = new VariantInputs(scope);

            lint.setDescription(
                    StringHelper.appendCapitalized(
                            "Runs lint on the ",
                            scope.getVariantConfiguration().getFullName(),
                            " build."));
        }
    }

    public static class VitalConfigAction extends BaseConfigAction<LintPerVariantTask> {

        private final VariantScope scope;

        public VitalConfigAction(@NonNull VariantScope scope) {
            super(scope.getGlobalScope());
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("lintVital");
        }

        @NonNull
        @Override
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void execute(@NonNull LintPerVariantTask task) {
            super.execute(task);

            String variantName = scope.getVariantData().getVariantConfiguration().getFullName();
            task.setVariantName(variantName);

            task.variantInputs = new VariantInputs(scope);
            task.fatalOnly = true;
            task.setDescription(
                    "Runs lint on just the fatal issues in the " + variantName + " build.");
        }
    }
}
