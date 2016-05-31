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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.builder.core.VariantConfiguration;
import com.android.utils.FileUtils;

import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A task to merge the atoms in the instant app.
 */
@ParallelizableTask
public class MergeAtoms extends DefaultAndroidTask {

    @TaskAction
    public void taskAction() throws IOException, BuildException {
        File outputDir = getOutputDir();
        FileUtils.cleanOutputDir(outputDir);

        List<File> inputAtomDirectories = getInputAtomDirectories();
        for (File atomDirectory : inputAtomDirectories) {
            if (!atomDirectory.isDirectory()) {
                throw new BuildException("Atom folder is not a directory: " + atomDirectory.getPath(), null);
            }

            for (File atomFile : FileUtils.find(atomDirectory, Pattern.compile("\\.atom$"))) {
                FileUtils.copyFile(atomFile, new File(outputDir, atomFile.getName()));
            }
        }
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @InputFiles
    public List<File> getInputAtomDirectories() {
        return inputAtomDirectories;
    }

    public void setInputAtomDirectories(List<File> inputAtomDirectories) {
        this.inputAtomDirectories = inputAtomDirectories;
    }

    private File outputDir;

    private List<File> inputAtomDirectories;

    public static class ConfigAction implements TaskConfigAction<MergeAtoms> {

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "Atoms");
        }

        @NonNull
        @Override
        public Class<MergeAtoms> getType() {
            return MergeAtoms.class;
        }

        @Override
        public void execute(@NonNull MergeAtoms mergeAtoms) {
            VariantConfiguration variantConfig =
                    scope.getVariantData().getVariantConfiguration();

            mergeAtoms.setVariantName(variantConfig.getFullName());
            mergeAtoms.setInputAtomDirectories(variantConfig.getAtomsDirectories());
            mergeAtoms.setOutputDir(scope.getMergeAssetsOutputDir());
        }

        @NonNull
        private VariantScope scope;

    }
}
