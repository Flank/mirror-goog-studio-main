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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.mockito.Mockito.when;

import android.databinding.tool.util.Preconditions;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchive;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderConfig;
import com.android.builder.dexing.DexArchives;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.dx.command.dexer.DxContext;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.gradle.api.file.FileCollection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for the {@link DexMergerTransform}. */
public class DexMergerTransformTest {

    private static final String PKG = "com/example/tools";
    private static final int NUM_INPUTS = 10;

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    private Context context;
    private TransformOutputProvider outputProvider;
    private Path out;

    @Before
    public void setUp() throws IOException {
        context = Mockito.mock(Context.class);
        out = tmpDir.getRoot().toPath().resolve("out");
        Files.createDirectories(out);
        outputProvider = new TestTransformOutputProvider(out);
    }

    @Test
    public void testBasic() throws Exception {
        Path dexArchive = tmpDir.getRoot().toPath().resolve("archive.jar");
        generateArchive(ImmutableList.of(PKG + "/A", PKG + "/B", PKG + "/C"), dexArchive);

        getTransform(DexingType.MONO_DEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(
                                        ImmutableList.of(
                                                new SimpleJarTransformInput(
                                                        new SimpleJarInput.Builder(
                                                                        dexArchive.toFile())
                                                                .create())))
                                .addOutputProvider(outputProvider)
                                .build());

        Dex mainDex = new Dex(out.resolve("main/classes.dex"));
        assertThat(mainDex)
                .containsExactlyClassesIn(
                        ImmutableList.of("L" + PKG + "/A;", "L" + PKG + "/B;", "L" + PKG + "/C;"));
        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_legacyAndMono_alwaysFullMerge() throws Exception {
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < NUM_INPUTS; i++) {
            expectedClasses.add("L" + PKG + "/A" + i + ";");
        }

        List<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        getTransform(DexingType.MONO_DEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(inputs)
                                .addOutputProvider(outputProvider)
                                .build());

        Dex mainDex = new Dex(out.resolve("main/classes.dex"));
        assertThat(mainDex).containsExactlyClassesIn(expectedClasses);
        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_native_externalLibsMerged() throws Exception {
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < NUM_INPUTS; i++) {
            expectedClasses.add("L" + PKG + "/A" + i + ";");
        }

        List<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(inputs)
                                .addOutputProvider(outputProvider)
                                .build());

