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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.apkzlib.zfile.ApkCreatorFactory;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.ApkInfo;
import com.android.sdklib.BuildToolInfo;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link InstantRunSliceSplitApkBuilder}
 */
public class InstantRunSplitApkBuilderTest {

    @Mock Logger logger;
    @Mock Project project;
    @Mock InstantRunBuildContext buildContext;
    @Mock AndroidBuilder androidBuilder;
    @Mock Aapt aapt;
    @Mock CoreSigningConfig coreSigningConfig;
    @Mock FileCollection mainResources;
    @Mock FileTree mainResourcesApkFileTree;

    @Mock TargetInfo targetInfo;
    @Mock BuildToolInfo buildTools;
    @Mock FileCollection apkList;

    @Rule public TemporaryFolder outputDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder supportDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder fileCacheDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder apkListDirectory = new TemporaryFolder();

    FileCache fileCache;
    InstantRunSliceSplitApkBuilder instantRunSliceSplitApkBuilder;
    File instantRunFolder;
    File aaptTempFolder;

    final ApkInfo apkInfo =
            ApkInfo.of(
                    VariantOutput.OutputType.MAIN,
                    ImmutableList.of(),
                    12345,
                    "test_version_name",
                    true,
                    null);

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(androidBuilder.getTargetInfo()).thenReturn(targetInfo);
        when(targetInfo.getBuildTools()).thenReturn(buildTools);
        when(mainResources.getAsFileTree()).thenReturn(mainResourcesApkFileTree);

        File apkListFile = apkListDirectory.newFile("apk-list.json");
        FileUtils.write(apkListFile, BuildOutputs.persistApkList(ImmutableList.of(apkInfo)));
        when(apkList.getSingleFile()).thenReturn(apkListFile);

