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

package com.android.build.gradle.internal.transforms;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.truth.MoreTruth;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Testing the {@link DexArchiveBuilderTransform} and {@link DexMergerTransform}. */
@RunWith(Parameterized.class)
public class DexArchiveBuilderTransformTest {

    @Parameterized.Parameters
    public static Collection<Object[]> setups() {
        return ImmutableList.of(new Object[] {DexerTool.DX}, new Object[] {DexerTool.D8});
    }

    @Parameterized.Parameter public DexerTool dexerTool;

    private static final String PACKAGE = "com/example/tools";

    private Context context;
    private TransformOutputProvider outputProvider;
    private Path out;
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    private final WorkerExecutor workerExecutor =
            new WorkerExecutor() {
                @Override
                public void submit(
                        Class<? extends Runnable> aClass,
                        Action<? super WorkerConfiguration> action) {
                    WorkerConfiguration workerConfiguration =
                            Mockito.mock(WorkerConfiguration.class);
                    ArgumentCaptor<DexArchiveBuilderTransform.DexConversionParameters> captor =
                            ArgumentCaptor.forClass(
                                    DexArchiveBuilderTransform.DexConversionParameters.class);
                    action.execute(workerConfiguration);
                    verify(workerConfiguration).setParams(captor.capture());
                    DexArchiveBuilderTransform.DexConversionWorkAction workAction =
                            new DexArchiveBuilderTransform.DexConversionWorkAction(
                                    captor.getValue());
                    workAction.run();
                }

                @Override
                public void await() throws WorkerExecutionException {
                    // do nothing;
                }
            };

    @Before
    public void setUp() throws IOException {
        context = Mockito.mock(Context.class);
        when(context.getWorkerExecutor()).thenReturn(workerExecutor);

        out = tmpDir.getRoot().toPath().resolve("out");
        Files.createDirectories(out);
        outputProvider = new TestTransformOutputProvider(out);
    }

    @Test
    public void testInitialBuild() throws Exception {
        TransformInput input =
                getInput(
                        getDirInput(
                                tmpDir.getRoot().toPath().resolve("dir_input"),
                                ImmutableList.of(PACKAGE + "/A")),
                        getJarInput(
                                tmpDir.getRoot().toPath().resolve("input.jar"),
                                ImmutableList.of(PACKAGE + "/B")));
        getTransform(null).transform(getInvocation(ImmutableList.of(input), outputProvider));

        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
        List<File> jarDexArchives = FileUtils.find(out.toFile(), Pattern.compile(".*\\.jar"));
        assertThat(jarDexArchives).hasSize(1);
    }

