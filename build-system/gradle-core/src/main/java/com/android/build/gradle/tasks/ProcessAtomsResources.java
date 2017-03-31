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
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.AtomDependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
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
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
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
        Collection<File> previousAtoms = Lists.newArrayList();

        for (AtomDependency atomDep : getFlatAtomList()) {
            String atomName = atomDep.getAtomName();
            File resOutBaseNameFile = getPackageOutputFiles().get(atomName);

            // Base atom, copy the resource package over.
            if (atomDep.getResourcePackage().exists()) {
                FileUtils.copyFile(getBaseAtomPackage(), resOutBaseNameFile);
                continue;
            }

            File srcOut = getSourceOutputDirs().get(atomName);
            assert srcOut != null;
            FileUtils.cleanOutputDir(srcOut);

            AndroidBuilder builder = getBuilder();
            MergingLog mergingLog = new MergingLog(getMergeBlameLogFolders().get(atomName));
            ProcessOutputHandler processOutputHandler =
                    new ParsingProcessOutputHandler(
                            new ToolOutputParser(new AaptOutputParser(), getILogger()),
                            new MergingLogRewriter(mergingLog::find, builder.getErrorReporter()));

            try {
                Aapt aapt =
                        AaptGradleFactory.make(
                                builder,
                                processOutputHandler,
                                true,
                                getProject(),
                                FileUtils.mkdirs(
                                        FileUtils.join(
                                                getIncrementalFolder(), "atom", "aapt-temp")),
                                getAaptOptions().getCruncherProcesses());

                AaptPackageConfig.Builder config =
                        new AaptPackageConfig.Builder()
                                .setManifestFile(getManifestFiles().get(atomName))
                                .setOptions(getAaptOptions())
                                .setResourceDir(getResDirs().get(atomName))
                                .setLibraries(getAtomLibraryDependencies().get(atomName))
                                .setCustomPackageForR(getPackagesForR().get(atomName))
                                .setSymbolOutputDir(getTextSymbolOutputDirs().get(atomName))
                                .setSourceOutputDir(srcOut)
                                .setResourceOutputApk(resOutBaseNameFile)
                                .setVariantType(VariantType.INSTANTAPP)
                                .setDebuggable(isDebuggable())
                                .setPseudoLocalize(isPseudoLocalesEnabled())
                                .setResourceConfigs(getResourceConfigs())
                                .setPreferredDensity(getPreferredDensity())
                                .setBaseFeature(getBaseAtomPackage())
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

    @InputFiles
    @NonNull
    public Collection<File> getManifestFilesCollection() {
        return getManifestFiles().values();
    }

    @Input
    @NonNull
    public Map<String, File> getManifestFiles() {
        return manifestFiles;
    }

    public void setManifestFiles(@NonNull Map<String, File> manifestFiles) {
        this.manifestFiles = manifestFiles;
    }

    @InputFiles
    @NonNull
    public Collection<File> getResDirsCollection() {
        return getResDirs().values();
    }

    @Input
    @NonNull
    public Map<String, File> getResDirs() {
        return resDirs;
    }

    public void setResDirs(@NonNull Map<String, File> resDirs) {
        this.resDirs = resDirs;
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getSourceOutputDirsCollection() {
        return getSourceOutputDirs().values();
    }

    @NonNull
    public Map<String, File> getSourceOutputDirs() {
        return sourceOutputDirs;
    }

    public void setSourceOutputDirs(@NonNull Map<String, File> sourceOutputDirs) {
        this.sourceOutputDirs = sourceOutputDirs;
    }

    @OutputDirectories
    @NonNull
    public Collection<File> getTextSymbolOutputDirsCollection() {
        return getTextSymbolOutputDirs().values();
    }

    @Input
    @NonNull
    public Map<String, File> getTextSymbolOutputDirs() {
        return textSymbolOutputDirs;
    }

    public void setTextSymbolOutputDirs(@NonNull Map<String, File> textSymbolOutputDirs) {
        this.textSymbolOutputDirs = textSymbolOutputDirs;
    }

    @OutputFiles
    @NonNull
    public Collection<File> getPackageOutputFilesCollection() {
        return getPackageOutputFiles().values();
    }

    @Input
    @NonNull
    public Map<String, File> getPackageOutputFiles() {
        return packageOutputFiles;
    }

    public void setPackageOutputFiles(@NonNull Map<String, File> packageOutputFiles) {
        this.packageOutputFiles = packageOutputFiles;
    }

    @Input
    @NonNull
    public Collection<String> getResourceConfigs() {
        return resourceConfigs;
    }

    public void setResourceConfigs(@NonNull Collection<String> resourceConfigs) {
        this.resourceConfigs = resourceConfigs;
    }

    @Input
    @Optional
    @Nullable
    public String getPreferredDensity() {
        return preferredDensity;
    }

    public void setPreferredDensity(@Nullable String preferredDensity) {
        this.preferredDensity = preferredDensity;
    }

    @InputFiles
    @NonNull
    public List<File> getInputFilesFromLibraries() {
        List<AndroidDependency> libDeps =
                getAtomLibraryDependencies()
                        .values()
                        .stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
        List<File> files = Lists.newArrayListWithCapacity(libDeps.size() * 2);
        for (AndroidDependency dependency : libDeps) {
            files.add(dependency.getManifest());
            files.add(dependency.getSymbolFile());
        }
        return files;
    }

    @NonNull
    public Map<String, List<AndroidDependency>> getAtomLibraryDependencies() {
        return atomLibraryDependencies;
    }

    public void setAtomLibraryDependencies(
            @NonNull Map<String, List<AndroidDependency>> atomLibraryDependencies) {
        this.atomLibraryDependencies = atomLibraryDependencies;
    }

    @Input
    public Map<String, String> getPackagesForR() {
        return packagesForR;
    }

    public void setPackagesForR(Map<String, String> packagesForR) {
        this.packagesForR = packagesForR;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Input
    public boolean isPseudoLocalesEnabled() {
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

    @Input
    public Map<String, File> getMergeBlameLogFolders() {
        return mergeBlameLogFolders;
    }

    public void setMergeBlameLogFolders(Map<String, File> mergeBlameLogFolders) {
        this.mergeBlameLogFolders = mergeBlameLogFolders;
    }

    @InputFile
    @Optional
    @NonNull
    public File getBaseAtomPackage() {
        return baseAtomPackage.get();
    }

    public void setBaseAtomPackage(@NonNull Supplier<File> baseAtomPackage) {
        this.baseAtomPackage = baseAtomPackage;
    }

    public List<AtomDependency> getFlatAtomList() {
        return flatAtomList.get();
    }

    public void setFlatAtomList(@NonNull Supplier<List<AtomDependency>> flatAtomList) {
        this.flatAtomList = flatAtomList;
    }

    private Map<String, File> manifestFiles;
    private Map<String, File> resDirs;
    private Map<String, File> sourceOutputDirs;
    private Map<String, File> textSymbolOutputDirs;
    private Map<String, File> packageOutputFiles;
    private Collection<String> resourceConfigs;
    private String preferredDensity;
    private Map<String, List<AndroidDependency>> atomLibraryDependencies;
    private Map<String, String> packagesForR;
    private boolean debuggable;
    private boolean pseudoLocalesEnabled;
    private AaptOptions aaptOptions;
    private Map<String, File> mergeBlameLogFolders;
    private Supplier<File> baseAtomPackage;
    private Supplier<List<AtomDependency>> flatAtomList;

    public static class ConfigAction implements TaskConfigAction<ProcessAtomsResources> {

        private final VariantOutputScope scope;

        public ConfigAction(@NonNull VariantOutputScope scope) {
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
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantScope().getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            processAtomsResources.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processAtomsResources.setVariantName(config.getFullName());
            processAtomsResources.setIncrementalFolder(
                    scope.getVariantScope().getIncrementalDir(getName()));
            processAtomsResources.setDebuggable(config.getBuildType().isDebuggable());
            processAtomsResources.setAaptOptions(
                    scope.getGlobalScope().getExtension().getAaptOptions());
            processAtomsResources.setPseudoLocalesEnabled(
                    config.getBuildType().isPseudoLocalesEnabled());

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "resourceConfigs",
                    variantData::discoverListOfResourceConfigs);
            ConventionMappingHelper.map(
                    processAtomsResources,
                    "preferredDensity",
                    () ->
                            AndroidGradleOptions.getBuildTargetDensity(
                                    scope.getGlobalScope().getProject()));

            processAtomsResources.setFlatAtomList(config::getFlatAndroidAtomsDependencies);

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "atomLibraryDependencies",
                    this::computeFlatAtomLibraryMap);

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "packagesForR",
                    () -> {
                        Map<String, String> outPackages = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            DefaultManifestParser manifestParser =
                                    new DefaultManifestParser(atom.getManifest());
                            String splitName = manifestParser.getSplit();
                            String applicationId = manifestParser.getPackage();
                            outPackages.put(atom.getAtomName(), applicationId + "." + splitName);
                        }
                        return outPackages;
                    });

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "sourceOutputDirs",
                    () -> {
                        Map<String, File> srcOutDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            srcOutDirs.put(
                                    atom.getAtomName(),
                                    scope.getVariantScope().getRClassSourceOutputDir(atom));
                        }
                        return srcOutDirs;
                    });

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "textSymbolOutputDirs",
                    () -> {
                        Map<String, File> textSymbolDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            textSymbolDirs.put(
                                    atom.getAtomName(),
                                    FileUtils.join(
                                            scope.getGlobalScope().getIntermediatesDir(),
                                            "symbols",
                                            atom.getAtomName(),
                                            variantData.getVariantConfiguration().getDirName()));
                        }
                        return textSymbolDirs;
                    });

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "manifestFiles",
                    () -> {
                        Map<String, File> manifestFiles = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            manifestFiles.put(atom.getAtomName(), atom.getManifest());
                        }
                        return manifestFiles;
                    });

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "resDirs",
                    () -> {
                        Map<String, File> resDirs = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            resDirs.put(atom.getAtomName(), atom.getResFolder());
                        }
                        return resDirs;
                    });

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "packageOutputFiles",
                    () -> {
                        Map<String, File> packageFiles = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            packageFiles.put(
                                    atom.getAtomName(),
                                    scope.getProcessResourcePackageOutputFile(atom));
                        }
                        return packageFiles;
                    });

            ConventionMappingHelper.map(
                    processAtomsResources,
                    "mergeBlameLogFolders",
                    () -> {
                        Map<String, File> blameLogFolders = Maps.newHashMap();
                        for (AtomDependency atom : config.getFlatAndroidAtomsDependencies()) {
                            blameLogFolders.put(
                                    atom.getAtomName(),
                                    scope.getVariantScope().getResourceBlameLogDir(atom));
                        }
                        return blameLogFolders;
                    });

            processAtomsResources.setBaseAtomPackage(
                    () -> {
                        AtomDependency baseAtom =
                                scope.getVariantScope()
                                        .getVariantConfiguration()
                                        .getPackageDependencies()
                                        .getBaseAtom();
                        return baseAtom == null ? null : baseAtom.getResourcePackage();
                    });
        }

        private Map<String, List<AndroidDependency>> computeFlatAtomLibraryMap() {
            DependencyContainer packageDependencies =
                    scope.getVariantScope().getVariantConfiguration().getPackageDependencies();
            ImmutableList<DependencyNode> directNodes = packageDependencies.getDependencies();

            Map<String, List<AndroidDependency>> outMap = Maps.newHashMap();

            // filter this to only the direct atoms.
            List<DependencyNode> atomNodes =
                    directNodes
                            .stream()
                            .filter(node -> node.getNodeType() == DependencyNode.NodeType.ATOM)
                            .collect(Collectors.toList());
            computeFlatAtomLibraryMap(atomNodes, outMap);
            return outMap;
        }

        private void computeFlatAtomLibraryMap(
                List<DependencyNode> atomNodes, Map<String, List<AndroidDependency>> outMap) {
            DependencyContainer packageDependencies =
                    scope.getVariantScope().getVariantConfiguration().getPackageDependencies();

            for (DependencyNode node : atomNodes) {
                AtomDependency atomDependency =
                        (AtomDependency)
                                packageDependencies.getDependencyMap().get(node.getAddress());

                if (outMap.keySet().contains(atomDependency.getAtomName())) {
                    continue;
                }

                final List<DependencyNode> androidNodes =
                        node.getDependencies()
                                .stream()
                                .filter(n -> n.getNodeType() == DependencyNode.NodeType.ATOM)
                                .collect(Collectors.toList());
                computeFlatAtomLibraryMap(androidNodes, outMap);

                outMap.put(atomDependency.getAtomName(), computeFlatAtomLibraryMap(node));
            }
        }

        private List<AndroidDependency> computeFlatAtomLibraryMap(DependencyNode atomNode) {
            List<AndroidDependency> flatAndroidLibraries = Lists.newArrayList();
            final List<DependencyNode> androidNodes =
                    atomNode.getDependencies()
                            .stream()
                            .filter(node -> node.getNodeType() == DependencyNode.NodeType.ANDROID)
                            .collect(Collectors.toList());

            computeFlatAtomLibraryMap(androidNodes, flatAndroidLibraries);
            return flatAndroidLibraries;
        }

        private void computeFlatAtomLibraryMap(
                List<DependencyNode> androidDependencies,
                List<AndroidDependency> outFlatAndroidDependencies) {
            DependencyContainer packageDependencies =
                    scope.getVariantScope().getVariantConfiguration().getPackageDependencies();

            for (DependencyNode node : androidDependencies) {
                AndroidDependency androidDependency =
                        (AndroidDependency)
                                packageDependencies.getDependencyMap().get(node.getAddress());

                if (outFlatAndroidDependencies.contains(androidDependency)) {
                    continue;
                }

                final List<DependencyNode> androidNodes =
                        node.getDependencies()
                                .stream()
                                .filter(n -> n.getNodeType() == DependencyNode.NodeType.ANDROID)
                                .collect(Collectors.toList());

                computeFlatAtomLibraryMap(androidNodes, outFlatAndroidDependencies);
                outFlatAndroidDependencies.add(androidDependency);
            }
        }
    }
}
