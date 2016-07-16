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
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.model.AndroidAtom;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * A task to process InstantApp resources.
 */
@ParallelizableTask
public class ProcessInstantAppResources extends IncrementalTask {

    @Override
    public void doFullTaskAction() throws IOException {
        AndroidBuilder builder = getBuilder();
        MergingLog mergingLog = new MergingLog(getMergeBlameLogFolder());
        ProcessOutputHandler processOutputHandler = new ParsingProcessOutputHandler(
                new ToolOutputParser(new AaptOutputParser(), getILogger()),
                new MergingLogRewriter(mergingLog, builder.getErrorReporter()));

        try {
            Aapt aapt = AaptGradleFactory.make(
                    getBuilder(),
                    processOutputHandler,
                    true,
                    true,
                    variantScope.getGlobalScope().getProject(),
                    VariantType.INSTANTAPP,
                    FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp")),
                    aaptOptions.getCruncherProcesses());

            AaptPackageConfig.Builder config = new AaptPackageConfig.Builder()
                    .setManifestFile(getManifestFile())
                    .setOptions(getAaptOptions())
                    .setResourceOutputApk(getOutputResourcePackage())
                    .setVariantType(getType())
                    .setDebuggable(getDebuggable())
                    .setPseudoLocalize(getPseudoLocalesEnabled())
                    .setBaseFeature(getBaseAtomResourcePackage())
                    .setPreviousFeatures(getAtomResourcePackages());

            builder.processResources(aapt, config, true);
        } catch (IOException | InterruptedException | ProcessException e) {
            throw new RuntimeException(e);
        }

    }

    @NonNull
    @InputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(@NonNull File manifestFile) {
        this.manifestFile = manifestFile;
    }

    @NonNull
    @InputFiles
    public Set<File> getAtomResourcePackages() {
        return atomResourcePackages;
    }

    public void setAtomResourcePackages(@NonNull Set<File> atomResourcePackages) {
        this.atomResourcePackages = atomResourcePackages;
    }

    @NonNull
    @InputFile
    public File getBaseAtomResourcePackage() {
        return baseAtomResourcePackage;
    }

    public void setBaseAtomResourcePackage(@NonNull File baseAtomResourcePackage) {
        this.baseAtomResourcePackage = baseAtomResourcePackage;
    }

    @NonNull
    @Input
    public VariantType getType() {
        return type;
    }

    public void setType(VariantType type) {
        this.type = type;
    }

    @Input
    public boolean getDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Input
    public boolean getPseudoLocalesEnabled() {
        return pseudoLocalesEnabled;
    }

    public void setPseudoLocalesEnabled(boolean pseudoLocalesEnabled) {
        this.pseudoLocalesEnabled = pseudoLocalesEnabled;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    public void setAaptOptions(AaptOptions aaptOptions) {
        this.aaptOptions = aaptOptions;
    }

    @NonNull
    @OutputFile
    public File getOutputResourcePackage() {
        return outputResourcePackage;
    }

    public void setOutputResourcePackage(@NonNull File outputResourcePackage) {
        this.outputResourcePackage = outputResourcePackage;
    }

    @NonNull
    @OutputDirectory
    public File getMergeBlameLogFolder() {
        return mergeBlameLogFolder;
    }

    public void setMergeBlameLogFolder(@NonNull File mergeBlameLogFolder) {
        this.mergeBlameLogFolder = mergeBlameLogFolder;
    }


    private File manifestFile;
    private Set<File> atomResourcePackages;
    private File baseAtomResourcePackage;
    private VariantType type;
    private boolean debuggable;
    private boolean pseudoLocalesEnabled;
    private AaptOptions aaptOptions;
    private File outputResourcePackage;
    private File mergeBlameLogFolder;
    private VariantScope variantScope;

    public static class ConfigAction implements TaskConfigAction<ProcessInstantAppResources> {

        public ConfigAction(@NonNull VariantOutputScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "InstantAppResources");
        }

        @NonNull
        @Override
        public Class<ProcessInstantAppResources> getType() {
            return ProcessInstantAppResources.class;
        }

        @Override
        public void execute(@NonNull ProcessInstantAppResources processInstantAppResources)
                throws BuildException {
            final GradleVariantConfiguration config =
                    scope.getVariantScope().getVariantConfiguration();

            processInstantAppResources.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processInstantAppResources.setVariantName(config.getFullName());
            processInstantAppResources.variantScope = scope.getVariantScope();
            processInstantAppResources.setIncrementalFolder(
                    scope.getVariantScope().getIncrementalDir(getName()));

            ConventionMappingHelper.map(processInstantAppResources, "manifestFile",
                    scope.getVariantOutputData().manifestProcessorTask::getOutputFile);

            processInstantAppResources.setType(config.getType());
            processInstantAppResources.setDebuggable(config.getBuildType().isDebuggable());
            processInstantAppResources.setAaptOptions(scope.getGlobalScope().getExtension().getAaptOptions());
            processInstantAppResources.setPseudoLocalesEnabled(
                    config.getBuildType().isPseudoLocalesEnabled());
            processInstantAppResources.setMergeBlameLogFolder(
                    scope.getVariantScope().getResourceBlameLogDir());

            AndroidAtom baseAtom = config.getPackageDependencies().getBaseAtom();
            if (baseAtom == null) {
                processInstantAppResources.getLogger().error(
                        "Instant apps need at least one atom.");
                throw new BuildException("Instant apps need at least one atom.", null);
            }
            processInstantAppResources.setBaseAtomResourcePackage(baseAtom.getResourcePackage());

            List<AndroidAtom> androidAtoms = config.getFlatAndroidAtomsDependencies();
            ImmutableSet.Builder<File> builder = ImmutableSet.builder();
            for (AndroidAtom atom : androidAtoms) {
                if (atom != baseAtom)
                    builder.add(scope.getProcessResourcePackageOutputFile(atom));
            }
            processInstantAppResources.setAtomResourcePackages(builder.build());

            processInstantAppResources.setOutputResourcePackage(
                    scope.getProcessResourcePackageOutputFile());
        }

        @NonNull
        private VariantOutputScope scope;

    }
}
