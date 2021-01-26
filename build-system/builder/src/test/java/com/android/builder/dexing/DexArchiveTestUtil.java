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
import com.android.builder.core.StandardOutErrMessageReceiver;
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.testutils.TestClassesGenerator;
import com.android.testutils.TestInputsGenerator;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/** Helper methods for testing dex archives. */
public final class DexArchiveTestUtil {

    public static final String PACKAGE = "test";

    /** Converts all class files in the input path the to the dex archive. */
    public static void convertClassesToDexArchive(
            @NonNull Path classesInput, @NonNull Path dexArchiveOutput) throws IOException {
        convertClassesToDexArchive(classesInput, dexArchiveOutput, 1);
    }

    /** Converts all class files in the input path the to the dex archive. */
    public static void convertClassesToDexArchive(
            @NonNull Path classesInput, @NonNull Path dexArchiveOutput, int minSdkVersion)
            throws IOException {
        convertClassesToDexArchive(classesInput, dexArchiveOutput, minSdkVersion, true);
    }

    public static void convertClassesToDexArchive(
            @NonNull Path classesInput,
            @NonNull Path dexArchiveOutput,
            int minSdkVersion,
            boolean isDebuggable)
            throws IOException {
        try (ClassFileInput inputs = ClassFileInputs.fromPath(classesInput)) {
            DexArchiveBuilder dexArchiveBuilder;
            if (!ClassFileInputs.jarMatcher.matches(dexArchiveOutput)) {
                Files.createDirectories(dexArchiveOutput);
            }

            dexArchiveBuilder =
                    DexArchiveBuilder.createD8DexBuilder(
                            new DexParameters(
                                    minSdkVersion, // minSdkVersion
                                    isDebuggable, // debuggable
                                    true, // dexPerClass
                                    true, // withDesugaring
                                    new ClassFileProviderFactory(Collections.emptyList()), // bootcp
                                    new ClassFileProviderFactory(Collections.emptyList()), // cp
                                    null, // coreLibDesugarConfig
                                    null, // coreLibDesugarOutputKeepRuleFile
                                    new StandardOutErrMessageReceiver() // messageReceiver
                                    ));

            dexArchiveBuilder.convert(
                    inputs.entries((x, y) -> true),
                    dexArchiveOutput,
                    null /* desugarGraphUpdater */);
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

    public static void mergeMonoDex(@NonNull List<Path> dexArchives, @NonNull Path outputDir)
            throws IOException, InterruptedException, DexArchiveMergerException {
        implMergeDexes(dexArchives, outputDir, DexingType.MONO_DEX, null, null);
    }

    public static void mergeLegacyDex(
            @NonNull List<Path> dexArchives,
            @NonNull Path outputDir,
            @NonNull List<String> mainDexRules,
            @NonNull Collection<Path> libraryFiles)
            throws IOException, DexArchiveMergerException {
        implMergeDexes(
                dexArchives, outputDir, DexingType.LEGACY_MULTIDEX, mainDexRules, libraryFiles);
    }

    public static void mergeNativeDex(@NonNull List<Path> dexArchives, @NonNull Path outputDir)
            throws IOException, DexArchiveMergerException {
        implMergeDexes(dexArchives, outputDir, DexingType.NATIVE_MULTIDEX, null, null);
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

    /**
     * Returns the list of classes descriptors generated by {@link #createClasses(Path, Collection)}
     * for the given class names.
     */
    public static List<String> getTestClassesDescriptors(@NonNull Collection<String> classNames)
            throws Exception {
        List<String> classWithPackage =
                classNames.stream().map(e -> PACKAGE + "/" + e).collect(Collectors.toList());
        return classWithPackage;
    }

    /** Writes empty classes to the specified output, and returns the list of descriptors. */
    public static List<String> createClasses(
            @NonNull Path classesOutput, @NonNull Collection<String> classNames) throws Exception {
        List<String> classWithPackage = getTestClassesDescriptors(classNames);
        if (classesOutput.toString().endsWith(SdkConstants.DOT_JAR)) {
            TestInputsGenerator.jarWithEmptyClasses(classesOutput, classWithPackage);
        } else {
            TestInputsGenerator.dirWithEmptyClasses(classesOutput, classWithPackage);
        }
        return classWithPackage;
    }

    /**
     * Runs the dex merger.
     *
     * @param mainDexListRules Proguard rules for choosing classes to be kept in the main dex file.
     * @param libraryFiles classes that are used only to resolve types in the program classes, but
     *     are not packaged in the final binary e.g. android.jar, provided classes etc.
     */
    private static void implMergeDexes(
            @NonNull List<Path> inputs,
            @NonNull Path outputDir,
            @NonNull DexingType dexingType,
            @Nullable List<String> mainDexListRules,
            @Nullable Collection<Path> libraryFiles)
            throws IOException, DexArchiveMergerException {
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListRules != null),
                "Main Dex rules must be set if and only if legacy multidex is enabled.");

        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (libraryFiles != null),
                "Library files must be set if and only if legacy multidex is enabled.");

        DexArchiveMerger merger =
                DexArchiveMerger.createD8DexMerger(
                        new StandardOutErrMessageReceiver(),
                        dexingType == DexingType.NATIVE_MULTIDEX ? 21 : 1,
                        true,
                        ForkJoinPool.commonPool());
        Files.createDirectory(outputDir);
        merger.mergeDexArchives(
                DexArchives.getAllEntriesFromArchives(inputs),
                outputDir,
                null,
                mainDexListRules,
                null,
                libraryFiles,
                null);
    }
}
