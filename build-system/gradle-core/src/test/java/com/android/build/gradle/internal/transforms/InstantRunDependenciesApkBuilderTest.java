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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.packaging.PackagerException;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.signing.KeytoolException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link InstantRunDependenciesApkBuilder}
 */
public class InstantRunDependenciesApkBuilderTest {

    @Mock Logger logger;
    @Mock Project project;
    @Mock InstantRunBuildContext buildContext;
    @Mock AndroidBuilder androidBuilder;
    @Mock CoreSigningConfig coreSigningConfig;
    @Mock PackagingScope packagingScope;

    @Mock TargetInfo targetInfo;
    @Mock BuildToolInfo buildTools;

    @Rule public TemporaryFolder outputDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder supportDirectory = new TemporaryFolder();
    @Rule public TemporaryFolder dexFileFolder = new TemporaryFolder();
    @Rule public TemporaryFolder fileCacheDirectory = new TemporaryFolder();

    InstantRunDependenciesApkBuilder instantRunSliceSplitApkBuilder;
    final List<InstantRunSplitApkBuilder.DexFiles> dexFilesList =
            new CopyOnWriteArrayList<>();

    @Before
    public void setUpMock() {
        MockitoAnnotations.initMocks(this);
        when(androidBuilder.getTargetInfo()).thenReturn(targetInfo);
        when(targetInfo.getBuildTools()).thenReturn(buildTools);
        when(buildTools.getPath(BuildToolInfo.PathId.ZIP_ALIGN)).thenReturn("/path/to/zip-align");
        when(packagingScope.getApplicationId()).thenReturn("com.foo.test");
        when(packagingScope.getVersionName()).thenReturn("test_version_name");
        when(packagingScope.getVersionCode()).thenReturn(12345);
    }

    @Before
    public void setup() {
        instantRunSliceSplitApkBuilder =
                new InstantRunDependenciesApkBuilder(
                        logger,
                        project,
                        buildContext,
                        androidBuilder,
                        FileCache.getInstanceWithSingleProcessLocking(fileCacheDirectory.getRoot()),
                        packagingScope,
                        coreSigningConfig,
                        AaptGeneration.AAPT_V2_JNI,
                        new AaptOptions(null, false, null),
                        outputDirectory.getRoot(),
                        supportDirectory.getRoot()) {
                    @NonNull
                    @Override
                    protected File generateSplitApk(@NonNull DexFiles dexFiles)
                            throws IOException, KeytoolException, PackagerException,
                                    InterruptedException, ProcessException, TransformException {
                        dexFilesList.add(dexFiles);
                        return new File("/dev/null");
                    }
                };
    }

    @Test
    public void testTransformInterface() {
        assertThat(instantRunSliceSplitApkBuilder.getScopes())
                .containsExactly(QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        assertThat(instantRunSliceSplitApkBuilder.getInputTypes()).containsExactly(
                ExtendedContentType.DEX);
        assertThat(instantRunSliceSplitApkBuilder.isIncremental()).isTrue();
    }

    @Test
    public void testIncremental() throws IOException, TransformException, InterruptedException {

        TransformOutputProvider transformOutputProvider = new TransformOutputProviderForTests() {};

        File dexFolderOne = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-2.dex"), "some dex");

        File dexFolderTwo = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderTwo, "dexFile2-1.dex"), "some dex");

        File dexFolderThree = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-2.dex"), "some dex");

        TransformInvocation transformInvocation =
                new TransformInvocationForTests(transformOutputProvider) {

                    @Override
                    public boolean isIncremental() {
                        return true;
                    }

                    @NonNull
                    @Override
                    public Collection<TransformInput> getInputs() {
                        return ImmutableSet.of(new TransformInput() {
                            @NonNull
                            @Override
                            public Collection<JarInput> getJarInputs() {
                                return ImmutableList.of();
                            }

                            @NonNull
                            @Override
                            public Collection<DirectoryInput> getDirectoryInputs() {
                                return ImmutableList.of(
                                        new DirectoryInputForTests("dex-one",
                                                QualifiedContent.Scope.PROJECT,
                                                dexFolderOne),
                                        new IncrementalInputForTests("dex-two",
                                                QualifiedContent.Scope.SUB_PROJECTS,
                                                dexFolderTwo,
                                                ImmutableMap.of(dexFolderTwo, Status.CHANGED)),
                                        new DirectoryInputForTests("dex-three",
                                                QualifiedContent.Scope.PROJECT,
                                                dexFolderThree));
                            }
                        });
                    }
                };

