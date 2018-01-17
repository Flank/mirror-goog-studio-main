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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.builder.model.ApiVersion;
import com.android.ide.common.build.ApkData;
import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

/**
 * Task to generate a manifest snippet that just contains a compatible-screens node with the given
 * density and the given list of screen sizes.
 */
@CacheableTask
public class CompatibleScreensManifest extends AndroidVariantTask {

    private Set<String> screenSizes;
    private File outputFolder;
    private OutputScope outputScope;
    private Supplier<String> minSdkVersion;

    @Input
    public Set<String> getScreenSizes() {
        return screenSizes;
    }

    public void setScreenSizes(Set<String> screenSizes) {
        this.screenSizes = screenSizes;
    }

    @Input
    List<ApkData> getSplits() {
        return outputScope.getApkDatas();
    }

    @Input
    @Optional
    String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    void setMinSdkVersion(Supplier<String> minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    @OutputDirectory
    File getOutputFolder() {
        return outputFolder;
    }

    void setOutputFolder(File outputFolder) {
        this.outputFolder = outputFolder;
    }

    @TaskAction
    public void generateAll() throws IOException {

        new BuildElements(
                        outputScope
                                .getApkDatas()
                                .stream()
                                .map(
                                        apkInfo -> {
                                            File generatedManifest = generate(apkInfo);
                                            return generatedManifest != null
                                                    ? new BuildOutput(
                                                            VariantScope.TaskOutputType
                                                                    .COMPATIBLE_SCREEN_MANIFEST,
                                                            apkInfo,
                                                            generatedManifest)
                                                    : null;
                                        })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()))
                .save(outputFolder);
    }

    @Nullable
    public File generate(ApkData apkData) {
        FilterData densityFilter =
                apkData.getFilter(com.android.build.OutputFile.FilterType.DENSITY);
        if (densityFilter == null) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                .append("    package=\"${packageName}\">\n")
                .append("\n");
        if (minSdkVersion.get() != null) {
            content.append("    <uses-sdk android:minSdkVersion=\"")
                    .append(minSdkVersion.get())
                    .append("\"/>\n");
        }
        content.append("    <compatible-screens>\n");

        // convert unsupported values to numbers.
        String density = convert(densityFilter.getIdentifier(), Density.XXHIGH, Density.XXXHIGH);

        for (String size : getScreenSizes()) {
            content.append(
                    "        <screen android:screenSize=\"").append(size).append("\" "
                    + "android:screenDensity=\"").append(density).append("\" />\n");
        }

        content.append(
                "    </compatible-screens>\n" +
                "</manifest>");


        File splitFolder = new File(outputFolder, apkData.getDirName());
        FileUtils.mkdirs(splitFolder);
        File manifestFile = new File(splitFolder, SdkConstants.ANDROID_MANIFEST_XML);

        try {
            Files.write(content.toString(), manifestFile, Charsets.UTF_8);
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
        return manifestFile;
    }

    private static String convert(@NonNull String density, @NonNull Density... densitiesToConvert) {
        for (Density densityToConvert : densitiesToConvert) {
            if (densityToConvert.getResourceValue().equals(density)) {
                return Integer.toString(densityToConvert.getDpiValue());
            }
        }
        return density;
    }

    public static class ConfigAction implements TaskConfigAction<CompatibleScreensManifest> {

        @NonNull private final VariantScope scope;
        @NonNull private final Set<String> screenSizes;

        public ConfigAction(@NonNull VariantScope scope, @NonNull Set<String> screenSizes) {
            this.scope = scope;
            this.screenSizes = screenSizes;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("create", "CompatibleScreenManifests");
        }

        @NonNull
        @Override
        public Class<CompatibleScreensManifest> getType() {
            return CompatibleScreensManifest.class;
        }

        @Override
        public void execute(@NonNull CompatibleScreensManifest csmTask) {
            csmTask.outputScope = scope.getOutputScope();
            csmTask.setVariantName(scope.getFullVariantName());
            csmTask.setScreenSizes(screenSizes);
            csmTask.setOutputFolder(scope.getCompatibleScreensManifestDirectory());
            GradleVariantConfiguration config = scope.getVariantConfiguration();
            csmTask.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion minSdk = config.getMergedFlavor().getMinSdkVersion();
                                return minSdk == null ? null : minSdk.getApiString();
                            });
        }
    }
}
