/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MERGED_ASSETS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates the {@code test_config.properties} file that is put on the classpath for running unit
 * tests.
 */
public class GenerateTestConfig extends DefaultTask {

    Path resourcesDirectory;
    Path assetsDirectory;
    Path sdkHome;
    Path generatedJavaResourcesDirectory;
    SplitScope splitScope;
    FileCollection manifests;

    @InputFiles
    FileCollection getManifests() {
        return manifests;
    }

    @TaskAction
    public void generateTestConfig() throws IOException {
        checkNotNull(resourcesDirectory);
        checkNotNull(assetsDirectory);
        checkNotNull(sdkHome);
        generateTestConfigForOutput(
                SplitScope.getOutput(
                        SplitScope.load(
                                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, manifests),
                        TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                        splitScope.getMainSplit()));
    }

    @VisibleForTesting
    void generateTestConfigForOutput(SplitScope.SplitOutput splitOutput) throws IOException {
        checkNotNull(splitOutput);

        Properties properties = new Properties();
        properties.put("android_sdk_home", sdkHome.toAbsolutePath().toString());
        properties.put("android_merged_resources", resourcesDirectory.toAbsolutePath().toString());
        properties.put("android_merged_assets", assetsDirectory.toAbsolutePath().toString());
        properties.put("android_merged_manifest", splitOutput.getOutputFile().toPath().toString());

        Path output = getOutputPath();
        Files.createDirectories(output.getParent());

        try (Writer writer = Files.newBufferedWriter(output)) {
            properties.store(writer, null);
        }
    }

    private Path getOutputPath() {
        return generatedJavaResourcesDirectory
                .resolve("com")
                .resolve("android")
                .resolve("tools")
                .resolve("test_config.properties");
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getResourcesDirectory() {
        return resourcesDirectory.toString();
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getAssetsDirectory() {
        return assetsDirectory.toString();
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getSdkHome() {
        return sdkHome.toString();
    }

    @OutputFile
    public File getOutputFile() {
        return getOutputPath().toFile();
    }

    public static class ConfigAction implements TaskConfigAction<GenerateTestConfig> {

        @NonNull private final VariantScope scope;
        private VariantScope testedScope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
            this.testedScope =
                    Preconditions.checkNotNull(
                                    scope.getTestedVariantData(), "Not a unit test variant.")
                            .getScope();
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("generate", "Config");
        }

        @NonNull
        @Override
        public Class<GenerateTestConfig> getType() {
            return GenerateTestConfig.class;
        }

        @Override
        public void execute(@NonNull GenerateTestConfig task) {
            // get the file collection that this task consumes.
            FileCollection assets = testedScope.getOutputs(MERGED_ASSETS);

            // we don't actually consume the task, only the path, so make a manual dependency
            // on the filecollections.
            task.dependsOn(assets);

            // then record the path for actual inputs.
            task.generatedJavaResourcesDirectory = scope.getGeneratedJavaResourcesDir().toPath();
            task.resourcesDirectory = testedScope.getMergeResourcesOutputDir().toPath();
            task.assetsDirectory = assets.getSingleFile().toPath();
            task.manifests =
                    testedScope.getOutputs(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS);
            task.splitScope = testedScope.getSplitScope();
            task.sdkHome =
                    Paths.get(scope.getGlobalScope().getAndroidBuilder().getTarget().getLocation());
        }
    }
}
