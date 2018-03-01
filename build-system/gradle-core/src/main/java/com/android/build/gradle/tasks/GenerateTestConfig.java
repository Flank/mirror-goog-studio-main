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

import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NOT_COMPILED_RES;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.ide.common.build.ApkInfo;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
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
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates the {@code test_config.properties} file that is put on the classpath for running unit
 * tests.
 *
 * <p>See DSL documentation in {@link TestOptions.UnitTestOptions#isIncludeAndroidResources()}
 */
public class GenerateTestConfig extends DefaultTask {

    FileCollection resourcesDirectory;
    BuildableArtifact assets;
    Path sdkHome;
    File generatedJavaResourcesDirectory;
    ApkInfo mainApkInfo;
    BuildableArtifact manifests;
    String packageForR;

    @Input
    ApkInfo getMainApkInfo() {
        return mainApkInfo;
    }

    @InputFiles
    BuildableArtifact getManifests() {
        return manifests;
    }

    @TaskAction
    public void generateTestConfig() throws IOException {
        checkNotNull(resourcesDirectory);
        checkNotNull(assets);
        checkNotNull(sdkHome);

        BuildOutput output =
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, manifests)
                        .element(mainApkInfo);
        generateTestConfigForOutput(
                Iterables.getOnlyElement(assets).toPath().toAbsolutePath(),
                resourcesDirectory.getSingleFile().toPath().toAbsolutePath(),
                sdkHome,
                packageForR,
                checkNotNull(output, "Unable to find manifest output").getOutputFile().toPath(),
                generatedJavaResourcesDirectory.toPath().toAbsolutePath());
    }

    @VisibleForTesting
    static void generateTestConfigForOutput(
            @NonNull Path assetsDir,
            @NonNull Path resDir,
            @NonNull Path sdkHome,
            @NonNull String packageForR,
            @NonNull Path manifest,
            @NonNull Path outputDir)
            throws IOException {

        Properties properties = new Properties();
        properties.setProperty("android_sdk_home", sdkHome.toAbsolutePath().toString());
        properties.setProperty("android_merged_resources", resDir.toAbsolutePath().toString());
        properties.setProperty("android_merged_assets", assetsDir.toAbsolutePath().toString());
        properties.setProperty("android_merged_manifest", manifest.toAbsolutePath().toString());
        properties.setProperty("android_custom_package", packageForR);

        Path output =
                outputDir
                        .resolve("com")
                        .resolve("android")
                        .resolve("tools")
                        .resolve("test_config.properties");
        Files.createDirectories(output.getParent());

        try (Writer writer = Files.newBufferedWriter(output)) {
            properties.store(writer, "# Generated by the Android Gradle Plugin");
        }
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getResourcesDirectory() {
        return resourcesDirectory.getSingleFile().getPath();
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getAssets() {
        return Iterables.getOnlyElement(assets).getPath();
    }

    @Input // No need for @InputDirectory, we only care about the path.
    public String getSdkHome() {
        return sdkHome.toString();
    }

    @OutputDirectory
    public File getOutputFile() {
        return generatedJavaResourcesDirectory;
    }

    @Input
    public String getPackageForR() {
        return packageForR;
    }

    public static class ConfigAction implements TaskConfigAction<GenerateTestConfig> {

        @NonNull private final VariantScope scope;
        @NonNull private final VariantScope testedScope;
        @NonNull private final File outputDirectory;

        public ConfigAction(@NonNull VariantScope scope, @NonNull File outputDirectory) {
            this.scope = scope;
            this.testedScope =
                    Preconditions.checkNotNull(
                                    scope.getTestedVariantData(), "Not a unit test variant.")
                            .getScope();
            this.outputDirectory = outputDirectory;
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
            // we don't actually consume the task, only the path, so make a manual dependency
            // on the filecollections.

            task.resourcesDirectory = testedScope.getOutput(MERGED_NOT_COMPILED_RES);
            task.dependsOn(task.resourcesDirectory);
            task.manifests =
                    testedScope
                            .getBuildArtifactsHolder()
                            .getFinalArtifactFiles(InternalArtifactType.MERGED_MANIFESTS);
            task.assets =
                    testedScope.getBuildArtifactsHolder().getFinalArtifactFiles(MERGED_ASSETS);
            task.dependsOn(task.assets);
            task.mainApkInfo = testedScope.getOutputScope().getMainSplit();
            task.sdkHome =
                    Paths.get(scope.getGlobalScope().getAndroidBuilder().getTarget().getLocation());
            task.generatedJavaResourcesDirectory = outputDirectory;
            task.packageForR = testedScope.getVariantConfiguration().getOriginalApplicationId();
        }
    }
}
