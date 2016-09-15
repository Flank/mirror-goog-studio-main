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
import static org.junit.Assert.assertEquals;
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
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.internal.utils.FileCache;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit test for {@link DexTransform}. */
@RunWith(MockitoJUnitRunner.class)
public class DexTransformTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @NonNull
    private final AndroidBuilder fakeAndroidBuilder =
            new FakeAndroidBuilder(
                    "projectId",
                    "createdBy",
                    mock(ProcessExecutor.class),
                    mock(JavaProcessExecutor.class),
                    mock(ErrorReporter.class),
                    mock(ILogger.class),
                    false /* verboseExec */);

    @Test
    public void testPreDexLibraries() throws IOException, TransformException, InterruptedException {
        // Inputs for dexing
        JarInput externalLibraryJarInput =
                getJarInput(testDir.newFile("foo"), QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        JarInput nonExternalLibraryJarInput =
                getJarInput(testDir.newFile("bar"), QualifiedContent.Scope.PROJECT);
        DirectoryInput directoryInput =
                getDirectoryInput(
                        testDir.newFolder("baz"), QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        Files.write("Foo content", externalLibraryJarInput.getFile(), StandardCharsets.UTF_8);
        Files.write("Bar content", nonExternalLibraryJarInput.getFile(), StandardCharsets.UTF_8);
        Files.write(
                "Baz content", new File(directoryInput.getFile(), "baz"), StandardCharsets.UTF_8);

        // Output directory of pre-dexing
        File preDexOutputDir = testDir.newFolder("pre-dex");

        // Output directory of dexing
        File dexOutputDir = testDir.newFolder("dex");

        // The build cache
        FileCache buildCache =
                FileCache.getInstanceWithSingleProcessLocking(testDir.newFolder("cache"));

        // Build tools revision
        Revision buildToolsRevision = new Revision(24, 0, 0);

        // Run dexing
        runDexing(
                ImmutableList.of(externalLibraryJarInput, nonExternalLibraryJarInput),
                ImmutableList.of(directoryInput),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                buildToolsRevision);

        // Assert pre-dex results
        File[] preDexOutputFiles = preDexOutputDir.listFiles();
        assertEquals(3, preDexOutputFiles.length);
        File preDexedExternalLibraryJarInput = null;
        File preDexedNonExternalLibraryJarInput = null;
        File preDexedDirectoryInput = null;
        for (int i = 0; i < 3; i++) {
            if (preDexOutputFiles[i].getName().contains("foo")) {
                preDexedExternalLibraryJarInput = preDexOutputFiles[i];
            } else if (preDexOutputFiles[i].getName().contains("bar")) {
                preDexedNonExternalLibraryJarInput = preDexOutputFiles[i];
            } else if (preDexOutputFiles[i].getName().contains("baz")) {
                preDexedDirectoryInput = preDexOutputFiles[i];
            }
        }
        assertThat(preDexedExternalLibraryJarInput).hasContents("Pre-dexed content of Foo content");
        assertThat(preDexedNonExternalLibraryJarInput)
                .hasContents("Pre-dexed content of Bar content");
        assertThat(preDexedDirectoryInput).hasContents("Pre-dexed content of Baz content");

        // Assert dex results
        File[] dexOutputFiles = dexOutputDir.listFiles();
        assertEquals(1, dexOutputFiles.length);
        File dexOutputFile = dexOutputFiles[0];
        assertThat(dexOutputFile).hasContents("Dexed content");

        // Assert cache results
        File[] cachedFiles = buildCache.getCacheDirectory().listFiles();
        assertEquals(1, cachedFiles.length);
        File cachedPreDexedExternalLibraryJarInput = new File(cachedFiles[0], "output");
        assertThat(cachedPreDexedExternalLibraryJarInput)
                .hasContents("Pre-dexed content of Foo content");

        long cachedFileTimestamp = cachedPreDexedExternalLibraryJarInput.lastModified();
        assertThat(preDexedExternalLibraryJarInput).wasModifiedAt(cachedFileTimestamp);
        long preDexedNonExternalLibraryJarInputTimestamp =
                preDexedNonExternalLibraryJarInput.lastModified();
        long preDexedDirectoryInputTimestamp = preDexedDirectoryInput.lastModified();

        // Re-run dexing
        TestUtils.waitForFileSystemTick();
        runDexing(
                ImmutableList.of(externalLibraryJarInput, nonExternalLibraryJarInput),
                ImmutableList.of(directoryInput),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                buildToolsRevision);

        // Assert pre-dex results
        assertEquals(3, preDexOutputDir.listFiles().length);
        assertThat(preDexedExternalLibraryJarInput).hasContents("Pre-dexed content of Foo content");
        assertThat(preDexedNonExternalLibraryJarInput)
                .hasContents("Pre-dexed content of Bar content");
        assertThat(preDexedDirectoryInput).hasContents("Pre-dexed content of Baz content");

        // Assert dex results
        assertEquals(1, dexOutputDir.listFiles().length);
        assertThat(dexOutputFile).hasContents("Dexed content");

        // Assert cache results
        assertEquals(1, buildCache.getCacheDirectory().listFiles().length);
        assertThat(cachedPreDexedExternalLibraryJarInput)
                .hasContents("Pre-dexed content of Foo content");

        assertThat(cachedPreDexedExternalLibraryJarInput).wasModifiedAt(cachedFileTimestamp);
        assertThat(preDexedExternalLibraryJarInput).wasModifiedAt(cachedFileTimestamp);
        assertThat(preDexedNonExternalLibraryJarInput)
                .isNewerThan(preDexedNonExternalLibraryJarInputTimestamp);
        assertThat(preDexedDirectoryInput).isNewerThan(preDexedDirectoryInputTimestamp);
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
                FileCache.getInstanceWithSingleProcessLocking(testDir.newFolder("cache"));

        // Build tools revision
        Revision buildToolsRevision = new Revision(24, 0, 0);

        // Run dexing
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                buildToolsRevision);

        // Assert cache results
        assertEquals(2, buildCache.getCacheDirectory().listFiles().length);

        // Re-run pre-dexing with the same input files and build tools revision
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                buildToolsRevision);
        // Expect the cache to remain the same
        assertEquals(2, buildCache.getCacheDirectory().listFiles().length);

        // Re-run pre-dexing with the same input files and different build tools revision
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                new Revision(25, 0, 0));
        // Expect the cache to contain 2 more entries
        assertEquals(4, buildCache.getCacheDirectory().listFiles().length);

        // Re-run pre-dexing with the same input files, with the contents of one file changed
        Files.write("New foo content", fooInput.getFile(), StandardCharsets.UTF_8);
        runDexing(
                ImmutableList.of(fooInput, barInput),
                ImmutableList.of(),
                preDexOutputDir,
                dexOutputDir,
                buildCache,
                buildToolsRevision);
        Files.write("Foo content", fooInput.getFile(), StandardCharsets.UTF_8);
        // Expect the cache to contain 1 more entry
        assertEquals(5, buildCache.getCacheDirectory().listFiles().length);

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
                buildToolsRevision);
        // Expect the cache to contain 1 more entry
        assertEquals(6, buildCache.getCacheDirectory().listFiles().length);

        // Re-run pre-dexing with 2 exploded-aar files as inputs
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
                buildToolsRevision);
        // Expect the cache to contain 1 more entry
        assertEquals(7, buildCache.getCacheDirectory().listFiles().length);

        // Re-run pre-dexing with 2 instant-run.jar files as inputs
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
                buildToolsRevision);
        // Expect the cache to contain 1 more entry
        assertEquals(8, buildCache.getCacheDirectory().listFiles().length);
    }

    private void runDexing(
            @NonNull Collection<JarInput> jarInputs,
            @NonNull Collection<DirectoryInput> directoryInputs,
            @NonNull File preDexOutputDir,
            @NonNull File dexOutputDir,
            @NonNull FileCache buildCache,
            @NonNull Revision buildToolsRevision)
            throws TransformException, InterruptedException, IOException {
        BuildToolInfo mockBuildToolInfo = mock(BuildToolInfo.class);
        when(mockBuildToolInfo.getRevision()).thenReturn(buildToolsRevision);
        TargetInfo mockTargetInfo = mock(TargetInfo.class);
        when(mockTargetInfo.getBuildTools()).thenReturn(mockBuildToolInfo);
        fakeAndroidBuilder.setTargetInfo(mockTargetInfo);

        DexTransform dexTransform =
                new DexTransform(
                        new DefaultDexOptions(),
                        false, // debugMode
                        false, // multiDex
                        null, // mainDexListFile
                        preDexOutputDir,
                        fakeAndroidBuilder,
                        mock(Logger.class),
                        mock(InstantRunBuildContext.class),
                        Optional.of(buildCache));

        TransformInput transformInput = getTransformInput(jarInputs, directoryInputs);
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
                @NonNull ProcessOutputHandler processOutputHandler)
                throws IOException {
            String content =
                    inputFile.isDirectory()
                            ? Files.toString(inputFile.listFiles()[0], StandardCharsets.UTF_8)
                            : Files.toString(inputFile, StandardCharsets.UTF_8);
            Files.write("Pre-dexed content of " + content, outFile, StandardCharsets.UTF_8);
        }

        @Override
        public void convertByteCode(
                @NonNull Collection<File> inputs,
                @NonNull File outDexFolder,
                boolean multidex,
                @Nullable File mainDexList,
                @NonNull DexOptions dexOptions,
                @NonNull ProcessOutputHandler processOutputHandler)
                throws IOException {
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
