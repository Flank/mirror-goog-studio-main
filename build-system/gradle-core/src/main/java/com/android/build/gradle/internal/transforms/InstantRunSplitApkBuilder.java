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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.ide.common.signing.KeytoolException;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Common behavior for creating instant run related split APKs.
 */
abstract class InstantRunSplitApkBuilder extends Transform {

    @NonNull
    protected final Logger logger;
    @NonNull
    protected final Project project;
    @NonNull
    private final AndroidBuilder androidBuilder;
    @Nullable private final FileCache fileCache;
    @NonNull private final AaptGeneration aaptGeneration;
    @NonNull private final InstantRunBuildContext buildContext;
    @NonNull
    protected final File outputDirectory;
    @Nullable
    private final CoreSigningConfig signingConf;
    @NonNull
    private final PackagingScope packagingScope;
    @NonNull
    private final AaptOptions aaptOptions;
    @NonNull
    private final File supportDirectory;

    public InstantRunSplitApkBuilder(
            @NonNull Logger logger,
            @NonNull Project project,
            @NonNull InstantRunBuildContext buildContext,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCache fileCache,
            @NonNull PackagingScope packagingScope,
            @Nullable CoreSigningConfig signingConf,
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AaptOptions aaptOptions,
            @NonNull File outputDirectory,
            @NonNull File supportDirectory) {
        this.logger = logger;
        this.project = project;
        this.buildContext = buildContext;
        this.androidBuilder = androidBuilder;
        this.fileCache = fileCache;
        this.packagingScope = packagingScope;
        this.signingConf = signingConf;
        this.aaptGeneration = aaptGeneration;
        this.aaptOptions = aaptOptions;
        this.outputDirectory = outputDirectory;
        this.supportDirectory = supportDirectory;
    }

    @NonNull
    @Override
    public final Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> builder =
                ImmutableMap.<String, Object>builder()
                        .put("applicationId", packagingScope.getApplicationId())
                        .put("versionCode", packagingScope.getVersionCode())
                        .put("aaptGeneration", aaptGeneration.name());
        if (packagingScope.getVersionName() != null) {
            builder.put("versionName", packagingScope.getVersionName());
        }
        return builder.build();
    }

    protected static class DexFiles {
        private final ImmutableSet<File> dexFiles;
        private final String dexFolderName;

        protected DexFiles(@NonNull File[] dexFiles, @NonNull String dexFolderName) {
            this(ImmutableSet.copyOf(dexFiles), dexFolderName);
        }

        protected DexFiles(@NonNull ImmutableSet<File> dexFiles, @NonNull String dexFolderName) {
            this.dexFiles = dexFiles;
            this.dexFolderName = dexFolderName;
        }

        protected String encodeName() {
            return dexFolderName.replace('-', '_');
        }

        protected ImmutableSet<File> getDexFiles() {
            return dexFiles;
        }
    }

    @NonNull
    protected File generateSplitApk(@NonNull DexFiles dexFiles)
            throws IOException, KeytoolException, PackagerException, InterruptedException,
                    ProcessException, TransformException, ExecutionException {

        String uniqueName = dexFiles.encodeName();
        final File alignedOutput = new File(outputDirectory, uniqueName + ".apk");
        Files.createParentDirs(alignedOutput);
        File resPackageFile = generateSplitApkManifest(uniqueName);

        // packageCodeSplitApk uses a temporary directory for incremental runs. Since we don't
        // do incremental builds here, make sure it gets an empty directory.
        File tempDir = new File(supportDirectory, "package_" + uniqueName);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new TransformException("Cannot create temporary folder "
                    + tempDir.getAbsolutePath());
        }

        FileUtils.cleanOutputDir(tempDir);

        androidBuilder.packageCodeSplitApk(
                resPackageFile,
                dexFiles.dexFiles,
                signingConf,
                alignedOutput,
                tempDir,
                ApkCreatorFactories.fromProjectProperties(project, true));

        buildContext.addChangedFile(FileType.SPLIT, alignedOutput);
        //noinspection ResultOfMethodCallIgnored
        resPackageFile.delete();
        return alignedOutput;
    }

    @NonNull
    private File generateSplitApkManifest(@NonNull String uniqueName)
            throws IOException, ProcessException, InterruptedException {

        String versionNameToUse = packagingScope.getVersionName();
        int versionCode = packagingScope.getVersionCode();
        if (versionNameToUse == null) {
            versionNameToUse = String.valueOf(versionCode);
        }

        File apkSupportDir = new File(supportDirectory, uniqueName);
        if (!apkSupportDir.exists() && !apkSupportDir.mkdirs()) {
            logger.error("Cannot create apk support dir {}", apkSupportDir.getAbsoluteFile());
        }
        File androidManifest = new File(apkSupportDir, "AndroidManifest.xml");
        try (OutputStreamWriter fileWriter =
                     new OutputStreamWriter(new FileOutputStream(androidManifest), "UTF-8")) {
            fileWriter.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                    .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                    .append("      package=\"").append(packagingScope.getApplicationId()).append("\"\n");
            if (versionCode != VersionQualifier.DEFAULT_VERSION) {
                fileWriter
                        .append("      android:versionCode=\"").append(String.valueOf(versionCode))
                        .append("\"\n")
                        .append("      android:versionName=\"").append(versionNameToUse)
                        .append("\"\n");
            }
            fileWriter
                    .append("      split=\"lib_").append(uniqueName).append("_apk\">\n")
                    .append("</manifest>\n");
            fileWriter.flush();
        }

        File resFilePackageFile = new File(apkSupportDir, "resources_ap");

        AaptPackageConfig.Builder aaptConfig = new AaptPackageConfig.Builder()
                .setManifestFile(androidManifest)
                .setOptions(aaptOptions)
                .setDebuggable(true)
                .setVariantType(VariantType.DEFAULT)
                .setResourceOutputApk(resFilePackageFile);

        androidBuilder.processResources(getAapt(), aaptConfig);

        return resFilePackageFile;
    }

    protected Aapt getAapt() throws IOException {
        return AaptGradleFactory.make(
                aaptGeneration,
                androidBuilder,
                null,
                fileCache,
                true,
                FileUtils.mkdirs(
                        new File(
                                packagingScope.getIncrementalDir(
                                        "instantRunDependenciesApkBuilder"),
                                "aapt-temp")),
                0);
    }

}
