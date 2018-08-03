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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_BASE_MODULE_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

/** Generates all metadata (like AndroidManifest.xml) necessary for a ABI dimension split APK. */
public class GenerateSplitAbiRes extends AndroidBuilderTask {

    @NonNull private final WorkerExecutorFacade workers;

    @Inject
    public GenerateSplitAbiRes(@NonNull WorkerExecutor workerExecutor) {
        this.workers = Workers.INSTANCE.getWorker(workerExecutor);
    }

    private Supplier<String> applicationId;
    private String outputBaseName;

    // these are the default values set in the variant's configuration, although they
    // are not directly use in this task, they will be used when versionName and versionCode
    // is not changed by the user's scripts. Therefore, if those values change, this task
    // should be considered out of date.
    private Supplier<String> versionName;
    private IntSupplier versionCode;

    private Set<String> splits;
    private File outputDirectory;
    private boolean debuggable;
    private AaptOptions aaptOptions;
    private OutputFactory outputFactory;
    private VariantType variantType;
    private VariantScope variantScope;
    @VisibleForTesting @Nullable Supplier<String> featureNameSupplier;
    @Nullable private FileCollection applicationIdOverride;
    @Nullable private FileCollection aapt2FromMaven;

    @Input
    public String getApplicationId() {
        return applicationId.get();
    }

    @Input
    public int getVersionCode() {
        return versionCode.getAsInt();
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName.get();
    }

    @Input
    public String getOutputBaseName() {
        return outputBaseName;
    }

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    @Input
    @Optional
    @Nullable
    public String getFeatureName() {
        return featureNameSupplier != null ? featureNameSupplier.get() : null;
    }

    @InputFiles
    @Optional
    @Nullable
    public FileCollection getApplicationIdOverride() {
        return applicationIdOverride;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    @Nullable
    public FileCollection getAapt2FromMaven() {
        return aapt2FromMaven;
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException, ProcessException {

        ImmutableList.Builder<BuildOutput> buildOutputs = ImmutableList.builder();
        try (WorkerExecutorFacade workerExecutor = workers) {
            for (String split : getSplits()) {
                File resPackageFile = getOutputFileForSplit(split);

            ApkData abiApkData =
                    outputFactory.addConfigurationSplit(
                            OutputFile.FilterType.ABI, split, resPackageFile.getName());
            abiApkData.setVersionCode(
                    variantScope.getVariantConfiguration().getVersionCodeSerializableSupplier());
            abiApkData.setVersionName(
                    variantScope.getVariantConfiguration().getVersionNameSerializableSupplier());

                // call user's script for the newly discovered ABI pure split.
                if (variantScope.getVariantData().variantOutputFactory != null) {
                    variantScope.getVariantData().variantOutputFactory.create(abiApkData);
                }

                File manifestFile = generateSplitManifest(split, abiApkData);

                AndroidBuilder builder = getBuilder();
                AaptPackageConfig aaptConfig =
                        new AaptPackageConfig.Builder()
                                .setManifestFile(manifestFile)
                                .setOptions(DslAdaptersKt.convert(aaptOptions))
                                .setDebuggable(debuggable)
                                .setResourceOutputApk(resPackageFile)
                                .setVariantType(variantType)
                                .setAndroidTarget(builder.getTarget())
                                .build();

                Aapt2ServiceKey aapt2ServiceKey =
                        Aapt2DaemonManagerService.registerAaptService(
                                aapt2FromMaven, builder.getBuildToolInfo(), builder.getLogger());
                Aapt2ProcessResourcesRunnable.Params params =
                        new Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey, aaptConfig);
                workerExecutor.submit(Aapt2ProcessResourcesRunnable.class, params);

                buildOutputs.add(
                        new BuildOutput(
                                InternalArtifactType.ABI_PROCESSED_SPLIT_RES,
                                abiApkData,
                                resPackageFile));
            }
        }
        new BuildElements(buildOutputs.build()).save(outputDirectory);
    }

    @VisibleForTesting
    File generateSplitManifest(String split, ApkData abiApkData) throws IOException {
        // Split name can only contains 0-9, a-z, A-Z, '.' and '_'.  Replace all other
        // characters with underscore.
        CharMatcher charMatcher =
                CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'))
                        .or(CharMatcher.is('_'))
                        .or(CharMatcher.is('.'))
                        .negate();

        String featureName = getFeatureName();

        String encodedSplitName =
                (featureName != null ? featureName + "." : "")
                        + "config."
                        + charMatcher.replaceFrom(split, '_');

        File tmpDirectory = new File(outputDirectory, split);
        FileUtils.mkdirs(tmpDirectory);

        File tmpFile = new File(tmpDirectory, "AndroidManifest.xml");

        String versionNameToUse = abiApkData.getVersionName();
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(abiApkData.getVersionCode());
        }

        // Override the applicationId for features.
        String manifestAppId;
        if (applicationIdOverride != null && !applicationIdOverride.isEmpty()) {
            manifestAppId =
                    ModuleMetadata.load(applicationIdOverride.getSingleFile()).getApplicationId();
        } else {
            manifestAppId = applicationId.get();
        }

        try (OutputStreamWriter fileWriter =
                new OutputStreamWriter(
                        new BufferedOutputStream(new FileOutputStream(tmpFile)), "UTF-8")) {

            fileWriter.append(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "      package=\""
                            + manifestAppId
                            + "\"\n"
                            + "      android:versionCode=\""
                            + abiApkData.getVersionCode()
                            + "\"\n"
                            + "      android:versionName=\""
                            + versionNameToUse
                            + "\"\n");

            if (featureName != null) {
                fileWriter.append("      configForSplit=\"" + featureName + "\"\n");
            }

            fileWriter.append(
                    "      split=\""
                            + encodedSplitName
                            + "\"\n"
                            + "      targetABI=\""
                            + split
                            + "\">\n"
                            + "       <uses-sdk android:minSdkVersion=\"21\"/>\n"
                            + "</manifest> ");
            fileWriter.flush();
        }
        return tmpFile;
    }

