/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.incremental.BuildContext;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.manifmerger.ManifestProvider;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;
import org.xml.sax.SAXException;

/**
 * A task that processes the manifest for test modules and tests in androidTest.
 *
 * <p>For both test modules and tests in androidTest process is the same, expect
 * for how the tested application id is extracted.</p>
 *
 * <p>Tests in androidTest get that info form the
 * {@link VariantConfiguration#getTestedApplicationId()}, while the test modules get the info from
 * the published intermediate manifest with type {@link AndroidArtifacts#TYPE_METADATA}
 * of the tested app.</p>
 */
@ParallelizableTask
public class ProcessTestManifest extends ManifestProcessorTask {

    @Nullable
    private FileCollection testTargetMetadata;

    @Nullable
    private File testManifestFile;

    private File tmpDir;
    private String testApplicationId;
    private String minSdkVersion;
    private String targetSdkVersion;
    private String testedApplicationId;
    private String instrumentationRunner;
    private Boolean handleProfiling;
    private Boolean functionalTest;
    private Map<String, Object> placeholdersValues;

    private ArtifactCollection manifests;
    // FIXME find a better way to inject the tested library's content into the main ArtifactCollection
    private FileCollection testedManifest;

    @Nullable
    private String testLabel;

    @Override
    protected void doFullTaskAction() throws IOException {
        if (testApplicationId == null && testTargetMetadata == null) {
            throw new RuntimeException("testApplicationId and testTargetMetadata are null");
        }
        String testedApplicationId = this.testedApplicationId;
        if (testTargetMetadata != null) {
            BuildContext buildContext = new BuildContext();
            try {
                buildContext.loadFromXmlFile(testTargetMetadata.getSingleFile());
            } catch (ParserConfigurationException  | SAXException e) {
                getLogger().error("Error while reading build-info.xml at " +
                    testTargetMetadata.getSingleFile().getAbsolutePath(), e);
                throw new IOException(e);
            }
            testedApplicationId = buildContext.getPackageId();
        }

        getBuilder().mergeManifestsForTestVariant(
                getTestApplicationId(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                testedApplicationId,
                getInstrumentationRunner(),
                getHandleProfiling(),
                getFunctionalTest(),
                getTestLabel(),
                getTestManifestFile(),
                computeProviders(),
                getPlaceholdersValues(),
                getManifestOutputFile(),
                getTmpDir());
    }

    @InputFile
    @Optional
    public File getTestManifestFile() {
        return testManifestFile;
    }

    public void setTestManifestFile(File testManifestFile) {
        this.testManifestFile = testManifestFile;
    }

    public File getTmpDir() {
        return tmpDir;
    }

    public void setTmpDir(File tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Input
    public String getTestApplicationId() {
        return testApplicationId;
    }

    public void setTestApplicationId(String testApplicationId) {
        this.testApplicationId = testApplicationId;
    }

    @Input
    public String getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(String minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    @Input
    public String getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(String targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    @Input
    @Optional
    public String getTestedApplicationId() {
        return testedApplicationId;
    }

    public void setTestedApplicationId(String testedApplicationId) {
        this.testedApplicationId = testedApplicationId;
    }

    @Input
    public String getInstrumentationRunner() {
        return instrumentationRunner;
    }

    public void setInstrumentationRunner(String instrumentationRunner) {
        this.instrumentationRunner = instrumentationRunner;
    }

    @Input
    public Boolean getHandleProfiling() {
        return handleProfiling;
    }

    public void setHandleProfiling(Boolean handleProfiling) {
        this.handleProfiling = handleProfiling;
    }

    @Input
    public Boolean getFunctionalTest() {
        return functionalTest;
    }

    public void setFunctionalTest(Boolean functionalTest) {
        this.functionalTest = functionalTest;
    }

    @Input
    @Optional
    public String getTestLabel() {
        return testLabel;
    }

    public void setTestLabel(String testLabel) {
        this.testLabel = testLabel;
    }

    @Input
    public Map<String, Object> getPlaceholdersValues() {
        return placeholdersValues;
    }

    public void setPlaceholdersValues(
            Map<String, Object> placeholdersValues) {
        this.placeholdersValues = placeholdersValues;
    }

    @InputFiles
    @Optional
    @Nullable
    public FileCollection getTestTargetMetadata() {
        return testTargetMetadata;
    }

    public void setTestTargetMetadata(FileCollection testTargetMetadata) {
        this.testTargetMetadata = testTargetMetadata;
    }

    /**
     * Compute the final list of providers based on the manifest file collection.
     * @return the list of providers.
     */
    public List<ManifestProvider> computeProviders() {
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size());

        for (ResolvedArtifactResult artifact : artifacts) {
            providers.add(new MergeManifests.ConfigAction.ManifestProviderImpl(
                    artifact.getFile(),
                    MergeManifests.getArtifactName(artifact)));
        }

        //add the tested manifest.
        if (testedManifest != null) {
            providers.add(new MergeManifests.ConfigAction.ManifestProviderImpl(
                    testedManifest.getSingleFile(),
                    "__tested_library__"));
        }

        return providers;
    }

    @InputFiles
    @Optional
    public FileCollection getTestedManifest() {
        return testedManifest;
    }

    @InputFiles
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    public static class ConfigAction implements TaskConfigAction<ProcessTestManifest> {

        @NonNull
        private final VariantScope scope;

        @Nullable
        private final FileCollection testTargetMetadata;

        public ConfigAction(
                @NonNull VariantScope scope,
                @Nullable FileCollection testTargetMetadata){
            this.scope = scope;
            this.testTargetMetadata = testTargetMetadata;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<ProcessTestManifest> getType() {
            return ProcessTestManifest.class;
        }

        @Override
        public void execute(@NonNull final ProcessTestManifest processTestManifestTask) {

            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    scope.getVariantConfiguration();

            processTestManifestTask.setTestManifestFile(config.getMainManifest());

            processTestManifestTask.setTmpDir(FileUtils.join(
                    scope.getGlobalScope().getIntermediatesDir(),
                    "tmp",
                    "manifest",
                    scope.getDirName()));

            // get single output for now.
            final BaseVariantOutputData variantOutputData =
                    scope.getVariantData().getOutputs().get(0);

            variantOutputData.manifestProcessorTask = processTestManifestTask;

            processTestManifestTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processTestManifestTask.setVariantName(config.getFullName());
            // will only be used if testTargetMetadata is null.
            processTestManifestTask.setTestApplicationId(config.getTestApplicationId());

            // only add the resources for tested libraries.
            processTestManifestTask.testedManifest = scope.getTestedArtifact(
                    AndroidArtifacts.TYPE_MANIFEST,
                    VariantType.LIBRARY);


            ConventionMappingHelper.map(processTestManifestTask, "minSdkVersion", () -> {
                        if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                            return scope.getGlobalScope().getAndroidBuilder()
                                    .getTargetCodename();
                        }
                        return config.getMinSdkVersion().getApiString();
                    });

            ConventionMappingHelper.map(processTestManifestTask, "targetSdkVersion", () -> {
                        if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                            return scope.getGlobalScope().getAndroidBuilder()
                                    .getTargetCodename();
                        }

                        return config.getTargetSdkVersion().getApiString();
                    });

            processTestManifestTask.testTargetMetadata = testTargetMetadata;

            ConventionMappingHelper.map(
                    processTestManifestTask, "instrumentationRunner",
                    config::getInstrumentationRunner);
            ConventionMappingHelper.map(
                    processTestManifestTask, "handleProfiling", config::getHandleProfiling);
            ConventionMappingHelper.map(
                    processTestManifestTask, "functionalTest", config::getFunctionalTest);
            ConventionMappingHelper.map(
                    processTestManifestTask, "testLabel", config::getTestLabel);

            processTestManifestTask.manifests = scope.getArtifactCollection(
                    AndroidArtifacts.ConfigType.PACKAGE,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.MANIFEST);

            processTestManifestTask.setManifestOutputFile(
                    variantOutputData.getScope().getManifestOutputFile());

            ConventionMappingHelper.map(
                    processTestManifestTask, "placeholdersValues", config::getManifestPlaceholders);
        }
    }
}
