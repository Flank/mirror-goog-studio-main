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
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.CharMatcher;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Generates all metadata (like AndroidManifest.xml) necessary for a ABI dimension split APK.
 */
@ParallelizableTask
public class GenerateSplitAbiRes extends BaseTask {

    private String applicationId;

    private String outputBaseName;

    private Set<String> splits;

    private File outputDirectory;

    private boolean debuggable;

    private AaptOptions aaptOptions;

    private ApkVariantOutputData variantOutputData;

    @SuppressWarnings("unused") // Synthetic task output
    @OutputFiles
    public List<File> getOutputFiles() {
        return getSplits().stream()
                .map(this::getOutputFileForSplit).collect(Collectors.toList());
    }

    @TaskAction
    protected void doFullTaskAction() throws IOException, InterruptedException, ProcessException {

        for (String split : getSplits()) {
            String resPackageFileName = getOutputFileForSplit(split).getAbsolutePath();

            File tmpDirectory = new File(getOutputDirectory(), getOutputBaseName());
            tmpDirectory.mkdirs();

            File tmpFile = new File(tmpDirectory, "AndroidManifest.xml");

            String versionNameToUse = getVersionName();
            if (versionNameToUse == null) {
                versionNameToUse = String.valueOf(getVersionCode());
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
                fileWriter.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "      package=\"" + getApplicationId() + "\"\n"
                        + "      android:versionCode=\"" + getVersionCode() + "\"\n"
                        + "      android:versionName=\"" + versionNameToUse + "\"\n"
                        + "      split=\"lib_" + splitName + "\">\n"
                        + "       <uses-sdk android:minSdkVersion=\"21\"/>\n" + "</manifest> ");
                fileWriter.flush();
            }

            Aapt aapt =
                    AaptGradleFactory.make(
                            getBuilder(),
                            variantOutputData.getScope().getVariantScope());
            AaptPackageConfig.Builder aaptConfig = new AaptPackageConfig.Builder();
            aaptConfig
                    .setManifestFile(tmpFile)
                    .setOptions(getAaptOptions())
                    .setDebuggable(isDebuggable())
                    .setResourceOutputApk(new File(resPackageFileName))
                    .setVariantType(
                            variantOutputData.getScope()
                                    .getVariantScope().getVariantConfiguration().getType());

            getBuilder().processResources(
                    aapt,
                    aaptConfig,
                    false /* enforceUniquePackageName */);
        }
    }

    private File getOutputFileForSplit(final String split) {
        return new File(getOutputDirectory(),
                "resources-" + getOutputBaseName() + "-" + split + ".ap_");
    }

    @Input
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    @Input
    public int getVersionCode() {
        return variantOutputData.getVersionCode();
    }

    @Input
    @Optional
    public String getVersionName() {
        return variantOutputData.getVersionName();
    }

    @Input
    public String getOutputBaseName() {
        return outputBaseName;
    }

    public void setOutputBaseName(String outputBaseName) {
        this.outputBaseName = outputBaseName;
    }

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    public void setSplits(Set<String> splits) {
        this.splits = splits;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    public void setAaptOptions(AaptOptions aaptOptions) {
        this.aaptOptions = aaptOptions;
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<GenerateSplitAbiRes> {

        private VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
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
            Set<String> filters = AbiSplitOptions.getAbiFilters(
                    scope.getGlobalScope().getExtension().getSplits().getAbiFilters());

            generateSplitAbiRes.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            generateSplitAbiRes.setVariantName(config.getFullName());

            generateSplitAbiRes.setOutputDirectory(scope.getGenerateSplitAbiResOutputDirectory());
            generateSplitAbiRes.setSplits(filters);
            generateSplitAbiRes.setOutputBaseName(config.getBaseName());
            generateSplitAbiRes.setApplicationId(config.getApplicationId());
            generateSplitAbiRes.variantOutputData =
                    (ApkVariantOutputData) scope.getVariantData().getOutputs().get(0);
            ConventionMappingHelper.map(generateSplitAbiRes, "debuggable", new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return config.getBuildType().isDebuggable();
                }
            });
            ConventionMappingHelper.map(generateSplitAbiRes, "aaptOptions",
                    new Callable<AaptOptions>() {
                        @Override
                        public AaptOptions call() throws Exception {
                            return scope.getGlobalScope().getExtension().getAaptOptions();
                        }
                    });
        }
    }
}
