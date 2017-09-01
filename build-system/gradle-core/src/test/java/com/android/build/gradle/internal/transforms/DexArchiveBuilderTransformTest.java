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
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
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
        TransformInput dirInput =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_input"),
                        ImmutableList.of(PACKAGE + "/A"));
        TransformInput jarInput =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setTransformOutputProvider(outputProvider)
                        .setInputs(ImmutableSet.of(dirInput, jarInput))
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(invocation);

        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
        List<File> jarDexArchives = FileUtils.find(out.toFile(), Pattern.compile(".*\\.jar"));
        assertThat(jarDexArchives).hasSize(1);
    }

    @Test
    public void testCacheUsedForExternalLibOnly() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        TransformInput dirInput =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_input"),
                        ImmutableList.of(PACKAGE + "/A"));
        TransformInput jarInput =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(ImmutableSet.of(dirInput, jarInput))
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        DexArchiveBuilderTransform transform = getTransform(userCache);
        transform.transform(invocation);

        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1);
    }

    @Test
    public void testCacheUsedForLocalJars() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache cache = FileCache.getInstanceWithSingleProcessLocking(cacheDir);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TransformInput input = getJarInput(inputJar, ImmutableList.of(PACKAGE + "/A"));

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(input)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        DexArchiveBuilderTransform transform = getTransform(cache);
        transform.transform(invocation);

        assertThat(cacheDir.listFiles(File::isDirectory)).hasLength(1);
    }

    @Test
    public void testEntryRemovedFromTheArchive() throws Exception {
        Path inputDir = tmpDir.getRoot().toPath().resolve("dir_input");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");

        TransformInput dirTransformInput =
                getDirInput(inputDir, ImmutableList.of(PACKAGE + "/A", PACKAGE + "/B"));
        TransformInput jarTransformInput = getJarInput(inputJar, ImmutableList.of(PACKAGE + "/C"));

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(dirTransformInput, jarTransformInput)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(invocation);
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isFile();

        // remove the class file
        TransformInput deletedDirInput =
                TransformTestHelper.directoryBuilder(inputDir.toFile())
                        .putChangedFiles(
                                ImmutableMap.of(
                                        inputDir.resolve(PACKAGE + "/B.class").toFile(),
                                        Status.REMOVED))
                        .setScope(QualifiedContent.Scope.PROJECT)
                        .build();

        TransformInput unchangedJarInput =
                TransformTestHelper.singleJarBuilder(inputJar.toFile())
                        .setStatus(Status.NOTCHANGED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInvocation secondInvocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(deletedDirInput, unchangedJarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        getTransform(null).transform(secondInvocation);
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isNull();
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isFile();
    }

    @Test
    public void testNonIncremental() throws Exception {
        TransformInput dirInput =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_input"),
                        ImmutableList.of(PACKAGE + "/A"));

        TransformInput jarInput =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(dirInput, jarInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .build();
        getTransform(null).transform(invocation);

        TransformInput dir2Input =
                getDirInput(
                        tmpDir.getRoot().toPath().resolve("dir_2_input"),
                        ImmutableList.of(PACKAGE + "/C"));
        TransformInput jar2Input =
                getJarInput(
                        tmpDir.getRoot().toPath().resolve("input.jar"),
                        ImmutableList.of(PACKAGE + "/B"));
        TransformInvocation invocation2 =
                TransformTestHelper.invocationBuilder()
                        .setContext(context)
                        .setInputs(dir2Input, jar2Input)
                        .setIncremental(false)
                        .setTransformOutputProvider(outputProvider)
                        .build();
        getTransform(null).transform(invocation2);
        MoreTruth.assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isNull();
    }

    @Test
    public void testCacheKeyInputsChanges() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);

        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        TransformInput jarInput = getJarInput(inputJar, ImmutableList.of());
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .addInput(jarInput)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();

        DexArchiveBuilderTransform transform = getTransform(userCache, 19, true);
        transform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1);

        DexArchiveBuilderTransform minChangedTransform = getTransform(userCache, 20, true);
        minChangedTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(2);

        DexArchiveBuilderTransform debuggableChangedTransform = getTransform(userCache, 19, false);
        debuggableChangedTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(3);

        DexArchiveBuilderTransform minAndDebuggableChangedTransform =
                getTransform(userCache, 20, false);
        minAndDebuggableChangedTransform.transform(invocation);
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(4);

        DexArchiveBuilderTransform useDifferentDexerTransform =
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
        useDifferentDexerTransform.transform(invocation);
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

    /** Regression test for b/65241720. */
    @Test
    public void testIncrementalWithSharding() throws Exception {
        File cacheDir = FileUtils.join(tmpDir.getRoot(), "cache");
        FileCache userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);
        Path input = tmpDir.getRoot().toPath().resolve("classes.jar");
        TestInputsGenerator.jarWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"));

        TransformInput jarInput =
                TransformTestHelper.singleJarBuilder(input.toFile())
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();

        TransformInvocation noCacheInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(jarInput)
                        .setIncremental(false)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();

        DexArchiveBuilderTransform noCacheTransform = getTransform(userCache);
        noCacheTransform.transform(noCacheInvocation);
        MoreTruth.assertThat(out.resolve("classes.jar.jar")).doesNotExist();

        // clean the output of the previous transform
        FileUtils.cleanOutputDir(out.toFile());

        TransformInvocation fromCacheInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(jarInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        DexArchiveBuilderTransform fromCacheTransform = getTransform(userCache);
        fromCacheTransform.transform(fromCacheInvocation);
        assertThat(FileUtils.getAllFiles(out.toFile())).hasSize(1);
        MoreTruth.assertThat(out.resolve("classes.jar.jar")).exists();

        // modify the file so it is not a build cache hit any more
        Files.deleteIfExists(input);
        TestInputsGenerator.jarWithEmptyClasses(input, ImmutableList.of("test/C"));

        TransformInput changedInput =
                TransformTestHelper.singleJarBuilder(input.toFile())
                        .setStatus(Status.CHANGED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                        .build();
        TransformInvocation changedInputInvocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(changedInput)
                        .setIncremental(true)
                        .setTransformOutputProvider(outputProvider)
                        .setContext(context)
                        .build();
        DexArchiveBuilderTransform changedInputTransform = getTransform(userCache);
        changedInputTransform.transform(changedInputInvocation);
        MoreTruth.assertThat(out.resolve("classes.jar.jar")).doesNotExist();
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
    private TransformInput getDirInput(@NonNull Path path, @NonNull Collection<String> classes)
            throws Exception {
        TestInputsGenerator.dirWithEmptyClasses(path, classes);

        return TransformTestHelper.directoryBuilder(path.toFile())
                .setContentType(QualifiedContent.DefaultContentType.CLASSES)
                .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .putChangedFiles(
                        classes.stream()
                                .collect(
                                        Collectors.toMap(
                                                e ->
                                                        path.resolve(e + SdkConstants.DOT_CLASS)
                                                                .toFile(),
                                                e -> Status.ADDED)))
                .build();
    }

    @NonNull
    private TransformInput getJarInput(@NonNull Path path, @NonNull Collection<String> classes)
            throws Exception {
        TestInputsGenerator.jarWithEmptyClasses(path, classes);
        return TransformTestHelper.singleJarBuilder(path.toFile())
                .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
                .setStatus(Status.ADDED)
                .build();
    }
}
