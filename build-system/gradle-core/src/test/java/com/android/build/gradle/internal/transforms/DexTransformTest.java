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
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexingMode;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        // Inputs for dexing
        JarInput nonSnapshotExternalLibraryJarInput =
                getJarInput(
                        testDir.newFile("nonSnapshotExtLibJar"),
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        JarInput snapshotExternalLibraryJarInput =
                getJarInput(
                        new File(testDir.newFolder("1.0-SNAPSHOT"), "snapshotExtLibJar"),
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        JarInput nonExternalLibraryJarInput =
                getJarInput(
                        testDir.newFile("nonExtLibJar"),
                        QualifiedContent.Scope.PROJECT);
        DirectoryInput directoryInput =
                getDirectoryInput(
                        testDir.newFolder("dirInput"),
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        Files.write(
                "nonSnapshotExtLibJar",
                nonSnapshotExternalLibraryJarInput.getFile(),
                StandardCharsets.UTF_8);
        Files.write(
                "snapshotExtLibJar",
                snapshotExternalLibraryJarInput.getFile(),
                StandardCharsets.UTF_8);
        Files.write(
                "nonExtLibJar",
                nonExternalLibraryJarInput.getFile(),
                StandardCharsets.UTF_8);
        Files.write(
                "dirInput",
                new File(directoryInput.getFile(), "baz"),
                StandardCharsets.UTF_8);

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
                        nonExternalLibraryJarInput),
                ImmutableList.of(directoryInput),
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
                        nonExternalLibraryJarInput),
                ImmutableList.of(directoryInput),
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
        JarInput fooInput =
                getJarInput(testDir.newFile("foo"), QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        JarInput barInput =
                getJarInput(testDir.newFile("bar"), QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        Files.write("Foo content", fooInput.getFile(), StandardCharsets.UTF_8);
        Files.write("Bar content", barInput.getFile(), StandardCharsets.UTF_8);

        // Output directory of pre-dexing
        File preDexOutputDir = testDir.newFolder("pre-dex");

        // Output directory of dexing
        File dexOutputDir = testDir.newFolder("dex");

        // The build cache
        FileCache buildCache =
                FileCache.getInstanceWithMultiProcessLocking(testDir.newFolder("cache"));

        // Run dexing
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);

        // Assert cache results, expect that 2 entries are created
        assertEquals(2, countCacheEntries(buildCache));

        // Re-run pre-dexing with the same input files and build tools revision
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        // Expect the cache to remain the same
        assertEquals(2, countCacheEntries(buildCache));

        // Re-run pre-dexing with the same input files and different build tools revision
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                new Revision(AndroidBuilder.MIN_BUILD_TOOLS_REV.getMajor() + 1));
        // Expect the cache to contain 2 more entries
        assertEquals(4, countCacheEntries(buildCache));

        // Re-run pre-dexing with the same input files, with the contents of one file changed
        Files.write("New foo content", fooInput.getFile(), StandardCharsets.UTF_8);
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                AndroidBuilder.MIN_BUILD_TOOLS_REV);
        Files.write("Foo content", fooInput.getFile(), StandardCharsets.UTF_8);
        // Expect the cache to contain 1 more entry
        assertEquals(5, countCacheEntries(buildCache));

        // Re-run pre-dexing with a new input file with the same contents
        JarInput bazInput =
                getJarInput(testDir.newFile("baz"), QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        Files.write("Foo content", fooInput.getFile(), StandardCharsets.UTF_8);
        runDexing(
                ImmutableList.of(fooInput, barInput, bazInput),
                ImmutableList.of(),
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
        JarInput explodedAarJar1 =
                getJarInput(explodedAarFile1, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        JarInput explodedAarJar2 =
                getJarInput(explodedAarFile2, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        runDexing(
                ImmutableList.of(explodedAarJar1, explodedAarJar2),
                ImmutableList.of(),
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
        JarInput instantRunJar1 =
                getJarInput(instantRunFile1, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        JarInput instantRunJar2 =
                getJarInput(instantRunFile2, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        runDexing(
                ImmutableList.of(instantRunJar1, instantRunJar2),
                ImmutableList.of(),
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
        JarInput nonSnapshotExternalLibraryJarInput =
                getJarInput(
                        testDir.newFile("nonSnapshotExtLibJar"),
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        DirectoryInput directoryInput =
                getDirectoryInput(
                        testDir.newFolder("dirInput"),
                        QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        Files.write(
                "nonSnapshotExtLibJar",
                nonSnapshotExternalLibraryJarInput.getFile(),
                StandardCharsets.UTF_8);
        Files.write(
                "dirInput",
                new File(directoryInput.getFile(), "baz"),
                StandardCharsets.UTF_8);

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
                    ImmutableList.of(nonSnapshotExternalLibraryJarInput),
                    ImmutableList.of(directoryInput),
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
            @NonNull Collection<JarInput> jarInputs,
            @NonNull Collection<DirectoryInput> directoryInputs,
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
        runPreDexing(
                fakeAndroidBuilder,
                jarInputs,
                directoryInputs,
                preDexOutputDir,
                buildCache,
                preDexNames);
        List<JarInput> preDexedInputs =
                preDexNames
                        .stream()
                        .map(
                                name ->
                                        getJarInput(
                                                FileUtils.join(preDexOutputDir, name),
                                                QualifiedContent.Scope.EXTERNAL_LIBRARIES))
                        .collect(Collectors.toList());

        // no merge dex files
        DexTransform dexTransform =
                new DexTransform(
                        new DefaultDexOptions(),
                        new DexingMode(DexingType.MONO_DEX),
                        true,
                        null, // mainDexListFile
                        targetInfo,
                        byteCodeConverter,
                        mock(ErrorReporter.class));

        TransformInput transformInput = getTransformInput(preDexedInputs, ImmutableList.of());
        TransformOutputProvider mockTransformOutputProvider = mock(TransformOutputProvider.class);
        when(mockTransformOutputProvider.getContentLocation(any(), any(), any(), any()))
                .thenReturn(dexOutputDir);
        TransformInvocation transformInvocation =
                new TransformInvocationBuilder(mock(Context.class))
                        .addInputs(ImmutableList.of(transformInput))
                        .addOutputProvider(mockTransformOutputProvider)
                        .build();

        dexTransform.transform(transformInvocation);
    }

    private static void runPreDexing(
            @NonNull AndroidBuilder fakeAndroidBuilder,
            @NonNull Collection<JarInput> jarInputs,
            @NonNull Collection<DirectoryInput> directoryInputs,
            @NonNull File preDexOutputDir,
            @NonNull FileCache buildCache,
            @NonNull List<String> preDexNames)
            throws TransformException, InterruptedException, IOException {
        PreDexTransform preDexTransform =
                new PreDexTransform(
                        new DefaultDexOptions(),
                        fakeAndroidBuilder,
                        buildCache,
                        new DexingMode(DexingType.MONO_DEX),
                        false);

        TransformInput transformInput = getTransformInput(jarInputs, directoryInputs);
        TransformOutputProvider mockTransformOutputProvider = mock(TransformOutputProvider.class);
        when(mockTransformOutputProvider.getContentLocation(any(), any(), any(), any()))
                .thenReturn(
                        FileUtils.join(preDexOutputDir, preDexNames.get(0)),
                        FileUtils.join(preDexOutputDir, preDexNames.get(1)),
                        FileUtils.join(preDexOutputDir, preDexNames.get(2)),
                        FileUtils.join(preDexOutputDir, preDexNames.get(3)));
        TransformInvocation transformInvocation =
                new TransformInvocationBuilder(mock(Context.class))
                        .addInputs(ImmutableList.of(transformInput))
                        .addOutputProvider(mockTransformOutputProvider)
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
                @Nullable Integer minSdkVersion)
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
                @Nullable Integer minSdkVersion)
                throws IOException, InterruptedException, ProcessException {
            Files.write(
                    "Dexed content", new File(outDexFolder, "classes.dex"), StandardCharsets.UTF_8);
        }
    }

    private static TransformInput getTransformInput(
            @NonNull Collection<JarInput> jarInputs,
            @NonNull Collection<DirectoryInput> directoryInputs) {
        return new TransformInput() {

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return jarInputs;
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return directoryInputs;
            }
        };
    }

    private static JarInput getJarInput(
            @NonNull File inputJar, @NonNull QualifiedContent.Scope scope) {
        return new JarInput() {

            @NonNull
            @Override
            public String getName() {
                return inputJar.getName();
            }

            @NonNull
            @Override
            public File getFile() {
                return inputJar;
            }

            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
            }

            @NonNull
            @Override
            public Set<Scope> getScopes() {
                return ImmutableSet.of(scope);
            }

            @NonNull
            @Override
            public Status getStatus() {
                return Status.NOTCHANGED;
            }
        };
    }

    private static DirectoryInput getDirectoryInput(
            @NonNull File inputDir, @NonNull QualifiedContent.Scope scope) {
        return new DirectoryInput() {

            @NonNull
            @Override
            public String getName() {
                return inputDir.getName();
            }

            @NonNull
            @Override
            public File getFile() {
                return inputDir;
            }

            @NonNull
            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
            }

            @NonNull
            @Override
            public Set<Scope> getScopes() {
                return ImmutableSet.of(scope);
            }

            @NonNull
            @Override
            public Map<File, Status> getChangedFiles() {
                return ImmutableMap.of();
            }
        };
    }
}
