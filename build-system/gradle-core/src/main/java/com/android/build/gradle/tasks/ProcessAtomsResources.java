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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_ANDROID_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_LIB_INFO;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ATOM_RESOURCE_PKG;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;

/** Task to process all atom resources and generate the final R class. */
public class ProcessAtomsResources extends IncrementalTask {

    private static final Logger LOG = Logging.getLogger(ProcessAndroidResources.class);

    @Override
    protected void doFullTaskAction() throws IOException {
        // Find the base atom package.
        @Nullable File baseAtomPackage = null;
        for (AtomConfig.AtomInfo atomInfo : atomConfigTask.getAtomInfoCollection()) {
            if (atomInfo.getResourcePackageFile() != null) {
                baseAtomPackage = atomInfo.getResourcePackageFile();
                break;
            }
        }

        Collection<File> previousAtoms = Lists.newArrayList();
        for (AtomConfig.AtomInfo atomInfo : atomConfigTask.getAtomInfoCollection()) {
            File resOutBaseNameFile = atomInfo.getPackageOutputFile();

            // Base atom, copy the resource package over.
            if (atomInfo.getResourcePackageFile() != null) {
                FileUtils.copyFile(atomInfo.getResourcePackageFile(), resOutBaseNameFile);
                continue;
            }

            File srcOut = atomInfo.getSourceOutputDir();
            FileUtils.cleanOutputDir(srcOut);

            AndroidBuilder builder = getBuilder();
            MergingLog mergingLog = new MergingLog(atomInfo.getMergeBlameLogDir());
            ProcessOutputHandler processOutputHandler =
                    new ParsingProcessOutputHandler(
                            new ToolOutputParser(new AaptOutputParser(), getILogger()),
                            new MergingLogRewriter(mergingLog::find, builder.getErrorReporter()));

            // Compute the R class package name.
            DefaultManifestParser manifestParser =
                    new DefaultManifestParser(atomInfo.getManifestFile());
            String splitName = manifestParser.getSplit();
            String applicationId = manifestParser.getPackage();
            String packageForR = applicationId + "." + splitName;

            try {
                Aapt aapt =
                        AaptGradleFactory.make(
                                aaptGeneration,
                                builder,
                                processOutputHandler,
                                true,
                                FileUtils.mkdirs(
                                        FileUtils.join(
                                                getIncrementalFolder(), "atom", "aapt-temp")),
                                getAaptOptions().getCruncherProcesses());

                AaptPackageConfig.Builder config =
                        new AaptPackageConfig.Builder()
                                .setManifestFile(atomInfo.getManifestFile())
                                .setOptions(aaptOptions)
                                .setResourceDir(atomInfo.getResourceDir())
                                .setLibraries(getLibraryInfoList(atomInfo.getLibInfoFile()))
                                .setCustomPackageForR(packageForR)
                                .setSymbolOutputDir(atomInfo.getTextSymbolOutputDir())
                                .setSourceOutputDir(srcOut)
                                .setResourceOutputApk(resOutBaseNameFile)
                                .setVariantType(VariantType.INSTANTAPP)
                                .setDebuggable(debuggable)
                                .setPseudoLocalize(pseudoLocalesEnabled)
                                .setBaseFeature(baseAtomPackage)
                                .setPreviousFeatures(previousAtoms);
                builder.processResources(aapt, config, true);

                // Add to the list of previous atoms.
                previousAtoms.add(resOutBaseNameFile);

                if (LOG.isInfoEnabled()) {
                    LOG.info("Aapt output file {}", resOutBaseNameFile.getAbsolutePath());
                }

            } catch (IOException | InterruptedException | ProcessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NonNull
    private static List<AaptPackageConfig.LibraryInfo> getLibraryInfoList(@NonNull File libInfoFile)
            throws IOException {
        ImmutableList.Builder<AaptPackageConfig.LibraryInfo> libInfoBuilder =
                ImmutableList.builder();

        if (libInfoFile.exists()) {
            try (Stream<String> stream = Files.lines(Paths.get(libInfoFile.getPath()))) {
                stream.forEach(
                        line -> {
                            String[] fileNames = line.split(Pattern.quote(File.pathSeparator));
                            if (fileNames.length != 2) {
                                return;
                            }
                            libInfoBuilder.add(
                                    new AaptPackageConfig.LibraryInfo(
                                            new File(fileNames[0]), new File(fileNames[1])));
                        });
            }
        }

        return libInfoBuilder.build();
    }

    @InputFiles
    @Optional
    @NonNull
    public FileCollection getAtomResourcePackages() {
        return atomResourcePackages.getArtifactFiles();
    }

    @InputFiles
    @NonNull
    public FileCollection getAtomManifestDirsCollection() {
        return atomManifests.getArtifactFiles();
    }

    @InputFiles
    @NonNull
    public FileCollection getResDirsCollection() {
        return atomResDirs.getArtifactFiles();
    }

    @InputFiles
    @NonNull
    public FileCollection getAtomLibInfoFilesCollection() {
        return atomLibInfos.getArtifactFiles();
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getSourceOutputDirsCollection() {
        return atomConfigTask.getSourceOutputDirsCollection();
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getTextSymbolOutputDirsCollection() {
        return atomConfigTask.getTextSymbolOutputDirsCollection();
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getMergeBlameLogDirsCollection() {
        return atomConfigTask.getMergeBlameLogDirsCollection();
    }

    @OutputFiles
    @NonNull
    public Collection<File> getPackageOutputFilesCollection() {
        return atomConfigTask.getPackageOutputFilesCollection();
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    @Input
    public boolean isPseudoLocalesEnabled() {
        return pseudoLocalesEnabled;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    public void setAaptOptions(AaptOptions aaptOptions) {
        this.aaptOptions = aaptOptions;
    }

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
    }

    private AaptGeneration aaptGeneration;
    private boolean debuggable;
    private boolean pseudoLocalesEnabled;
    private AaptOptions aaptOptions;
    private ArtifactCollection atomManifests;
    private ArtifactCollection atomResourcePackages;
    private ArtifactCollection atomResDirs;
    private ArtifactCollection atomLibInfos;
    private AtomConfig atomConfigTask;

    public static class ConfigAction implements TaskConfigAction<ProcessAtomsResources> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("processAll", "AtomsResources");
        }

        @NonNull
        @Override
        public Class<ProcessAtomsResources> getType() {
            return ProcessAtomsResources.class;
        }

        @Override
        public void execute(@NonNull ProcessAtomsResources processAtomsResources) {
            final BaseVariantData variantData = scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();
            final GlobalScope globalScope = scope.getGlobalScope();

            processAtomsResources.aaptGeneration =
                    AaptGeneration.fromProjectOptions(globalScope.getProjectOptions());
            processAtomsResources.atomConfigTask = scope.getVariantData().atomConfigTask;

            processAtomsResources.setAndroidBuilder(globalScope.getAndroidBuilder());
            processAtomsResources.setVariantName(config.getFullName());
            processAtomsResources.setIncrementalFolder(scope.getIncrementalDir(getName()));
            processAtomsResources.debuggable = config.getBuildType().isDebuggable();
            processAtomsResources.aaptOptions = globalScope.getExtension().getAaptOptions();
            processAtomsResources.pseudoLocalesEnabled =
                    config.getBuildType().isPseudoLocalesEnabled();

            processAtomsResources.atomManifests =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_MANIFEST);
            processAtomsResources.atomResourcePackages =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_RESOURCE_PKG);
            processAtomsResources.atomResDirs =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_ANDROID_RES);
            processAtomsResources.atomLibInfos =
                    scope.getArtifactCollection(COMPILE_CLASSPATH, MODULE, ATOM_LIB_INFO);

            // FIXME: Reset preferred density here.
        }
    }
}
