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
import com.android.build.OutputFile;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.scope.SplitFactory;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.google.common.base.CharMatcher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates all metadata (like AndroidManifest.xml) necessary for a ABI dimension split APK.
 */
public class GenerateSplitAbiRes extends BaseTask {

    private String applicationId;
    private String outputBaseName;

    // these are the default values set in the variant's configuration, although they
    // are not directly use in this task, they will be used when versionName and versionCode
    // is not changed by the user's scripts. Therefore, if those values change, this task
    // should be considered out of date.
    private String versionName;
    private int versionCode;
    private AaptGeneration aaptGeneration;

    private Set<String> splits;
    private File outputDirectory;
    private boolean debuggable;
    private AaptOptions aaptOptions;
    private SplitScope splitScope;
    private SplitFactory splitFactory;
    private VariantType variantType;
    private VariantScope variantScope;
    private FileCache fileCache;

    @Input
    public String getApplicationId() {
        return applicationId;
    }

    @Input
    public int getVersionCode() {
        return versionCode;
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName;
    }

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
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

    @TaskAction
    protected void doFullTaskAction() throws IOException, InterruptedException, ProcessException {

        splitScope.deleteAllEntries(VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES);
        for (String split : getSplits()) {
            File resPackageFile = getOutputFileForSplit(split);

            ApkData abiApkData =
                    splitFactory.addConfigurationSplit(
                            OutputFile.FilterType.ABI, split, resPackageFile.getName());
            abiApkData.setVersionCode(variantScope.getVariantConfiguration().getVersionCode());
            abiApkData.setVersionName(variantScope.getVariantConfiguration().getVersionName());

            // call user's script for the newly discovered ABI pure split.
            if (variantScope.getVariantData().variantOutputFactory != null) {
                variantScope.getVariantData().variantOutputFactory.create(abiApkData);
            }

            File tmpDirectory = new File(outputDirectory, getOutputBaseName());
            FileUtils.mkdirs(tmpDirectory);

            File tmpFile = new File(tmpDirectory, "AndroidManifest.xml");

            String versionNameToUse = abiApkData.getVersionName();
            if (versionNameToUse == null) {
                versionNameToUse = String.valueOf(abiApkData.getVersionCode());
            }

            try (OutputStreamWriter fileWriter =
                         new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8")) {
                // Split name can only contains 0-9, a-z, A-Z, '.' and '_'.  Replace all other
                // characters with underscore.
                String splitName = CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'))
                        .or(CharMatcher.is('_'))
                        .or(CharMatcher.is('.'))
                        .negate()
                        .replaceFrom(split + "_" + getOutputBaseName(), '_');
                fileWriter.append(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "      package=\""
                                + applicationId
                                + "\"\n"
                                + "      android:versionCode=\""
                                + abiApkData.getVersionCode()
                                + "\"\n"
                                + "      android:versionName=\""
                                + versionNameToUse
                                + "\"\n"
                                + "      split=\"lib_"
                                + splitName
                                + "\">\n"
                                + "       <uses-sdk android:minSdkVersion=\"21\"/>\n"
                                + "</manifest> ");
                fileWriter.flush();
            }

            AndroidBuilder builder = getBuilder();
            Aapt aapt =
                    AaptGradleFactory.make(
                            aaptGeneration,
                            builder,
                            new LoggedProcessOutputHandler(
                                    new AaptGradleFactory.FilteringLogger(builder.getLogger())),
                            fileCache,
                            true,
                            FileUtils.mkdirs(
                                    new File(
                                            variantScope.getIncrementalDir(getName()),
                                            "aapt-temp")),
                            variantScope
                                    .getGlobalScope()
                                    .getExtension()
                                    .getAaptOptions()
                                    .getCruncherProcesses());
            AaptPackageConfig.Builder aaptConfig = new AaptPackageConfig.Builder();
            aaptConfig
                    .setManifestFile(tmpFile)
                    .setOptions(DslAdaptersKt.convert(aaptOptions))
                    .setDebuggable(debuggable)
                    .setResourceOutputApk(resPackageFile)
                    .setVariantType(variantType);

            getBuilder().processResources(aapt, aaptConfig);
            splitScope.addOutputForSplit(
                    VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES,
                    abiApkData,
                    resPackageFile);
        }

        splitScope.save(VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES, outputDirectory);
    }

    // FIX ME : this calculation should move to SplitScope.Split interface
    private File getOutputFileForSplit(final String split) {
        return new File(outputDirectory, "resources-" + getOutputBaseName() + "-" + split + ".ap_");
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<GenerateSplitAbiRes> {

        @NonNull private final VariantScope scope;
        @NonNull private final File outputDirectory;

        public ConfigAction(@NonNull VariantScope scope, @NonNull File outputDirectory) {
            this.scope = scope;
            this.outputDirectory = outputDirectory;
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

            generateSplitAbiRes.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            generateSplitAbiRes.setVariantName(config.getFullName());

            // not used directly, but considered as input for the task.
            generateSplitAbiRes.versionCode = config.getVersionCode();
            generateSplitAbiRes.versionName = config.getVersionName();
            generateSplitAbiRes.aaptGeneration =
                    AaptGeneration.fromProjectOptions(scope.getGlobalScope().getProjectOptions());
            generateSplitAbiRes.fileCache = scope.getGlobalScope().getBuildCache();

            generateSplitAbiRes.variantScope = scope;
            generateSplitAbiRes.variantType = config.getType();
            generateSplitAbiRes.outputDirectory = outputDirectory;
            generateSplitAbiRes.splits =
                    AbiSplitOptions.getAbiFilters(
                            scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
            generateSplitAbiRes.outputBaseName = config.getBaseName();
            generateSplitAbiRes.applicationId = config.getApplicationId();
            generateSplitAbiRes.debuggable = config.getBuildType().isDebuggable();
            generateSplitAbiRes.aaptOptions =
                    scope.getGlobalScope().getExtension().getAaptOptions();
            generateSplitAbiRes.splitScope = scope.getSplitScope();
            generateSplitAbiRes.splitFactory = scope.getVariantData().getSplitFactory();
        }
    }
}
