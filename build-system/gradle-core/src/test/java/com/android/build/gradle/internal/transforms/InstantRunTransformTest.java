/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Tests for the {@link InstantRunTransform} class
 */
public class InstantRunTransformTest {

    @Mock
    Context context;

    @Mock
    VariantScope variantScope;

    @Mock
    GlobalScope globalScope;

    @Mock
    InstantRunBuildContext instantRunBuildContext;

    @Mock
    Project project;

    @Mock
    WaitableExecutor<Void> executor;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUpMock() {
        MockitoAnnotations.initMocks(this);
        AndroidBuilder mockBuilder = Mockito.mock(AndroidBuilder.class);
        when(mockBuilder.getBootClasspath(true)).thenReturn(ImmutableList.of());
        when(globalScope.getAndroidBuilder()).thenReturn(mockBuilder);
        when(globalScope.getProject()).thenReturn(project);
        when(variantScope.getGlobalScope()).thenReturn(globalScope);
        when(variantScope.getInstantRunBuildContext()).thenReturn(instantRunBuildContext);
        when(variantScope.getInstantRunBootClasspath()).thenReturn(ImmutableList.of());
        when(instantRunBuildContext.getBuildMode()).thenReturn(InstantRunBuildMode.HOT_WARM);

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Callable callable = (Callable) args[0];
            callable.call();
            return null;
        }).when(executor).execute(anyCallable());
    }

    @Test
    public void classLoaderOverrideTest()
            throws TransformException, InterruptedException, IOException {

        // now set up a funky classloader.
        ClassLoader classLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());

        InstantRunTransform transform = new InstantRunTransform(executor, variantScope) {

            @Nullable
            @Override
            protected Void transformToClasses2Format(@NonNull File inputDir,
                    @NonNull File inputFile, @NonNull File outputDir,
                    @NonNull Status change) throws IOException {
                assertNotEquals(classLoader, Thread.currentThread().getContextClassLoader());
                return null;
            }

            @Nullable
            @Override
            protected Void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                assertNotEquals(classLoader, Thread.currentThread().getContextClassLoader());
                return null;
            }
        };

        ImmutableList.Builder<TransformInput> input = ImmutableList.builder();

        input.add(new TransformInput() {

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(new DirectoryInputForTests() {
                    @NonNull
                    @Override
                    public Map<File, Status> getChangedFiles() {
                        return ImmutableMap.<File, Status>builder()
                                .put(new File("/tmp/foo/bar/Changed.class"), Status.CHANGED)
                                .put(new File("/tmp/foo/bar/Added.class"), Status.ADDED)
                                .build();
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return new File("/tmp");
                    }
                });
            }

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }
        });

        // make sure our executor is called with the right class loader context.
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Callable callable = (Callable) args[0];
            // check that the context class loader is not the one we just created above.
            assertNotEquals(classLoader, Thread.currentThread().getContextClassLoader());
            callable.call();
            return null;
        }).when(executor).execute(anyCallable());

        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            TransformOutputProvider transformOutput =
                    createTransformOutputProvider(new File("out"), new File("out.3"));

            assertEquals(classLoader, Thread.currentThread().getContextClassLoader());
            transform.transform(new TransformInvocationBuilder(context)
                    .addInputs(input.build())
                    .addOutputProvider(transformOutput)
                    .setIncrementalMode(true)
                    .build());
        } finally {
            Thread.currentThread().setContextClassLoader(currentClassLoader);
        }
    }

    @Test
    public void incrementalModeTest() throws TransformException, InterruptedException, IOException {

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        InstantRunTransform transform = createTransform(
                filesElectedForClasses2Transformation, filesElectedForClasses3Transformation);

        ImmutableList.Builder<TransformInput> input = ImmutableList.builder();

        input.add(new TransformInput() {

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(new DirectoryInputForTests() {
                    @NonNull
                    @Override
                    public Map<File, Status> getChangedFiles() {
                        return ImmutableMap.<File, Status>builder()
                                .put(new File("/tmp/foo/bar/Changed.class"), Status.CHANGED)
                                .put(new File("/tmp/foo/bar/Added.class"), Status.ADDED)
                                .build();
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return new File("/tmp");
                    }
                });
            }

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }
        });

        TransformOutputProvider transformOutput =
                createTransformOutputProvider(temporaryFolder.newFolder("out"),
                        temporaryFolder.newFolder("out.3"));

        transform.transform(new TransformInvocationBuilder(context)
            .addInputs(input.build())
            .addOutputProvider(transformOutput)
            .setIncrementalMode(true)
            .build());

        ImmutableList<File> processedFiles = filesElectedForClasses2Transformation.build();
        assertEquals("Wrong number of files elected for classes 2 processing", 2, processedFiles.size());
        assertEquals("Output File path",
                FileUtils.toSystemDependentPath("/tmp/foo/bar/Changed.class"),
                processedFiles.get(0).getPath());
        assertEquals("Output File path",
                FileUtils.toSystemDependentPath("/tmp/foo/bar/Added.class"),
                processedFiles.get(1).getPath());
        processedFiles = filesElectedForClasses3Transformation.build();
        assertEquals("Wrong number of files elected for classes 3 processing", 1, processedFiles.size());
        assertEquals("Output File path",
                FileUtils.toSystemDependentPath("/tmp/foo/bar/Changed.class"),
                processedFiles.get(0).getPath());
    }

    @Test
    public void fullModeTest()
            throws IOException, TransformException, InterruptedException {

        final File inputFolder = temporaryFolder.newFolder("input");

        final File originalFile = createEmptyFile(inputFolder, "com/example/A.class");
        final File otherFile = createEmptyFile(inputFolder, "com/other/B.class");

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        when(instantRunBuildContext.getVerifierResult()).thenReturn(
                InstantRunVerifierStatus.COLD_SWAP_REQUESTED);
        when(instantRunBuildContext.getBuildMode()).thenReturn(InstantRunBuildMode.COLD);

        InstantRunTransform transform = createTransform(
                filesElectedForClasses2Transformation, filesElectedForClasses2Transformation);

        ImmutableList.Builder<TransformInput> input = ImmutableList.builder();

        input.add(new TransformInput() {

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(new DirectoryInputForTests() {
                    @NonNull
                    @Override
                    public Map<File, Status> getChangedFiles() {
                        return ImmutableMap.of();
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return inputFolder;
                    }
                });
            }

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }
        });

        TransformOutputProvider transformOutputProvider =
                createTransformOutputProvider(temporaryFolder.newFolder("output"),
                        temporaryFolder.newFolder("outputEnhancedFolder"));

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addInputs(input.build())
                .setIncrementalMode(false)
                .build());

        verify(instantRunBuildContext).setVerifierStatus(
                    Mockito.eq(InstantRunVerifierStatus.BUILD_NOT_INCREMENTAL));

        ImmutableList<File> processedFiles = filesElectedForClasses2Transformation.build();
        assertEquals("Wrong number of files elected for processing", 2, processedFiles.size());
        // check produced files.
        assertThat(processedFiles).containsAllOf(originalFile, otherFile);

        assertThat(filesElectedForClasses3Transformation.build()).isEmpty();
    }

    @Test
    public void dirtyFullModeTest()
            throws IOException, TransformException, InterruptedException {

        final File inputFolder = temporaryFolder.newFolder("input");
        final File originalFile = createEmptyFile(inputFolder, "com/example/A.class");
        final File otherFile = createEmptyFile(inputFolder, "com/other/B.class");

        final File outputFolder = temporaryFolder.newFolder("output");
        final File outputFile = createEmptyFile(outputFolder, "com/example/A.class");

        final File outputEnhancedFolder = temporaryFolder.newFolder("outputEnhanced");
        final File outputEnhancedFile =
                createEmptyFile(outputEnhancedFolder, "com/example/A$override.class");

        assertTrue(outputFile.exists());
        assertTrue(outputEnhancedFile.exists());

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        when(instantRunBuildContext.getVerifierResult()).thenReturn(
                InstantRunVerifierStatus.COLD_SWAP_REQUESTED);
        when(instantRunBuildContext.getBuildMode()).thenReturn(InstantRunBuildMode.COLD);

        InstantRunTransform transform = createTransform(
                filesElectedForClasses2Transformation, filesElectedForClasses2Transformation);

        ImmutableList.Builder<TransformInput> input = ImmutableList.builder();

        input.add(new TransformInput() {

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(new DirectoryInputForTests() {
                    @NonNull
                    @Override
                    public Map<File, Status> getChangedFiles() {
                        return ImmutableMap.of();
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return inputFolder;
                    }
                });
            }

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }
        });

        TransformOutputProvider transformOutputProvider =
                createTransformOutputProvider(outputFolder, outputEnhancedFolder);

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addInputs(input.build())
                .setIncrementalMode(false)
                .build());

        verify(instantRunBuildContext).setVerifierStatus(
                Mockito.eq(InstantRunVerifierStatus.BUILD_NOT_INCREMENTAL));

        ImmutableList<File> processedFiles = filesElectedForClasses2Transformation.build();
        assertEquals("Wrong number of files elected for processing", 2, processedFiles.size());
        // check produced files.
        assertThat(processedFiles).containsAllOf(originalFile, otherFile);

        assertThat(filesElectedForClasses3Transformation.build()).isEmpty();
        assertThat(outputEnhancedFile.listFiles()).isNull();
    }

    @Test
    public void fileDeletionInIncrementalModeTest()
            throws TransformException, InterruptedException, IOException {
        fileDeletionTest(true /* incrementalMode */);
    }

    @Test
    public void fileDeletionInFullModeTest()
            throws TransformException, InterruptedException, IOException {
        fileDeletionTest(false /* incrementalMode */);
    }

    private void fileDeletionTest(boolean incrementalMode)
            throws IOException, TransformException, InterruptedException {

        final File inputFolder = temporaryFolder.newFolder("input");
        final File originalFile = createEmptyFile(inputFolder, "com/example/A.class");
        final File otherFile = createEmptyFile(inputFolder, "com/other/B.class");

        final File outputFolder = temporaryFolder.newFolder("output");
        final File outputFile = createEmptyFile(outputFolder, "com/example/A.class");

        final File outputEnhancedFolder = temporaryFolder.newFolder("outputEnhanced");
        final File outputEnhancedFile =
                createEmptyFile(outputEnhancedFolder, "com/example/A$override.class");

        assertTrue(outputFile.exists());
        assertTrue(outputEnhancedFile.exists());

        final ImmutableList.Builder<File> filesElectedForClasses2Transformation = ImmutableList.builder();
        final ImmutableList.Builder<File> filesElectedForClasses3Transformation = ImmutableList.builder();

        InstantRunTransform transform = createTransform(
                filesElectedForClasses2Transformation, filesElectedForClasses3Transformation);

        ImmutableList.Builder<TransformInput> input = ImmutableList.builder();

        input.add(new TransformInput() {

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(new DirectoryInputForTests() {
                    @NonNull
                    @Override
                    public Map<File, Status> getChangedFiles() {
                        return incrementalMode
                            ? ImmutableMap.<File, Status>builder()
                                .put(otherFile, Status.ADDED)
                                .put(originalFile, Status.REMOVED)
                                .build()
                            : ImmutableMap.of();
                    }

                    @NonNull
                    @Override
                    public File getFile() {
                        return inputFolder;
                    }
                });
            }

            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of();
            }
        });

        TransformOutputProvider transformOutputProvider =
                createTransformOutputProvider(outputFolder, outputEnhancedFolder);

        // delete the "deleted" file.
        FileUtils.delete(originalFile);
        if (!incrementalMode) {
            when(instantRunBuildContext.getBuildMode()).thenReturn(InstantRunBuildMode.COLD);
        }

        transform.transform(new TransformInvocationBuilder(context)
                .addOutputProvider(transformOutputProvider)
                .addInputs(input.build())
                .setIncrementalMode(incrementalMode)
                .build());


        if (!incrementalMode) {
            verify(instantRunBuildContext).setVerifierStatus(
                    Mockito.eq(InstantRunVerifierStatus.BUILD_NOT_INCREMENTAL));
        }

        ImmutableList<File> processedFiles = filesElectedForClasses2Transformation.build();
        assertEquals("Wrong number of files elected for processing", 1, processedFiles.size());
        assertThat(processedFiles.get(0).getAbsolutePath()).contains("com/other/B.class");

        assertFalse("Incremental support class file should have been deleted.", outputFile.exists());
        assertFalse("Enhanced class file should have been deleted.", outputEnhancedFile.exists());
    }

    private static File createEmptyFile(File folder, String path)
            throws IOException {
        File file = new File(folder, path);
        Files.createParentDirs(file);
        Files.touch(file);
        return file;
    }

    private abstract static class DirectoryInputForTests implements DirectoryInput {

        @NonNull
        @Override
        public String getName() {
            return "test";
        }

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return ImmutableSet.of(DefaultContentType.CLASSES);
        }

        @NonNull
        @Override
        public Set<Scope> getScopes() {
            return ImmutableSet.of(Scope.PROJECT);
        }

    }

    private static TransformOutputProvider createTransformOutputProvider(File classes2, File classes3) {
        return new TransformOutputProvider() {
            @Override
            public void deleteAll() throws IOException {

            }

            @NonNull
            @Override
            public File getContentLocation(@NonNull String name,
                    @NonNull Set<ContentType> types,
                    @NonNull Set<? super Scope> scopes, @NonNull Format format) {
                assertThat(types).hasSize(1);
                if (types.iterator().next().equals(QualifiedContent.DefaultContentType.CLASSES)) {
                    return classes2;
                } else {
                    return classes3;
                }
            }
        };
    }

    private InstantRunTransform createTransform(
            ImmutableList.Builder<File> filesElectedForClasses2Transformation,
            ImmutableList.Builder<File> filesElectedForClasses3Transformation) {

        return new InstantRunTransform(executor, variantScope) {
            @Override
            @Nullable
            protected Void transformToClasses2Format(
                    @NonNull File inputDir,
                    @NonNull File inputFile,
                    @NonNull File outputDir,
                    @NonNull Status status)
                    throws IOException {
                filesElectedForClasses2Transformation.add(inputFile);
                return null;
            }

            @Override
            @Nullable
            protected Void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
                    throws IOException {
                filesElectedForClasses3Transformation.add(inputFile);
                return null;
            }

            @Override
            protected void wrapUpOutputs(File classes2Folder, File classes3Folder)
                    throws IOException {

            }
        };
    }

    @SuppressWarnings({"unchecked"})
    private static <T> Callable<T> anyCallable() {
        return (Callable<T>) any(Callable.class);
    }
}