        instantRunSliceSplitApkBuilder.transform(transformInvocation);
        assertThat(dexFilesList).hasSize(1);
        assertThat(dexFilesList.get(0).getDexFiles()).hasSize(5);
        assertThat(dexFilesList.get(0).getDexFiles().stream().map(File::getName)
                .collect(Collectors.toList()))
                .containsExactly("dexFile1-1.dex", "dexFile1-2.dex", "dexFile2-1.dex",
                        "dexFile3-1.dex", "dexFile3-2.dex");
    }

    @Test
    public void testNonIncremental() throws IOException, TransformException, InterruptedException {

        TransformOutputProvider transformOutputProvider = new TransformOutputProviderForTests() {};

        File dexFolderOne = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-2.dex"), "some dex");

        File dexFolderTwo = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderTwo, "dexFile2-1.dex"), "some dex");

        File dexFolderThree = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-2.dex"), "some dex");

        TransformInvocation transformInvocation =
                new TransformInvocationForTests(transformOutputProvider) {
                    @NonNull
                    @Override
                    public Collection<TransformInput> getInputs() {
                        return ImmutableSet.of(new TransformInput() {
                            @NonNull
                            @Override
                            public Collection<JarInput> getJarInputs() {
                                return ImmutableList.of();
                            }

                            @NonNull
                            @Override
                            public Collection<DirectoryInput> getDirectoryInputs() {
                                return ImmutableList.of(
                                        new DirectoryInputForTests("dex-one",
                                                QualifiedContent.Scope.PROJECT,
                                                dexFolderOne),
                                        new DirectoryInputForTests("dex-two",
                                                QualifiedContent.Scope.SUB_PROJECTS,
                                                dexFolderTwo),
                                        new DirectoryInputForTests("dex-three",
                                                QualifiedContent.Scope.PROJECT,
                                                dexFolderThree));
                            }
                        });
                    }
                };

        instantRunSliceSplitApkBuilder.transform(transformInvocation);
        assertThat(dexFilesList).hasSize(1);
        assertThat(dexFilesList.get(0).getDexFiles()).hasSize(5);
        assertThat(dexFilesList.get(0).getDexFiles().stream().map(File::getName)
                .collect(Collectors.toList()))
                .containsExactly("dexFile1-1.dex", "dexFile1-2.dex", "dexFile2-1.dex",
                        "dexFile3-1.dex", "dexFile3-2.dex");
    }

    /**
     * Test that in incremental mode, if no file of interest has changed, no new apk is produced.
     */
    @Test
    public void testIncrementalNoChangeOfInterest() throws IOException, TransformException, InterruptedException {

        TransformOutputProvider transformOutputProvider = new TransformOutputProviderForTests() {};

        File dexFolderOne = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderOne, "dexFile1-2.dex"), "some dex");

        File dexFolderTwo = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderTwo, "dexFile2-1.dex"), "some dex");

        File dexFolderThree = dexFileFolder.newFolder();
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-1.dex"), "some dex");
        FileUtils.createFile(new File(dexFolderThree, "dexFile3-2.dex"), "some dex");

        TransformInvocation transformInvocation =
                new TransformInvocationForTests(transformOutputProvider) {
                    @Override
                    public boolean isIncremental() {
                        return true;
                    }

                    @NonNull
                    @Override
                    public Collection<TransformInput> getInputs() {
                        return ImmutableSet.of(new TransformInput() {
                            @NonNull
                            @Override
                            public Collection<JarInput> getJarInputs() {
                                return ImmutableList.of();
                            }

                            @NonNull
                            @Override
                            public Collection<DirectoryInput> getDirectoryInputs() {
                                return ImmutableList.of(
                                        new DirectoryInputForTests("dex-one",
                                                QualifiedContent.Scope.PROJECT,
                                                dexFolderOne),
                                        new DirectoryInputForTests("dex-two",
                                                QualifiedContent.Scope.SUB_PROJECTS,
                                                dexFolderTwo),
                                        new DirectoryInputForTests("dex-three",
                                                QualifiedContent.Scope.PROJECT,
                                                dexFolderThree));
                            }
                        });
                    }
                };

        instantRunSliceSplitApkBuilder.transform(transformInvocation);
        assertThat(dexFilesList).hasSize(0);
    }

    public abstract static class TransformOutputProviderForTests implements TransformOutputProvider {

        @Override
        public void deleteAll() throws IOException {
        }

        @NonNull
        @Override
        public File getContentLocation(@NonNull String name,
                @NonNull Set<QualifiedContent.ContentType> types,
                @NonNull Set<? super QualifiedContent.Scope> scopes, @NonNull Format format) {
            fail("Unexpected call to getContentLocation");
            throw new RuntimeException("Unexpected call to getContentLocation");
        }
    }

    private static class TransformInvocationForTests implements TransformInvocation {

        @NonNull
        private final TransformOutputProvider transformOutputProvider;

        private TransformInvocationForTests(
                @NonNull TransformOutputProvider transformOutputProvider) {
            this.transformOutputProvider = transformOutputProvider;
        }

        @NonNull
        @Override
        public Context getContext() {
            return Mockito.mock(Context.class);
        }

        @NonNull
        @Override
        public Collection<TransformInput> getInputs() {
            return ImmutableSet.of();
        }

        @NonNull
        @Override
        public Collection<TransformInput> getReferencedInputs() {
            return ImmutableSet.of();
        }

        @NonNull
        @Override
        public Collection<SecondaryInput> getSecondaryInputs() {
            return ImmutableSet.of();
        }

        @Nullable
        @Override
        public TransformOutputProvider getOutputProvider() {
            return transformOutputProvider;
        }

        @Override
        public boolean isIncremental() {
            return false;
        }
    }

    private static class DirectoryInputForTests implements DirectoryInput {

        private final String name;
        private final ScopeType scopeType;
        private final File file;

        protected DirectoryInputForTests(String name, ScopeType scopeType, File file) {
            this.name = name;
            this.scopeType = scopeType;
            this.file = file;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return ImmutableSet.of(ExtendedContentType.DEX);
        }

        @NonNull
        @Override
        public Set<? super Scope> getScopes() {
            return ImmutableSet.of(scopeType);
        }

        @NonNull
        @Override
        public File getFile() {
            return file;
        }

        @NonNull
        @Override
        public Map<File, Status> getChangedFiles() {
            return ImmutableMap.of();
        }
    }

    private static class IncrementalInputForTests extends DirectoryInputForTests {

        private final Map<File, Status> changedFiles;

        protected IncrementalInputForTests(String name,
                ScopeType scopeType, File file, Map<File, Status> changedFiles) {
            super(name, scopeType, file);
            this.changedFiles = changedFiles;
        }

        @NonNull
        @Override
        public Map<File, Status> getChangedFiles() {
            return changedFiles;
        }
    }
}