        Dex mainDex = new Dex(out.resolve("externalLibs/classes.dex"));
        assertThat(mainDex).containsExactlyClassesIn(expectedClasses);
        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1);
    }

    @Test
    public void test_native_nonExternalHaveDexEach() throws Exception {
        List<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.PROJECT);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(inputs)
                                .addOutputProvider(outputProvider)
                                .build());

        Truth.assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex")))
                .hasSize(inputs.size());
    }

    @Test
    public void test_native_changedInput() throws Exception {
        List<TransformInput> inputs =
                getTransformInputs(NUM_INPUTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        List<TransformInput> projectInputs =
                getTransformInputs(1, QualifiedContent.Scope.PROJECT, "B");
        inputs.addAll(projectInputs);

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(inputs)
                                .addOutputProvider(outputProvider)
                                .setIncrementalMode(true)
                                .build());

        File externalMerged = out.resolve("externalLibs/classes.dex").toFile();
        long lastModified =
                FileUtils.getAllFiles(out.toFile())
                        .filter(f -> !Objects.equals(f, externalMerged))
                        .last()
                        .get()
                        .lastModified();
        TestUtils.waitForFileSystemTick();

        Path libArchive = tmpDir.getRoot().toPath().resolve("added.jar");
        generateArchive(ImmutableList.of(PKG + "/C"), libArchive);
        List<TransformInput> updatedInputs =
                ImmutableList.of(
                        new SimpleJarTransformInput(
                                new SimpleJarInput.Builder(libArchive.toFile())
                                        .setStatus(Status.ADDED)
                                        .setScopes(
                                                ImmutableSet.of(
                                                        QualifiedContent.Scope.EXTERNAL_LIBRARIES))
                                        .create()));

        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(updatedInputs)
                                .addOutputProvider(outputProvider)
                                .setIncrementalMode(true)
                                .build());

        Truth.assertThat(externalMerged.lastModified()).isGreaterThan(lastModified);
        FileUtils.getAllFiles(out.toFile())
                .filter(f -> !Objects.equals(f, externalMerged))
                .forEach(f -> Truth.assertThat(f.lastModified()).isEqualTo(lastModified));
    }

    @Test(timeout = 20_000)
    public void test_native_doesNotDeadlock() throws Exception {
        int inputCnt = 3 * Runtime.getRuntime().availableProcessors();
        List<TransformInput> inputs =
                getTransformInputs(inputCnt, QualifiedContent.Scope.SUB_PROJECTS);
        getTransform(DexingType.NATIVE_MULTIDEX)
                .transform(
                        new TransformInvocationBuilder(context)
                                .addInputs(inputs)
                                .addOutputProvider(outputProvider)
                                .build());
    }

    private DexMergerTransform getTransform(@NonNull DexingType dexingType) throws IOException {
        Preconditions.check(
                dexingType != DexingType.LEGACY_MULTIDEX,
                "Main dex list required for legacy multidex");
        return getTransform(dexingType, null);
    }

    private DexMergerTransform getTransform(
            @NonNull DexingType dexingType, @Nullable ImmutableSet<String> mainDex)
            throws IOException {
        FileCollection collection;
        if (mainDex != null) {
            Preconditions.check(
                    dexingType == DexingType.LEGACY_MULTIDEX,
                    "Main dex list must only be used for legacy multidex");
            File tmpFile = tmpDir.newFile();
            Files.write(tmpFile.toPath(), mainDex, StandardOpenOption.TRUNCATE_EXISTING);
            collection = Mockito.mock(FileCollection.class);
            when(collection.getSingleFile()).thenReturn(tmpFile);
        } else {
            collection = null;
        }
        return new DexMergerTransform(
                dexingType, collection, new NoOpErrorReporter(), DexMergerTool.DX);
    }

    @NonNull
    private List<TransformInput> getTransformInputs(int cnt, @NonNull QualifiedContent.Scope scope)
            throws Exception {
        return getTransformInputs(cnt, scope, "A");
    }

    @NonNull
    private List<TransformInput> getTransformInputs(
            int cnt, @NonNull QualifiedContent.Scope scope, @NonNull String classNamPrefix)
            throws Exception {
        List<Path> archives = Lists.newArrayList();
        for (int i = 0; i < cnt; i++) {
            archives.add(tmpDir.newFolder().toPath().resolve("archive" + i + ".jar"));
            generateArchive(
                    ImmutableList.of(PKG + "/" + classNamPrefix + i), Iterables.getLast(archives));
        }

        List<TransformInput> inputs = Lists.newArrayList();
        for (Path dexArchive : archives) {
            inputs.add(
                    new SimpleJarTransformInput(
                            new SimpleJarInput.Builder(dexArchive.toFile())
                                    .setScopes(ImmutableSet.of(scope))
                                    .create()));
        }
        return inputs;
    }

    private void generateArchive(@NonNull Collection<String> classes, @NonNull Path dexArchivePath)
            throws Exception {
        Path classesInput = tmpDir.newFolder().toPath().resolve("input");
        TestInputsGenerator.dirWithEmptyClasses(classesInput, classes);

        // now convert to dex archive
        DexArchiveBuilder builder =
                DexArchiveBuilder.createDxDexBuilder(
                        new DexArchiveBuilderConfig(
                                new DxContext(System.out, System.err), true, 0, DexerTool.DX));

        try (ClassFileInput input = ClassFileInputs.fromPath(classesInput, p -> true);
                DexArchive dexArchive = DexArchives.fromInput(dexArchivePath)) {
            builder.convert(input, dexArchive);
        }
    }
}
