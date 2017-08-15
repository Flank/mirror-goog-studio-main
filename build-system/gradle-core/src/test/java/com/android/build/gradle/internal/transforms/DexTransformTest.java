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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexingType;
import com.android.builder.internal.FakeAndroidTarget;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit test for {@link DexTransform}. */
public class DexTransformTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    private FakeAndroidTarget androidTarget;
    private File buildToolsFolder;

    @Before
    public void setUp() throws Exception {
        androidTarget =
                new FakeAndroidTarget(testDir.newFolder("platform").getPath(), "android-25");
        buildToolsFolder = testDir.newFolder("build-tools");
    }

    @Test
    public void testPreDexLibraries() throws IOException, TransformException, InterruptedException {
        File nonSnapshotExtLibJar = testDir.newFile("nonSnapshotExtLibJar");
        Files.write("nonSnapshotExtLibJar", nonSnapshotExtLibJar, StandardCharsets.UTF_8);
        TransformInput nonSnapshotExternalLibraryJarInput =
                TransformTestHelper.singleJarBuilder(nonSnapshotExtLibJar)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setStatus(Status.NOTCHANGED)
                        .build();

        File snapshotExtLibJar = new File(testDir.newFolder("1.0-SNAPSHOT"), "snapshotExtLibJar");
        Files.write("snapshotExtLibJar", snapshotExtLibJar, StandardCharsets.UTF_8);
        TransformInput snapshotExternalLibraryJarInput =
                TransformTestHelper.singleJarBuilder(snapshotExtLibJar)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setStatus(Status.NOTCHANGED)
                        .build();

        File nonExtLibJar = testDir.newFile("nonExtLibJar");
        Files.write("nonExtLibJar", nonExtLibJar, StandardCharsets.UTF_8);
        TransformInput nonExternalLibraryJarInput =
                TransformTestHelper.singleJarBuilder(nonExtLibJar)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setScopes(QualifiedContent.Scope.PROJECT)
                        .setStatus(Status.NOTCHANGED)
                        .build();

        File dirInput = testDir.newFolder("dirInput");
        Files.write("dirInput", new File(dirInput, "baz"), StandardCharsets.UTF_8);
        TransformInput directoryInput =
                TransformTestHelper.directoryBuilder(dirInput)
                        .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .build();

        // Output directory of pre-dexing
        File preDexOutputDir = testDir.newFolder("pre-dex");

        // Output directory of dexing
        File dexOutputDir = testDir.newFolder("dex");

        // The build cache
        FileCache buildCache =
                FileCache.getInstanceWithMultiProcessLocking(testDir.newFolder("cache"));

        // Run dexing
        runDexing(
                ImmutableList.of(
                        nonSnapshotExternalLibraryJarInput,
                        snapshotExternalLibraryJarInput,
                        nonExternalLibraryJarInput,
                        directoryInput),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);

        // Assert pre-dex results, expect that all the inputs are pre-dexed
        assertEquals(4, getFileCount(preDexOutputDir));
        File[] preDexOutputFiles = preDexOutputDir.listFiles();
        assertNotNull(preDexOutputFiles);
        File preDexedNonSnapshotExternalLibraryJarInput = null;
        File preDexedSnapshotExternalLibraryJarInput = null;
        File preDexedNonExternalLibraryJarInput = null;
        File preDexedDirectoryInput = null;
        for (int i = 0; i < 4; i++) {
            if (Files.readFirstLine(preDexOutputFiles[i], Charsets.UTF_8)
                    .contains("nonSnapshotExtLibJar")) {
                preDexedNonSnapshotExternalLibraryJarInput = preDexOutputFiles[i];
            } else if (Files.readFirstLine(preDexOutputFiles[i], Charsets.UTF_8)
                    .contains("snapshotExtLibJar")) {
                preDexedSnapshotExternalLibraryJarInput = preDexOutputFiles[i];
            } else if (Files.readFirstLine(preDexOutputFiles[i], Charsets.UTF_8)
                    .contains("nonExtLibJar")) {
                preDexedNonExternalLibraryJarInput = preDexOutputFiles[i];
            } else if (Files.readFirstLine(preDexOutputFiles[i], Charsets.UTF_8)
                    .contains("dirInput")) {
                preDexedDirectoryInput = preDexOutputFiles[i];
            }
        }
        assertNotNull(preDexedSnapshotExternalLibraryJarInput);
        assertNotNull(preDexedDirectoryInput);
        assertNotNull(preDexedNonExternalLibraryJarInput);
        assertNotNull(preDexedNonSnapshotExternalLibraryJarInput);

        assertThat(preDexedNonSnapshotExternalLibraryJarInput)
                .hasContents("Pre-dexed content of nonSnapshotExtLibJar");
        assertThat(preDexedSnapshotExternalLibraryJarInput)
                .hasContents("Pre-dexed content of snapshotExtLibJar");
        assertThat(preDexedNonExternalLibraryJarInput)
                .hasContents("Pre-dexed content of nonExtLibJar");
        assertThat(preDexedDirectoryInput)
                .hasContents("Pre-dexed content of dirInput");

        // Assert dex results, expect that all the pre-dexed outputs are merged into 1 file
        File[] dexOutputFiles = dexOutputDir.listFiles();
        assertNotNull(dexOutputFiles);
        assertEquals(1, dexOutputFiles.length);
        File dexOutputFile = dexOutputFiles[0];
        assertThat(dexOutputFile).hasContents("Dexed content");

        // Assert cache results, expect that only the pre-dexed output of non-snapshot external
        // library jar file is cached
        File[] files = buildCache.getCacheDirectory().listFiles();
        assertNotNull(files);
        List<File> cachedEntries =
                Arrays.stream(files).filter(File::isDirectory).collect(Collectors.toList());
        assertEquals(1, cachedEntries.size());
        File cachedPreDexedNonSnapshotExternalLibraryJarInput =
                new File(cachedEntries.get(0), "output");
        assertThat(cachedPreDexedNonSnapshotExternalLibraryJarInput)
                .hasContents("Pre-dexed content of nonSnapshotExtLibJar");

        long cachedFileTimestamp = cachedPreDexedNonSnapshotExternalLibraryJarInput.lastModified();
        assertThat(preDexedNonSnapshotExternalLibraryJarInput).wasModifiedAt(cachedFileTimestamp);
        long preDexedSnapshotExternalLibraryJarInputTimestamp =
                preDexedSnapshotExternalLibraryJarInput.lastModified();
        long preDexedNonExternalLibraryJarInputTimestamp =
                preDexedNonExternalLibraryJarInput.lastModified();
        long preDexedDirectoryInputTimestamp =
                preDexedDirectoryInput.lastModified();

        // Re-run dexing
        TestUtils.waitForFileSystemTick();
        runDexing(
                ImmutableList.of(
                        nonSnapshotExternalLibraryJarInput,
                        snapshotExternalLibraryJarInput,
                        nonExternalLibraryJarInput,
                        directoryInput),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);

        // Assert pre-dex results, expect that the contents are unchanged
        assertEquals(4, getFileCount(preDexOutputDir));
        assertThat(preDexedNonSnapshotExternalLibraryJarInput)
                .hasContents("Pre-dexed content of nonSnapshotExtLibJar");
        assertThat(preDexedSnapshotExternalLibraryJarInput)
                .hasContents("Pre-dexed content of snapshotExtLibJar");
        assertThat(preDexedNonExternalLibraryJarInput)
                .hasContents("Pre-dexed content of nonExtLibJar");
        assertThat(preDexedDirectoryInput)
                .hasContents("Pre-dexed content of dirInput");

        // Assert dex results, expect that the contents are unchanged
        assertEquals(1, getFileCount(dexOutputDir));
        assertThat(dexOutputFile).hasContents("Dexed content");

        // Assert cache results, expect that the contents are unchanged
        assertEquals(1, countCacheEntries(buildCache));
        assertThat(cachedPreDexedNonSnapshotExternalLibraryJarInput)
                .hasContents("Pre-dexed content of nonSnapshotExtLibJar");

        // Also verify the timestamps to make sure that the cache's contents are not overwritten,
        // whereas the pre-dexed directory's non-cached contents are overwritten
        assertThat(cachedPreDexedNonSnapshotExternalLibraryJarInput)
                .wasModifiedAt(cachedFileTimestamp);
        assertThat(preDexedNonSnapshotExternalLibraryJarInput)
                .wasModifiedAt(cachedFileTimestamp);
        assertThat(preDexedSnapshotExternalLibraryJarInput)
                .isNewerThan(preDexedSnapshotExternalLibraryJarInputTimestamp);
        assertThat(preDexedNonExternalLibraryJarInput)
                .isNewerThan(preDexedNonExternalLibraryJarInputTimestamp);
        assertThat(preDexedDirectoryInput)
                .isNewerThan(preDexedDirectoryInputTimestamp);
    }

    @Test
    public void testInputsToBuildCache()
            throws IOException, TransformException, InterruptedException {
        // Inputs for dexing
        File inputFoo = testDir.newFile("foo");
        Files.write("Foo content", inputFoo, StandardCharsets.UTF_8);
        TransformInput transformInputFoo =
                TransformTestHelper.singleJarBuilder(inputFoo)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setStatus(Status.NOTCHANGED)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();

        File inputBar = testDir.newFile("bar");
        Files.write("Bar content", inputBar, StandardCharsets.UTF_8);
        TransformInput transformInputBar =
                TransformTestHelper.singleJarBuilder(inputBar)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();

        // Output directory of pre-dexing
        File preDexOutputDir = testDir.newFolder("pre-dex");

        // Output directory of dexing
        File dexOutputDir = testDir.newFolder("dex");

        // The build cache
        FileCache buildCache =
                FileCache.getInstanceWithMultiProcessLocking(testDir.newFolder("cache"));

        // Run dexing
        runDexing(
                ImmutableList.of(transformInputFoo, transformInputBar),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);

        // Assert cache results, expect that 2 entries are created
        assertEquals(2, countCacheEntries(buildCache));

        // Re-run pre-dexing with the same input files and build tools revision
        runDexing(
                ImmutableList.of(transformInputFoo, transformInputBar),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        // Expect the cache to remain the same
        assertEquals(2, countCacheEntries(buildCache));

        // Re-run pre-dexing with the same input files and different build tools revision
        runDexing(
                ImmutableList.of(transformInputFoo, transformInputBar),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                new Revision(AndroidBuilder.MIN_BUILD_TOOLS_REV.getMajor() + 1));
        // Expect the cache to contain 2 more entries
        assertEquals(4, countCacheEntries(buildCache));

        // Re-run pre-dexing with the same input files, with the contents of one file changed
        Files.write("New foo content", inputFoo, StandardCharsets.UTF_8);
        runDexing(
                ImmutableList.of(transformInputFoo, transformInputBar),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        Files.write("Foo content", inputFoo, StandardCharsets.UTF_8);
        // Expect the cache to contain 1 more entry
        assertEquals(5, countCacheEntries(buildCache));

        // Re-run pre-dexing with a new empty file
        File inputBaz = testDir.newFile("baz");
        TransformInput transformInputBaz =
                TransformTestHelper.singleJarBuilder(inputBaz)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        runDexing(
                ImmutableList.of(transformInputFoo, transformInputBar, transformInputBaz),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        // Expect the cache to contain 1 more entry
        assertEquals(6, countCacheEntries(buildCache));

        // Re-run pre-dexing with 2 exploded-aar files with the same contents as inputs
        File explodedAarFile1 =
                new File(
                        testDir.newFolder(),
                        "exploded-aar/com.android.support/support-compat/24.2.0/jars/classes.jar");
        File explodedAarFile2 =
                new File(
                        testDir.newFolder(),
                        "exploded-aar/com.android.support/support-compat/24.2.0/jars/classes.jar");
        Files.createParentDirs(explodedAarFile1);
        Files.createParentDirs(explodedAarFile2);
        Files.write("Some content", explodedAarFile1, StandardCharsets.UTF_8);
        Files.write("Some content", explodedAarFile2, StandardCharsets.UTF_8);
        TransformInput explodedAarJar1 =
                TransformTestHelper.singleJarBuilder(explodedAarFile1)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInput explodedAarJar2 =
                TransformTestHelper.singleJarBuilder(explodedAarFile2)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        runDexing(
                ImmutableList.of(explodedAarJar1, explodedAarJar2),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        // Expect the cache to contain 1 more entry
        assertEquals(7, countCacheEntries(buildCache));

        // Re-run pre-dexing with 2 instant-run.jar files with the same contents as inputs
        File instantRunFile1 = new File(testDir.newFolder(), "instant-run.jar");
        File instantRunFile2 = new File(testDir.newFolder(), "instant-run.jar");
        Files.write("Some content", instantRunFile1, StandardCharsets.UTF_8);
        Files.write("Some content", instantRunFile2, StandardCharsets.UTF_8);
        TransformInput instantRunJar1 =
                TransformTestHelper.singleJarBuilder(instantRunFile1)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInput instantRunJar2 =
                TransformTestHelper.singleJarBuilder(instantRunFile2)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        runDexing(
                ImmutableList.of(instantRunJar1, instantRunJar2),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        // Expect the cache to contain 1 more entry
        assertEquals(8, countCacheEntries(buildCache));
    }

    @Test
    public void testBuildCacheFailure() throws Exception {
        // Inputs for dexing
        File nonSnapshotExtLibJar = testDir.newFile("nonSnapshotExtLibJar");
        Files.write("nonSnapshotExtLibJar", nonSnapshotExtLibJar, StandardCharsets.UTF_8);
        TransformInput jarTransformInput =
                TransformTestHelper.singleJarBuilder(nonSnapshotExtLibJar)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();

        File dirInput = testDir.newFolder("dirInput");
        Files.write("dirInput", new File(dirInput, "baz"), StandardCharsets.UTF_8);
        TransformInput directoryTransformInput =
                TransformTestHelper.directoryBuilder(dirInput)
                        .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                        .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();

        // Output directory of pre-dexing
        File preDexOutputDir = testDir.newFolder("pre-dex");

        // Output directory of dexing
        File dexOutputDir = testDir.newFolder("dex");

        // Let the build cache throw an exception when called
        FileCache buildCache = mock(FileCache.class);
        when(buildCache.createFile(any(), any(), any())).thenThrow(
                new RuntimeException("Build cache error"));
        when(buildCache.getCacheDirectory()).thenReturn(testDir.newFolder("cache"));

        // Run dexing, expect it to fail
        try {
            runDexing(
                    ImmutableList.of(jarTransformInput, directoryTransformInput),
                    preDexOutputDir,
                    dexOutputDir,
                    buildCache,
                    AndroidBuilder.MIN_BUILD_TOOLS_REV);
            fail("Expected TransformException");
        } catch (TransformException exception) {
            assertThat(exception.getMessage()).contains("Unable to pre-dex");
            assertThat(Throwables.getRootCause(exception).getMessage())
                    .contains("Build cache error");
        }
    }

    private void runDexing(
            @NonNull Collection<TransformInput> transformInputs,
            @NonNull File preDexOutputDir,
            @NonNull File dexOutputDir,
            @NonNull FileCache buildCache,
            @NonNull Revision buildToolsRevision)
            throws TransformException, InterruptedException, IOException {
        TargetInfo targetInfo =
                new TargetInfo(
                        androidTarget,
                        BuildToolInfo.fromStandardDirectoryLayout(
                                buildToolsRevision, buildToolsFolder));

        AndroidBuilder fakeAndroidBuilder =
                new FakeAndroidBuilder(
                        "projectId",
                        "createdBy",
                        mock(ProcessExecutor.class),
                        mock(JavaProcessExecutor.class),
                        mock(ErrorReporter.class),
                        mock(ILogger.class),
                        false /* verboseExec */);
        fakeAndroidBuilder.setTargetInfo(targetInfo);

        FakeDexByteCodeConverter byteCodeConverter =
                new FakeDexByteCodeConverter(
                        mock(ILogger.class), targetInfo, mock(JavaProcessExecutor.class), false);

        // first we need to pre-dex
        List<String> preDexNames =
                ImmutableList.of("predex_0.jar", "predex_1.jar", "predex_2.jar", "predex_3.jar");
        runPreDexing(fakeAndroidBuilder, transformInputs, preDexOutputDir, buildCache, preDexNames);
        Set<TransformInput> preDexedInputs =
                preDexNames
                        .stream()
                        .map(
                                name ->
                                        TransformTestHelper.singleJarBuilder(
                                                        FileUtils.join(preDexOutputDir, name))
                                                .setScopes(
                                                        QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                                                .setContentTypes(ExtendedContentType.DEX)
                                                .build())
                        .collect(Collectors.toSet());

        // no merge dex files
        DexTransform dexTransform =
                new DexTransform(
                        new DefaultDexOptions(),
                        DexingType.MONO_DEX,
                        true,
                        null, // mainDexListFile
                        targetInfo,
                        byteCodeConverter,
                        mock(ErrorReporter.class),
                        1);

        TransformOutputProvider mockTransformOutputProvider = mock(TransformOutputProvider.class);
        when(mockTransformOutputProvider.getContentLocation(any(), any(), any(), any()))
                .thenReturn(dexOutputDir);
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(mockTransformOutputProvider)
                        .setInputs(preDexedInputs)
                        .build();

        dexTransform.transform(transformInvocation);
    }

    private static void runPreDexing(
            @NonNull AndroidBuilder fakeAndroidBuilder,
            @NonNull Collection<TransformInput> transformInputs,
            @NonNull File preDexOutputDir,
            @NonNull FileCache buildCache,
            @NonNull List<String> preDexNames)
            throws TransformException, InterruptedException, IOException {
        PreDexTransform preDexTransform =
                new PreDexTransform(
                        new DefaultDexOptions(),
                        fakeAndroidBuilder,
                        buildCache,
                        DexingType.MONO_DEX,
                        1);

        TransformOutputProvider mockTransformOutputProvider = mock(TransformOutputProvider.class);
        when(mockTransformOutputProvider.getContentLocation(any(), any(), any(), any()))
                .thenReturn(
                        FileUtils.join(preDexOutputDir, preDexNames.get(0)),
                        FileUtils.join(preDexOutputDir, preDexNames.get(1)),
                        FileUtils.join(preDexOutputDir, preDexNames.get(2)),
                        FileUtils.join(preDexOutputDir, preDexNames.get(3)));
        TransformInvocation transformInvocation =
                TransformTestHelper.invocationBuilder()
                        .setTransformOutputProvider(mockTransformOutputProvider)
                        .setInputs(new HashSet<>(transformInputs))
                        .build();

        preDexTransform.transform(transformInvocation);
    }

    public static int getFileCount(File directory) {
        checkArgument(directory.isDirectory());
        File[] files = directory.listFiles();
        assertNotNull(files);
        return files.length;
    }

    private static int countCacheEntries(FileCache buildCache) {
        // The cache directory contains subdirectories which are cache entries, and lock files.
        // Therefore, the number of cache entries equals the number of subdirectories.
        File[] files = buildCache.getCacheDirectory().listFiles();
        assertNotNull(files);
        return (int) Arrays.stream(files).filter(File::isDirectory).count();
    }

    private static class FakeAndroidBuilder extends AndroidBuilder {

        public FakeAndroidBuilder(
                @NonNull String projectId,
                @Nullable String createdBy,
                @NonNull ProcessExecutor processExecutor,
                @NonNull JavaProcessExecutor javaProcessExecutor,
                @NonNull ErrorReporter errorReporter,
                @NonNull ILogger logger,
                boolean verboseExec) {
            super(
                    projectId,
                    createdBy,
                    processExecutor,
                    javaProcessExecutor,
                    errorReporter,
                    logger,
                    verboseExec);
        }

        @Override
        public void preDexLibrary(
                @NonNull File inputFile,
                @NonNull File outFile,
                boolean multiDex,
                @NonNull DexOptions dexOptions,
                @NonNull ProcessOutputHandler processOutputHandler,
                int minSdkVersion)
                throws IOException {
            String content =
                    inputFile.isDirectory()
                            ? Files.toString(
                                    verifyNotNull(inputFile.listFiles())[0], StandardCharsets.UTF_8)
                            : Files.toString(inputFile, StandardCharsets.UTF_8);
            Files.write("Pre-dexed content of " + content, outFile, StandardCharsets.UTF_8);
        }
    }

    private static class FakeDexByteCodeConverter extends DexByteCodeConverter {

        public FakeDexByteCodeConverter(
                ILogger logger,
                TargetInfo targetInfo,
                JavaProcessExecutor javaProcessExecutor,
                boolean verboseExec) {
            super(logger, targetInfo, javaProcessExecutor, verboseExec, new NoOpErrorReporter());
        }

        @Override
        public void convertByteCode(
                @NonNull Collection<File> inputs,
                @NonNull File outDexFolder,
                boolean multidex,
                @Nullable File mainDexList,
                @NonNull DexOptions dexOptions,
                @NonNull ProcessOutputHandler processOutputHandler,
                int minSdkVersion)
                throws IOException, InterruptedException, ProcessException {
            Files.write(
                    "Dexed content", new File(outDexFolder, "classes.dex"), StandardCharsets.UTF_8);
        }
    }
}
