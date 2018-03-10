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

package com.android.build.gradle.internal.ide;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonCompositeVisitor;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStatsBuildingVisitor;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStreamingParser;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStreamingVisitor;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.Version;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builder class for {@link NativeAndroidProject}. */
class NativeAndroidProjectBuilder {
    @NonNull private final String projectName;
    @NonNull private final Set<File> buildFiles = Sets.newHashSet();
    @NonNull private final Map<String, String> extensions = Maps.newHashMap();
    @NonNull private final List<NativeArtifact> artifacts = Lists.newArrayList();
    @NonNull private final List<NativeToolchain> toolChains = Lists.newArrayList();
    @NonNull private final Map<List<String>, NativeSettings> settingsMap = Maps.newHashMap();
    @NonNull private final Set<String> buildSystems = Sets.newHashSet();
    int settingIndex = 0;

    NativeAndroidProjectBuilder(@NonNull String projectName) {
        this.projectName = projectName;
    }

    /**
     * Add a buildSystem to the global list of build systems for this {@link NativeAndroidProject}.
     */
    void addBuildSystem(@NonNull String buildSystem) {
        this.buildSystems.add(buildSystem);
    }

    /**
     * Add a per-variant Json to builder. JSon is streamed so it is not read into memory all at
     * once.
     */
    void addJson(
            @NonNull JsonReader reader,
            @NonNull String variantName,
            @NonNull GradleBuildVariant.NativeBuildConfigInfo.Builder config)
            throws IOException {
        JsonStreamingVisitor modelBuildingVisitor = new JsonStreamingVisitor(this, variantName);
        AndroidBuildGradleJsonStatsBuildingVisitor statsVisitor =
                new AndroidBuildGradleJsonStatsBuildingVisitor(config);
        AndroidBuildGradleJsonCompositeVisitor composite =
                new AndroidBuildGradleJsonCompositeVisitor(statsVisitor, modelBuildingVisitor);
        try (AndroidBuildGradleJsonStreamingParser parser =
                new AndroidBuildGradleJsonStreamingParser(reader, composite)) {
            parser.parse();
        }
    }

    /**
     * Build the final {@link NativeAndroidProject}. Return null if there are no build files (which
     * is taken as a sign that there's nothing to show the user in Android Studio).
     */
    @Nullable
    NativeAndroidProject buildOrNull() {
        // If there are no build files (therefore no native configurations) don't return a model
        if (this.buildFiles.isEmpty()) {
            return null;
        }
        return new NativeAndroidProjectImpl(
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                this.projectName,
                this.buildFiles,
                this.artifacts,
                this.toolChains,
                ImmutableList.copyOf(this.settingsMap.values()),
                this.extensions,
                buildSystems,
                Version.BUILDER_MODEL_API_VERSION);
    }

    /**
     * Json streaming parser that converts a series of JSon files to {@link NativeAndroidProject}
     */
    static class JsonStreamingVisitor extends AndroidBuildGradleJsonStreamingVisitor {
        @NonNull private final NativeAndroidProjectBuilder builder;
        @NonNull private final String variantName;
        @Nullable private String currentToolchain = null;
        @Nullable private String currentCExecutable = null;
        @Nullable private String currentCppExecutable = null;
        @Nullable private String currentLibraryName = null;
        @Nullable private String currentLibraryToolchain = null;
        @Nullable private String currentLibraryOutput = null;
        @Nullable private String currentLibraryAbi = null;
        @Nullable private String currentLibraryArtifactName = null;
        @Nullable private List<File> currentLibraryRuntimeFiles = null;
        @Nullable private List<NativeFile> currentLibrarySourceFiles = null;
        @Nullable private String currentLibraryFileSettingsName = null;
        @Nullable private String currentLibraryFilePath = null;
        @Nullable private String currentLibraryFileWorkingDirectory = null;

        JsonStreamingVisitor(
                @NonNull NativeAndroidProjectBuilder builder, @NonNull String variantName) {
            this.variantName = variantName;
            this.builder = builder;
        }

        @Override
        public void visitBuildFile(@NonNull String buildFile) {
            builder.buildFiles.add(new File(buildFile));
        }

        @Override
        public void beginLibrary(@NonNull String libraryName) {
            this.currentLibraryName = libraryName;
            this.currentLibraryRuntimeFiles = Lists.newArrayList();
            this.currentLibrarySourceFiles = Lists.newArrayList();
        }