        instantRunFolder = supportDirectory.newFolder("instant-run");
        aaptTempFolder = supportDirectory.newFolder("aapt-temp");
        fileCache = FileCache.getInstanceWithSingleProcessLocking(fileCacheDirectory.getRoot());
        instantRunSliceSplitApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        logger,
                        project,
                        buildContext,
                        androidBuilder,
                        fileCache,
                        "com.foo.test",
                        coreSigningConfig,
                        AaptGeneration.AAPT_V2_DAEMON_MODE,
                        new AaptOptions(null, false, null),
                        outputDirectory.getRoot(),
                        instantRunFolder,
                        aaptTempFolder, /* runAapt2Serially */
                        false,
                        mainResources,
                        mainResources,
                        apkList,
                        apkInfo) {
                    @Override
                    protected Aapt getAapt() {
                        return aapt;
                    }
                };
    }

    @Test
    public void testParameterInputs() {

        Map<String, Object> parameterInputs = instantRunSliceSplitApkBuilder.getParameterInputs();
        assertThat(parameterInputs).containsEntry("applicationId", "com.foo.test");
        assertThat(parameterInputs).hasSize(2);
    }

    @Test
    public void testGeneratedManifest() throws Exception {

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        File folder = new File(instantRunFolder, "folderName");
        assertThat(folder.isDirectory()).isTrue();
        assertThat(folder.getName()).isEqualTo("folderName");
        File[] folderFiles = folder.listFiles();
        assertThat(folderFiles).hasLength(1);
        assertThat(folderFiles[0].getName()).isEqualTo("AndroidManifest.xml");
        String androidManifest = Files.toString(folderFiles[0], Charsets.UTF_8);
        assertThat(androidManifest).contains("package=\"com.foo.test\"");
        assertThat(androidManifest).contains("android:versionCode=\"12345\"");
        assertThat(androidManifest).contains("android:versionName=\"test_version_name\"");
        assertThat(androidManifest).contains("split=\"lib_folderName_apk\"");
    }

    @Test
    public void testApFileGeneration() throws Exception {

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        ArgumentCaptor<AaptPackageConfig.Builder> aaptConfigCaptor =
                ArgumentCaptor.forClass(AaptPackageConfig.Builder.class);

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        Mockito.verify(androidBuilder)
                .processResources(any(Aapt.class), aaptConfigCaptor.capture());

        AaptPackageConfig build = aaptConfigCaptor.getValue().build();
        File resourceOutputApk = build.getResourceOutputApk();
        assertThat(resourceOutputApk.getName()).isEqualTo("resources_ap");
        assertThat(build.getVariantType()).isEqualTo(VariantType.DEFAULT);
        assertThat(build.isDebuggable()).isTrue();
    }

    @Test
    public void testGenerateSplitApk() throws Exception {

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        File mainResourcesApk = new File("mainResources" + SdkConstants.DOT_RES);
        when(mainResourcesApkFileTree.getFiles()).thenReturn(ImmutableSet.of(mainResourcesApk));
        ArgumentCaptor<AaptPackageConfig.Builder> aaptConfigCaptor =
                ArgumentCaptor.forClass(AaptPackageConfig.Builder.class);

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        Mockito.verify(androidBuilder)
                .processResources(any(Aapt.class), aaptConfigCaptor.capture());

        AaptPackageConfig build = aaptConfigCaptor.getValue().build();
        File resourceOutputApk = build.getResourceOutputApk();
        assertThat(build.getImports()).hasSize(1);
        assertThat(build.getImports()).containsExactly(mainResourcesApk);
        assertThat(resourceOutputApk.getName()).isEqualTo("resources_ap");

        ArgumentCaptor<File> outApkLocation = ArgumentCaptor.forClass(File.class);
        Mockito.verify(androidBuilder).packageCodeSplitApk(
                eq(resourceOutputApk), eq(dexFiles.getDexFiles()),
                eq(coreSigningConfig), outApkLocation.capture(), any(File.class),
                any(ApkCreatorFactory.class));

        assertThat(outApkLocation.getValue().getName()).isEqualTo(dexFiles.encodeName() + ".apk");
        Mockito.verify(buildContext).addChangedFile(eq(FileType.SPLIT),
                eq(outApkLocation.getValue()));
    }

    @Test
    public void testNoVersionGeneration() throws Exception {

        File apkListFile = apkListDirectory.newFile("apk-list2.json");
        ApkInfo apkInfo =
                ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), -1, "-1", true, null);

        FileUtils.write(apkListFile, BuildOutputs.persistApkList(ImmutableList.of()));
        when(apkList.getSingleFile()).thenReturn(apkListFile);

        InstantRunSliceSplitApkBuilder instantRunSliceSplitApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        logger,
                        project,
                        buildContext,
                        androidBuilder,
                        fileCache,
                        "com.foo.test",
                        coreSigningConfig,
                        AaptGeneration.AAPT_V2_DAEMON_MODE,
                        new AaptOptions(null, false, null),
                        outputDirectory.getRoot(),
                        instantRunFolder,
                        aaptTempFolder, /* runAapt2Serially */
                        false,
                        mainResources,
                        mainResources,
                        apkList,
                        apkInfo) {
                    @Override
                    protected Aapt getAapt() {
                        return aapt;
                    }
                };

        ImmutableSet<File> files = ImmutableSet.of(
                new File("/tmp", "dexFile1"), new File("/tmp", "dexFile2"));

        InstantRunSplitApkBuilder.DexFiles dexFiles =
                new InstantRunSplitApkBuilder.DexFiles(files, "folderName");

        instantRunSliceSplitApkBuilder.generateSplitApk(apkInfo, dexFiles);
        File folder = new File(instantRunFolder, "folderName");
        assertThat(folder.isDirectory()).isTrue();
        assertThat(folder.getName()).isEqualTo("folderName");
        File[] folderFiles = folder.listFiles();
        assertThat(folderFiles).hasLength(1);
        assertThat(folderFiles[0].getName()).isEqualTo("AndroidManifest.xml");
        String androidManifest = Files.toString(folderFiles[0], Charsets.UTF_8);
        assertThat(androidManifest).doesNotContain("android:versionCode");
        assertThat(androidManifest).doesNotContain("android:versionName");
    }
}
