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

package com.android.builder.dexing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.dx.command.dexer.DxContext;
import com.android.testutils.TestClassesGenerator;
import com.android.testutils.TestInputsGenerator;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/** Helper methods for testing dex archives. */
public final class DexArchiveTestUtil {

    public static final String PACKAGE = "test";

    private static final int NUM_THREADS = 4;
    private static DxContext dxContext = new DxContext(System.out, System.err);

    /** Converts all class files in the input path the to the dex archive. */
    public static void convertClassesToDexArchive(
            @NonNull Path classesInput, @NonNull Path dexArchiveOutput) throws IOException {
        convertClassesToDexArchive(classesInput, dexArchiveOutput, 0);
    }

    /** Converts all class files in the input path the to the dex archive. */
    public static void convertClassesToDexArchive(
            @NonNull Path classesInput, @NonNull Path dexArchiveOutput, int minSdkVersion)
            throws IOException {
        try (DexArchive dexArchive = DexArchives.fromInput(dexArchiveOutput)) {
            ClassFileInput inputs = ClassFileInputs.fromPath(classesInput, e -> true);
            DexArchiveBuilderConfig config =
                    new DexArchiveBuilderConfig(NUM_THREADS, dxContext, true, minSdkVersion);

            DexArchiveBuilder dexArchiveBuilder = new DexArchiveBuilder(config);
            dexArchiveBuilder.convert(inputs, dexArchive);
        }
    }

    /**
     * Creates a dex archive containing the specified classes. Jar containing classes and the dex
     * archive will be written in the supplied empty directory. It returns the path to the created
     * dex archive.
     */
    @NonNull
    public static Path createClassesAndConvertToDexArchive(
            @NonNull Path emptyDir, @NonNull String... classes) throws Exception {
        Path classesInput = emptyDir.resolve("input");
        createClasses(classesInput, Sets.newHashSet(classes));
        Path dexArchive = emptyDir.resolve("dex_archive.jar");
        convertClassesToDexArchive(classesInput, dexArchive);
        return dexArchive;
    }

    public static void mergeMonoDex(@NonNull Collection<Path> dexArchives, @NonNull Path outputDir)
            throws IOException, InterruptedException {
        implMergeDexes(dexArchives, outputDir, DexingType.MONO_DEX, null);
    }

    public static void mergeLegacyDex(
            @NonNull Collection<Path> dexArchives,
            @NonNull Path outputDir,
            @NonNull Set<String> mainDexList)
            throws IOException, InterruptedException {
        implMergeDexes(dexArchives, outputDir, DexingType.LEGACY_MULTIDEX, mainDexList);
    }

    public static void mergeNativeDex(
            @NonNull Collection<Path> dexArchives, @NonNull Path outputDir)
            throws IOException, InterruptedException {
        implMergeDexes(dexArchives, outputDir, DexingType.NATIVE_MULTIDEX, null);
    }

    /** Gets a DEX-style class names from the specified class names without the package. */
    @NonNull
    public static List<String> getDexClasses(@NonNull String... namesWithoutPackage) {
        return getDexClasses(Lists.newArrayList(namesWithoutPackage));
    }

    /** Gets a DEX-style class names from the specified class names without the package. */
    @NonNull
    public static List<String> getDexClasses(@NonNull List<String> namesWithoutPackage) {
        return namesWithoutPackage
                .stream()
                .map(e -> "L" + PACKAGE + "/" + e + ";")
                .collect(Collectors.toList());
    }

    /** Writes a class to the specified root dir, containing specified number of methods. */
    public static void createClassWithMethodDescriptors(
            @NonNull Path rootPath, @NonNull String className, int numMethods) throws Exception {
        Path classFile = rootPath.resolve(PACKAGE + "/" + className + SdkConstants.DOT_CLASS);

        Set<String> methodNames = Sets.newHashSet();
        String[] methodDescriptors = new String[numMethods];
        for (int i = 0; i < numMethods; i++) {
            methodDescriptors[i] = className + i + ":()V";
            methodNames.add(className + i);
        }

        Files.createDirectories(classFile.getParent());
        byte[] bigClass = TestClassesGenerator.classWithEmptyMethods(className, methodDescriptors);

        Files.write(classFile, bigClass);
    }

    /** Writes empty classes to the specified output. */
    public static void createClasses(
            @NonNull Path classesOutput, @NonNull Collection<String> classNames) throws Exception {
        List<String> classWithPackage =
                classNames.stream().map(e -> PACKAGE + "/" + e).collect(Collectors.toList());
        if (classesOutput.toString().endsWith(SdkConstants.DOT_JAR)) {
            TestInputsGenerator.jarWithEmptyClasses(classesOutput, classWithPackage);
        } else {
            TestInputsGenerator.dirWithEmptyClasses(classesOutput, classWithPackage);
        }
    }

    /**
     * Runs the dex merger.
     *
     * @param mainDexList the list of classes to keep in the main dex. Must be set if the dexing
     *     mode is legacy mulidex, must be null otherwise.
     */
    private static void implMergeDexes(
            @NonNull Collection<Path> inputs,
            @NonNull Path outputDir,
            @NonNull DexingType dexingType,
            @Nullable Set<String> mainDexList)
            throws IOException, InterruptedException {
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexList != null),
                "Main Dex list must be set if and only if legacy multidex is enabled.");

        DexMergerConfig config = new DexMergerConfig(dexingType, dxContext);
        DexArchiveMerger merger = new DexArchiveMerger(config, ForkJoinPool.commonPool());
        Files.createDirectory(outputDir);
        merger.merge(inputs, outputDir, mainDexList);
    }
}