    @Test
    public void testCacheUsedForExternalLibOnly() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        TransformInput input =
                getInput(
                        getDirInput(
                                tmpDir.getRoot().toPath().resolve("dir_input"),
                                ImmutableList.of(PACKAGE + "/A")),
                        getJarInput(
                                tmpDir.getRoot().toPath().resolve("input.jar"),
                                ImmutableList.of(PACKAGE + "/B")));
        DexArchiveBuilderTransform transform = getTransform(userCache);
        transform.transform(getInvocation(ImmutableList.of(input), outputProvider));

        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1);
    }

    @Test
    public void testCacheUsedForLocalJars() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache cache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TestInputsGenerator.jarWithEmptyClasses(inputJar, ImmutableList.of(PACKAGE + "/A"));
        SimpleJarInput jarInput =
                new SimpleJarInput.Builder(inputJar.toFile())
                        .setScopes(ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES))
                        .create();
        TransformInput input = new SimpleJarTransformInput(jarInput);

        DexArchiveBuilderTransform transform = getTransform(cache);
        transform.transform(getInvocation(ImmutableList.of(input), outputProvider));

        assertThat(cacheDir.listFiles(File::isDirectory)).hasLength(1);
    }

    @Test
    public void testEntryRemovedFromTheArchive() throws Exception {
        Path inputDir = tmpDir.getRoot().toPath().resolve("dir_input");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TransformInput input =
                getInput(
                        getDirInput(inputDir, ImmutableList.of(PACKAGE + "/A", PACKAGE + "/B")),
                        getJarInput(inputJar, ImmutableList.of(PACKAGE + "/C")));
        getTransform(null).transform(getInvocation(ImmutableList.of(input), outputProvider));
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isFile();

        // remove the class file
        DirectoryInput deletedFile =
                new TestDirectoryInput(
                        ImmutableMap.of(
                                inputDir.resolve(PACKAGE + "/B.class").toFile(), Status.REMOVED),
                        inputDir.getFileName().toString(),
                        inputDir.toFile(),
                        ImmutableSet.of(),
                        ImmutableSet.of(QualifiedContent.Scope.PROJECT));

        JarInput unchangedJarInput =
                TransformTestHelper.jarBuilder(inputJar.toFile())
                        .setStatus(Status.NOTCHANGED)
                        .build();
        TransformInput updatedInputs = getInput(deletedFile, unchangedJarInput);
        getTransform(null)
                .transform(getInvocation(ImmutableList.of(updatedInputs), outputProvider));
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isNull();
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isFile();
    }

    @Test
    public void testNonIncremental() throws Exception {
        TransformInput input =
                getInput(
                        getDirInput(
                                tmpDir.getRoot().toPath().resolve("dir_input"),
                                ImmutableList.of(PACKAGE + "/A")),
                        getJarInput(
                                tmpDir.getRoot().toPath().resolve("input.jar"),
                                ImmutableList.of(PACKAGE + "/B")));
        getTransform(null)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(ImmutableList.of(input))
                                .addOutputProvider(outputProvider)
                                .setIncrementalMode(false)
                                .build());
        TransformInput secondRunInput =
                getInput(
                        getDirInput(
                                tmpDir.getRoot().toPath().resolve("dir_2_input"),
                                ImmutableList.of(PACKAGE + "/C")),
                        getJarInput(
                                tmpDir.getRoot().toPath().resolve("input.jar"),
                                ImmutableList.of(PACKAGE + "/B")));
        getTransform(null)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(ImmutableList.of(secondRunInput))
                                .addOutputProvider(outputProvider)
                                .setIncrementalMode(false)
                                .build());
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isNull();
    }

    @Test
    public void testCacheKeyInputsChanges() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TestInputsGenerator.jarWithEmptyClasses(inputJar, ImmutableList.of());
        JarInput jarInput =
                TransformTestHelper.jarBuilder(inputJar.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .setStatus(Status.ADDED)
                        .build();
        TransformInput transformInput =
                TransformTestHelper.inputBuilder().addInput(jarInput).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(transformInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();

        DexArchiveBuilderTransform transform = getTransform(userCache, 19, true);
        transform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1);

        DexArchiveBuilderTransform minChanged = getTransform(userCache, 20, true);
        minChanged.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(2);

        DexArchiveBuilderTransform debuggableChanged = getTransform(userCache, 19, false);
        debuggableChanged.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(3);

        DexArchiveBuilderTransform minAndDebuggableChanged = getTransform(userCache, 20, false);
        minAndDebuggableChanged.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(4);

        DexArchiveBuilderTransform useDifferentDexer =
                new DexArchiveBuilderTransform(
                        new DefaultDexOptions(),
                        new NoOpErrorReporter(),
                        userCache,
                        20,
                        dexerTool == DexerTool.DX ? DexerTool.D8 : DexerTool.DX,
                        true,
                        10,
                        10,
                        false);
        useDifferentDexer.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(5);
    }

    @Test
    public void testIncrementalUnchangedDirInput() throws Exception {
        Path input = tmpDir.newFolder("classes").toPath();
        TestInputsGenerator.dirWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"));

        TransformInput dirInput =
                TransformTestHelper.directoryBuilder(input.toFile())
                        .putChangedFiles(ImmutableMap.of())
                        .build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(dirInput))
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        getTransform(null, 21, true).transform(invocation);
        Truth.assertThat(FileUtils.getAllFiles(out.toFile())).isEmpty();
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(
            @Nullable FileCache userCache, int minSdkVersion, boolean isDebuggable) {
        return new DexArchiveBuilderTransform(
                new DefaultDexOptions(),
                new NoOpErrorReporter(),
                userCache,
                minSdkVersion,
                dexerTool,
                true,
                10,
                10,
                isDebuggable);
    }

    @NonNull
    private DexArchiveBuilderTransform getTransform(@Nullable FileCache userCache) {
        return getTransform(userCache, 1, true);
    }

    private int cacheEntriesCount(@NonNull File cacheDir) {
        File[] files = cacheDir.listFiles(File::isDirectory);
        assertThat(files).isNotNull();
        return files.length;

    }

    @NonNull
    private TransformInvocation getInvocation(
            @NonNull Collection<TransformInput> inputs,
            @NonNull TransformOutputProvider outputProvider) {
        return new TransformInvocationBuilder(context)
                .addInputs(inputs)
                .addOutputProvider(outputProvider)
                .setIncrementalMode(true)
                .build();
    }

    @NonNull
    private TransformInput getInput(
            @NonNull DirectoryInput directoryInput, @NonNull JarInput jarInput) {
        return new TransformInput() {
            @NonNull
            @Override
            public Collection<JarInput> getJarInputs() {
                return ImmutableList.of(jarInput);
            }

            @NonNull
            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of(directoryInput);
            }
        };
    }

    @NonNull
    private DirectoryInput getDirInput(@NonNull Path path, @NonNull Collection<String> classes)
            throws Exception {
        TestInputsGenerator.dirWithEmptyClasses(path, classes);

        return new TestDirectoryInput(
                classes.stream()
                        .collect(
                                Collectors.toMap(
                                        e -> path.resolve(e + SdkConstants.DOT_CLASS).toFile(),
                                        e -> Status.ADDED)),
                path.getFileName().toString(),
                path.toFile(),
                ImmutableSet.of(),
                ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES));
    }

    @NonNull
    private JarInput getJarInput(@NonNull Path path, @NonNull Collection<String> classes)
            throws Exception {
        TestInputsGenerator.jarWithEmptyClasses(path, classes);
        return new SimpleJarInput.Builder(path.toFile())
                .setScopes(ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES))
                .create();
    }

    private static final class TestDirectoryInput implements DirectoryInput {

        @NonNull private final Map<File, Status> changedFiles;
        @NonNull private final String name;
        @NonNull private final File file;
        @NonNull private final Set<ContentType> contentTypes;
        @NonNull private final Set<? super Scope> scopes;

        public TestDirectoryInput(
                @NonNull Map<File, Status> changedFiles,
                @NonNull String name,
                @NonNull File file,
                @NonNull Set<ContentType> contentTypes,
                @NonNull Set<? super Scope> scopes) {
            this.changedFiles = changedFiles;
            this.name = name;
            this.file = file;
            this.contentTypes = contentTypes;
            this.scopes = scopes;
        }

        @NonNull
        @Override
        public Map<File, Status> getChangedFiles() {
            return changedFiles;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        @NonNull
        @Override
        public File getFile() {
            return file;
        }

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return contentTypes;
        }

        @NonNull
        @Override
        public Set<? super Scope> getScopes() {
            return scopes;
        }
    }
}
