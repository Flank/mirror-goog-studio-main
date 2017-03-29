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

package com.android.build.gradle.internal.externalBuild;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.sdk.TargetInfo;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Project;

/**
 * Reads the manifest file produced by an external build system.
 */
public class ExternalBuildManifestLoader {

    /**
     * Loads the passed manifest file and populates its information in the passed context object.
     *
     * @param manifestProtoFile the manifest file, in binary proto format
     * @param project the project directory in case the manifest contains relative paths.
     * @param projectOptions the project options
     * @param externalBuildContext the context to populate.
     * @throws IOException if the file cannot be read correctly
     */
    public static void loadAndPopulateContext(
            @NonNull File execRootFile,
            @NonNull File manifestProtoFile,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull ExternalBuildContext externalBuildContext)
            throws IOException {
        if (!manifestProtoFile.exists()) {
            throw new FileNotFoundException(manifestProtoFile.getAbsolutePath());
        }

        ExternalBuildApkManifest.ApkManifest manifest;
        // read the manifest file
        try (InputStream is = new BufferedInputStream(new FileInputStream(manifestProtoFile))) {
            manifest = ExternalBuildApkManifest.ApkManifest.parseFrom(is);
        }
        externalBuildContext.setBuildManifest(manifest);
        externalBuildContext.setExecutionRoot(execRootFile);

        List<File> jarFiles = manifest.getJarsList().stream()
                .map(artifact -> new File(execRootFile, artifact.getExecRootPath()))
                .collect(Collectors.toList());
        externalBuildContext.setInputJarFiles(jarFiles);

        externalBuildContext.setAndroidBuilder(
                createAndroidBuilder(execRootFile, project, projectOptions, manifest));
    }

    @NonNull
    private static AndroidBuilder createAndroidBuilder(
            @NonNull File executionRoot,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull ExternalBuildApkManifest.ApkManifest manifest) {
        ExternalBuildApkManifest.AndroidSdk sdk = manifest.getAndroidSdk();

        File zipAlign = sdk.getZipalign().isEmpty()
            ? new File(getAbsoluteFile(executionRoot, sdk.getAapt()).getParentFile(),
                SdkConstants.FN_ZIPALIGN)
            : getAbsoluteFile(executionRoot, sdk.getZipalign());

        BuildToolInfo buildToolInfo = BuildToolInfo.partial(
                new Revision(25, 0, 0),
                project.getProjectDir(),
                ImmutableMap.of(
                        // TODO: Put dx.jar in the proto
                        BuildToolInfo.PathId.DX_JAR,
                        getAbsoluteFile(executionRoot, sdk.getDx()),
                        BuildToolInfo.PathId.ZIP_ALIGN,
                        zipAlign,
                        BuildToolInfo.PathId.AAPT,
                        getAbsoluteFile(executionRoot, sdk.getAapt())));

        IAndroidTarget androidTarget =
                new ExternalBuildAndroidTarget(
                        getAbsoluteFile(executionRoot, sdk.getAndroidJar()));

        TargetInfo targetInfo = new TargetInfo(androidTarget, buildToolInfo);

        AndroidBuilder androidBuilder =
                new AndroidBuilder(
                        project.getPath(),
                        "Android Studio + external build system",
                        new GradleProcessExecutor(project),
                        new GradleJavaProcessExecutor(project),
                        new ExtraModelInfo(projectOptions, project.getLogger()),
                        new LoggerWrapper(project.getLogger()),
                        false);

        androidBuilder.setTargetInfo(targetInfo);
        return androidBuilder;
    }

    private static File getAbsoluteFile(File executionRoot, String relativeOrAbsoluteFilePath) {
        File relativeOrAbsoluteFile = new File(relativeOrAbsoluteFilePath);
        return relativeOrAbsoluteFile.isAbsolute()
                ? relativeOrAbsoluteFile
                : new File(executionRoot, relativeOrAbsoluteFilePath);
    }
}
