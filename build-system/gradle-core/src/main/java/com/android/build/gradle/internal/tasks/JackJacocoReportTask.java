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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.ide.common.process.ProcessException;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.util.Collection;
import java.util.function.Supplier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.List;

/**
 * Jacoco report task for Jack.
 */

public class JackJacocoReportTask extends BaseTask {

    private File coverageDirectory;

    private File reportDir;

    private Supplier<Collection<File>> sourceDir;

    private String reportName;

    private File metadataFile;

    @InputDirectory
    public File getCoverageDirectory() {
        return coverageDirectory;
    }

    public void setCoverageDirectory(File coverageDirectory) {
        this.coverageDirectory = coverageDirectory;
    }

    @OutputDirectory
    public File getReportDir() {
        return reportDir;
    }

    public void setReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    @InputFiles
    public Collection<File> getSourceDir() {
        return sourceDir.get();
    }

    @Input
    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    @InputFile
    public File getMetadataFile() {
        return metadataFile;
    }

    public void setMetadataFile(File metadataFile) {
        this.metadataFile = metadataFile;
    }

    @TaskAction
    void createReport() throws ProcessException {
        List<File> coverageFiles = Lists.newArrayList();
        if (getCoverageDirectory() != null) {
            Files.fileTreeTraverser().breadthFirstTraversal(getCoverageDirectory())
                    .filter(File::isFile)
                    .copyInto(coverageFiles);
        }

        if (coverageFiles.isEmpty()) {
            throw new ProcessException(String.format(
                    "No coverage data to process in directory '%1$s'", getCoverageDirectory()));
        } else if (coverageFiles.size() > 1) {
            throw new ProcessException(String.format(
                    "More than one coverage file found in directory '%1$s', sharding for test "
                            + "coverage is not yet supported for Jack", getCoverageDirectory()));
        }
        getBuilder().createJacocoReportWithJackReporter(
                Iterables.getOnlyElement(coverageFiles),
                getReportDir(),
                sourceDir.get(),
                getReportName(),
                getMetadataFile());
    }


    public static class ConfigAction implements TaskConfigAction<JackJacocoReportTask> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("create", "CoverageReport");
        }

        @NonNull
        @Override
        public Class<JackJacocoReportTask> getType() {
            return JackJacocoReportTask.class;
        }

        @Override
        public void execute(@NonNull JackJacocoReportTask task) {

            task.setVariantName(scope.getVariantConfiguration().getFullName());
            task.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());

            checkNotNull(scope.getTestedVariantData());
            final VariantScope testedScope = scope.getTestedVariantData().getScope();

            task.setDescription("Creates JaCoCo test coverage report from "
                    + "data gathered on the device.");

            task.setReportName(scope.getVariantConfiguration().getFullName());


            task.coverageDirectory =
                    ((TestVariantData) scope.getVariantData()).connectedTestTask.getCoverageDir();

            task.sourceDir = TaskInputHelper.bypassFileSupplier(() ->
                            testedScope.getVariantData().getJavaSourceFoldersForCoverage());

            task.setReportDir(testedScope.getCoverageReportDir());
            task.setMetadataFile(testedScope.getJackCoverageMetadataFile());
        }
    }
}
