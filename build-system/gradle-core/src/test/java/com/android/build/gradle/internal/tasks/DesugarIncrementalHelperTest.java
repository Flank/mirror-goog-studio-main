/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.transforms.testdata.Animal;
import com.android.build.gradle.internal.transforms.testdata.CarbonForm;
import com.android.build.gradle.internal.transforms.testdata.Cat;
import com.android.build.gradle.internal.transforms.testdata.Tiger;
import com.android.build.gradle.internal.transforms.testdata.Toy;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.testutils.TestInputsGenerator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DesugarIncrementalHelperTest {

    public static final String PROJECT_VARIANT = "app:debug";
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    public DesugarIncrementalHelperTest() {}

    @Before
    public void setUp() {
        // remove any previous state first
        getDesugarIncrementalTransformHelper(false, Collections.emptySet(), Collections.emptySet())
                .getAdditionalPaths();
    }

    @Test
    public void testBasicFull() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);
    }

    @Test
    public void testIncremental_baseInterfaceChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> changedPaths = getChangedPaths(input, CarbonForm.class);
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), changedPaths)
                        .getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Animal.class, Cat.class, Tiger.class));
    }

    @Test
    public void testIncremental_intermediateInterfaceChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> changedPaths = getChangedPaths(input, Animal.class);

        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), changedPaths)
                        .getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Cat.class, Tiger.class));
    }

    @Test
    public void testIncremental_functionalInterfaceChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> changedPaths = getChangedPaths(input, Toy.class);
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), changedPaths)
                        .getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Cat.class, Tiger.class));
    }

    @Test
    public void testIncremental_superClassChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> changedPaths = getChangedPaths(input, Cat.class);

        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), changedPaths)
                        .getAdditionalPaths();

        assertThat(impactedPaths).containsExactlyElementsIn(getPaths(input, Tiger.class));
    }

    @Test
    public void testIncremental_classChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> changedPaths = getChangedPaths(input, Tiger.class);

        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), changedPaths)
                        .getAdditionalPaths();

        assertThat(impactedPaths).isEmpty();
    }

    @Test
    public void testIncremental_multipleChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> changedPaths = getChangedPaths(input, CarbonForm.class, Toy.class);
        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), changedPaths)
                        .getAdditionalPaths();

        assertThat(impactedPaths)
                .containsExactlyElementsIn(getPaths(input, Cat.class, Animal.class, Tiger.class));
    }

    @Test
    public void testIncremental_noneChange() throws IOException {
        Path input = tmpDir.getRoot().toPath().resolve("input");
        initializeGraph(input);

        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), Collections.emptySet())
                        .getAdditionalPaths();

        assertThat(impactedPaths).isEmpty();
    }

    @Test
    public void testDirAndJarInput_incremental() throws IOException {
        Path inputDir = tmpDir.getRoot().toPath().resolve("input_dir");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        ImmutableSet<File> allInputs = ImmutableSet.of(inputDir.toFile(), inputJar.toFile());

        TestInputsGenerator.pathWithClasses(
                inputDir, ImmutableList.of(Cat.class, Toy.class, Tiger.class));
        TestInputsGenerator.pathWithClasses(
                inputJar, ImmutableList.of(Animal.class, CarbonForm.class));
        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.emptySet())
                                .getAdditionalPaths())
                .isEmpty();

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.singleton(inputJar))
                                .getAdditionalPaths())
                .containsExactlyElementsIn(getPaths(inputDir, Cat.class, Tiger.class));

        Set<Path> changedPaths = getChangedPaths(inputDir, Toy.class);
        assertThat(
                        getDesugarIncrementalTransformHelper(true, allInputs, changedPaths)
                                .getAdditionalPaths())
                .containsExactlyElementsIn(getPaths(inputDir, Cat.class, Tiger.class));
    }

    @Test
    public void testDirAndJarInput_incremental_jarDependsOnDir() throws IOException {
        Path inputDir = tmpDir.getRoot().toPath().resolve("input_dir");
        Path inputJar = tmpDir.getRoot().toPath().resolve("input.jar");
        ImmutableSet<File> allInputs = ImmutableSet.of(inputDir.toFile(), inputJar.toFile());

        TestInputsGenerator.pathWithClasses(
                inputDir, ImmutableList.of(Animal.class, CarbonForm.class));
        TestInputsGenerator.pathWithClasses(
                inputJar, ImmutableList.of(Cat.class, Toy.class, Tiger.class));

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.emptySet())
                                .getAdditionalPaths())
                .isEmpty();

        Set<Path> changedPaths = getChangedPaths(inputDir, Animal.class);
        assertThat(
                        getDesugarIncrementalTransformHelper(true, allInputs, changedPaths)
                                .getAdditionalPaths())
                .containsExactly(inputJar);
    }

    @Test
    public void testTwoJars_incremental() throws IOException {
        Path fstJar = tmpDir.getRoot().toPath().resolve("input1.jar");
        Path sndJar = tmpDir.getRoot().toPath().resolve("input2.jar");
        ImmutableSet<File> allInputs = ImmutableSet.of(fstJar.toFile(), sndJar.toFile());

        TestInputsGenerator.pathWithClasses(
                fstJar, ImmutableList.of(Cat.class, Toy.class, Tiger.class));
        TestInputsGenerator.pathWithClasses(
                sndJar, ImmutableList.of(Animal.class, CarbonForm.class));

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.emptySet())
                                .getAdditionalPaths())
                .isEmpty();

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.singleton(sndJar))
                                .getAdditionalPaths())
                .containsExactly(fstJar);
    }

    @Test
    public void test_typeInMultiplePaths() throws IOException {
        Path fstJar = tmpDir.getRoot().toPath().resolve("input1.jar");
        Path sndJar = tmpDir.getRoot().toPath().resolve("input2.jar");
        Path trdJar = tmpDir.getRoot().toPath().resolve("input3.jar");
        ImmutableSet<File> allInputs =
                ImmutableSet.of(fstJar.toFile(), sndJar.toFile(), trdJar.toFile());

        TestInputsGenerator.pathWithClasses(fstJar, ImmutableList.of(Animal.class));
        TestInputsGenerator.pathWithClasses(sndJar, ImmutableList.of(Cat.class));
        TestInputsGenerator.pathWithClasses(trdJar, ImmutableList.of(Cat.class));

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.emptySet())
                                .getAdditionalPaths())
                .isEmpty();

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true, allInputs, Collections.singleton(fstJar))
                                .getAdditionalPaths())
                .containsExactly(sndJar, trdJar);
    }

    @Test
    public void test_incrementalDeletedNonInitialized() {
        Path fstJar = tmpDir.getRoot().toPath().resolve("input1.jar");

        assertThat(
                        getDesugarIncrementalTransformHelper(
                                        true,
                                        Collections.singleton(fstJar.toFile()),
                                        Collections.singleton(fstJar))
                                .getAdditionalPaths())
                .isEmpty();
    }

    private static void initializeGraph(@NonNull Path input) throws IOException {
        TestInputsGenerator.pathWithClasses(
                input,
                ImmutableList.of(
                        Animal.class, CarbonForm.class, Cat.class, Toy.class, Tiger.class));

        Set<Path> impactedPaths =
                getDesugarIncrementalTransformHelper(
                                true, Collections.singleton(input.toFile()), Collections.emptySet())
                        .getAdditionalPaths();

        assertThat(impactedPaths).isEmpty();
    }

    @NonNull
    private static DesugarIncrementalHelper getDesugarIncrementalTransformHelper(
            boolean isIncremental,
            @NonNull Iterable<File> allInputs,
            @NonNull Set<Path> changedPaths) {
        return new DesugarIncrementalHelper(
                PROJECT_VARIANT,
                isIncremental,
                allInputs,
                () -> changedPaths,
                WaitableExecutor.useDirectExecutor());
    }

    @NonNull
    private static Set<Path> getChangedPaths(@NonNull Path root, @NonNull Class<?>... classes) {
        return new HashSet<>(getPaths(root, classes));
    }

    @NonNull
    private static Collection<Path> getPaths(@NonNull Path root, @NonNull Class<?>... classes) {
        List<Path> path = new ArrayList<>(classes.length);
        for (Class<?> klass : classes) {
            path.add(root.resolve(TestInputsGenerator.getPath(klass)));
        }
        return path;
    }
}