    // FIX ME : this calculation should move to SplitScope.Split interface
    private File getOutputFileForSplit(final String split) {
        return new File(outputDirectory, "resources-" + getOutputBaseName() + "-" + split + ".ap_");
    }

    // ----- CreationAction -----

    public static class CreationAction extends TaskCreationAction<GenerateSplitAbiRes> {

        @NonNull private final VariantScope scope;
        @NonNull private final FeatureSetMetadata.SupplierProvider provider;

        public CreationAction(@NonNull VariantScope scope) {
            this(scope, FeatureSetMetadata.getInstance());
        }

        @VisibleForTesting
        CreationAction(
                @NonNull VariantScope scope,
                @NonNull FeatureSetMetadata.SupplierProvider provider) {
            this.scope = scope;
            this.provider = provider;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("generate", "SplitAbiRes");
        }

        @Override
        @NonNull
        public Class<GenerateSplitAbiRes> getType() {
            return GenerateSplitAbiRes.class;
        }

        @Override
        public void execute(@NonNull GenerateSplitAbiRes generateSplitAbiRes) {
            final VariantConfiguration config = scope.getVariantConfiguration();
            VariantType variantType = config.getType();

            generateSplitAbiRes.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            generateSplitAbiRes.setVariantName(config.getFullName());

            if (variantType.isFeatureSplit()) {
                generateSplitAbiRes.featureNameSupplier =
                        provider.getFeatureNameSupplierForTask(scope, generateSplitAbiRes);
            }

            // not used directly, but considered as input for the task.
            generateSplitAbiRes.versionCode = config::getVersionCode;
            generateSplitAbiRes.versionName = config::getVersionName;

            generateSplitAbiRes.variantScope = scope;
            generateSplitAbiRes.variantType = variantType;
            generateSplitAbiRes.outputDirectory = scope.getArtifacts().appendArtifact(
                    InternalArtifactType.ABI_PROCESSED_SPLIT_RES,
                    generateSplitAbiRes,
                    "out");
            generateSplitAbiRes.splits =
                    AbiSplitOptions.getAbiFilters(
                            scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
            generateSplitAbiRes.outputBaseName = config.getBaseName();
            generateSplitAbiRes.applicationId = config::getApplicationId;
            generateSplitAbiRes.debuggable = config.getBuildType().isDebuggable();
            generateSplitAbiRes.aaptOptions =
                    scope.getGlobalScope().getExtension().getAaptOptions();
            generateSplitAbiRes.outputFactory = scope.getVariantData().getOutputFactory();
            generateSplitAbiRes.aapt2FromMaven =
                    Aapt2MavenUtils.getAapt2FromMaven(scope.getGlobalScope());

            // if BASE_FEATURE get the app ID from the app module
            if (variantType.isBaseModule() && variantType.isHybrid()) {
                generateSplitAbiRes.applicationIdOverride =
                        scope.getArtifactFileCollection(
                                METADATA_VALUES, MODULE, METADATA_BASE_MODULE_DECLARATION);
            } else if (variantType.isFeatureSplit()) {
                // if feature split, get it from the base module
                generateSplitAbiRes.applicationIdOverride =
                        scope.getArtifactFileCollection(
                                COMPILE_CLASSPATH, MODULE, FEATURE_APPLICATION_ID_DECLARATION);
            }
        }
    }
}