        @Override
        public void endLibrary() {
            checkNotNull(currentLibraryName);
            checkNotNull(currentLibraryToolchain);
            checkNotNull(currentLibrarySourceFiles);
            checkNotNull(currentLibraryOutput);
            checkNotNull(currentLibraryRuntimeFiles);
            checkNotNull(currentLibraryAbi);
            checkNotNull(currentLibraryArtifactName);

            builder.artifacts.add(
                    new NativeArtifactImpl(
                            currentLibraryName,
                            currentLibraryToolchain,
                            variantName,
                            "",
                            ImmutableList.of(),
                            currentLibrarySourceFiles,
                            ImmutableList.of(),
                            newFileOrNull(currentLibraryOutput),
                            currentLibraryRuntimeFiles,
                            currentLibraryAbi,
                            currentLibraryArtifactName));

            this.currentLibraryName = null;
            this.currentLibraryToolchain = null;
            this.currentLibraryOutput = null;
            this.currentLibraryAbi = null;
            this.currentLibraryArtifactName = null;
            this.currentLibraryRuntimeFiles = null;
            this.currentLibrarySourceFiles = null;
        }

        @Override
        public void beginToolchain(@NonNull String toolchain) {
            this.currentToolchain = toolchain;
        }

        @Override
        public void endToolchain() {
            checkNotNull(currentToolchain);
            builder.toolChains.add(
                    new NativeToolchainImpl(
                            this.currentToolchain,
                            newFileOrNull(currentCExecutable),
                            newFileOrNull(currentCppExecutable)));
            this.currentToolchain = null;
            this.currentCExecutable = null;
            this.currentCppExecutable = null;
        }

        @Nullable
        private File newFileOrNull(@Nullable String filename) {
            if (filename == null) {
                return null;
            }
            return new File(filename);
        }

        @Override
        public void visitLibraryAbi(@NonNull String abi) {
            this.currentLibraryAbi = abi;
        }

        @Override
        public void visitLibraryArtifactName(@NonNull String artifact) {
            this.currentLibraryArtifactName = artifact;
        }

        @Override
        public void visitLibraryOutput(@NonNull String output) {
            this.currentLibraryOutput = output;
        }

        @Override
        public void visitLibraryToolchain(@NonNull String toolchain) {
            this.currentLibraryToolchain = toolchain;
        }

        @Override
        public void visitToolchainCCompilerExecutable(@NonNull String executable) {
            this.currentCExecutable = executable;
        }

        @Override
        public void visitToolchainCppCompilerExecutable(@NonNull String executable) {
            this.currentCppExecutable = executable;
        }

        @Override
        public void visitLibraryFileFlags(@NonNull String flags) {
            this.currentLibraryFileSettingsName =
                    getSettingsName(StringHelper.tokenizeCommandLineToEscaped(flags));
        }

        @Override
        public void visitLibraryFileSrc(@NonNull String src) {
            this.currentLibraryFilePath = src;
        }

        @Override
        public void visitLibraryFileWorkingDirectory(@NonNull String workingDirectory) {
            this.currentLibraryFileWorkingDirectory = workingDirectory;
        }

        @Override
        public void visitCFileExtensions(@NonNull String extension) {
            builder.extensions.put(extension, "c");
        }

        @Override
        public void visitCppFileExtensions(@NonNull String extension) {
            builder.extensions.put(extension, "c++");
        }

        @Override
        public void visitLibraryRuntimeFile(@NonNull String runtimeFile) {
            checkNotNull(currentLibraryRuntimeFiles);
            currentLibraryRuntimeFiles.add(new File(runtimeFile));
        }

        @Override
        public void endLibraryFile() {
            checkNotNull(currentLibrarySourceFiles);
            checkNotNull(currentLibraryFilePath);

            if (currentLibraryFileSettingsName != null) {
                // See https://issuetracker.google.com/73122455
                // In the case where there is no flags field we don't generate a settings key
                // Just skip this library source file. Since we don't have flags we can't tell
                // if it is even an Android-targeting build.
                this.currentLibrarySourceFiles.add(
                        new NativeFileImpl(
                                new File(currentLibraryFilePath),
                                currentLibraryFileSettingsName,
                                newFileOrNull(currentLibraryFileWorkingDirectory)));
            }

            this.currentLibraryFilePath = null;
            this.currentLibraryFileSettingsName = null;
            this.currentLibraryFileWorkingDirectory = null;
        }

        private String getSettingsName(@NonNull List<String> flags) {
            // Copy flags to ensure it is serializable.
            List<String> flagsCopy = ImmutableList.copyOf(flags);
            NativeSettings setting = builder.settingsMap.get(flags);
            if (setting == null) {
                setting = new NativeSettingsImpl("setting" + builder.settingIndex, flagsCopy);
                builder.settingsMap.put(flagsCopy, setting);
                builder.settingIndex++;
            }
            return setting.getName();
        }
    }
}
