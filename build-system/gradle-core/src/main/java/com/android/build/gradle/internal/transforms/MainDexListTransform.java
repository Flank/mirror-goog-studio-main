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

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.dexing.RuntimeAnnotatedClassCollector;
import com.android.builder.dexing.RuntimeAnnotatedClassDetector;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.ProcessException;
import com.android.multidex.MainDexListBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.jetbrains.annotations.NotNull;
import proguard.ParseException;

/**
 * Transform for multi-dex main dex list.
 *
 * <p>This does not actually consume anything, rather it only reads streams and extract information
 * from them.
 */
public class MainDexListTransform extends BaseProguardAction implements MainDexListWriter {

    enum ProguardInput {
        INPUT_JAR,
        LIBRARY_JAR,
    }

    private static final List<String> MAIN_DEX_LIST_FILTER = ImmutableList.of("**.class");

    // Inputs
    @NonNull private final BuildableArtifact manifestKeepListProguardFile;
    @Nullable private final File userMainDexKeepProguard;
    @Nullable
    private final File userMainDexKeepFile;
    @NonNull
    private final VariantScope variantScope;

    private final boolean keepRuntimeAnnotatedClasses;

    // Internal intermediates
    private final File proguardComponentsJarFile;

    // Outputs
    @NonNull
    private final File configFileOut;
    private File mainDexListFile;

    public MainDexListTransform(
            @NonNull VariantScope variantScope,
            @NonNull DexOptions dexOptions) {
        super(variantScope);
        this.manifestKeepListProguardFile =
                variantScope
                        .getArtifacts()
                        .getFinalArtifactFiles(
                                InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES);
        this.userMainDexKeepProguard = variantScope.getVariantConfiguration().getMultiDexKeepProguard();
        this.userMainDexKeepFile = variantScope.getVariantConfiguration().getMultiDexKeepFile();
        this.variantScope = variantScope;
        configFileOut = new File(variantScope.getGlobalScope().getBuildDir() + "/" + FD_INTERMEDIATES
                + "/multi-dex/" + variantScope.getVariantConfiguration().getDirName()
                + "/components.flags");
        keepRuntimeAnnotatedClasses = dexOptions.getKeepRuntimeAnnotatedClasses();
        proguardComponentsJarFile = variantScope.getProguardComponentsJarFile();
    }

    @Override
    public void setMainDexListOutputFile(@NotNull File mainDexListFile) {
        this.mainDexListFile = mainDexListFile;
    }

    @NonNull
    @Override
    public String getName() {
        return "multidexlist";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                Scope.PROJECT,
                Scope.SUB_PROJECTS,
                Scope.EXTERNAL_LIBRARIES,
                Scope.PROVIDED_ONLY,
                Scope.TESTED_CODE);
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> builder = ImmutableList.builder();
        builder.add(SecondaryFile.nonIncremental(manifestKeepListProguardFile));
        if (userMainDexKeepFile != null) {
            builder.add(SecondaryFile.nonIncremental(userMainDexKeepFile));
        }
        if (userMainDexKeepProguard != null) {
            builder.add(SecondaryFile.nonIncremental(userMainDexKeepProguard));
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> params = ImmutableMap.builder();
        params.put("keepRuntimeAnnotatedClasses", keepRuntimeAnnotatedClasses);
        params.put("implementationClass", "MainDexListTransform");
        TargetInfo targetInfo = variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo();
        if (targetInfo != null) {
            params.put("build_tools", targetInfo.getBuildTools().getRevision().toString());
        }
        return params.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return Lists.newArrayList(mainDexListFile, configFileOut);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        // Re-direct the output to appropriate log levels, just like the official ProGuard task.
        LoggingManager loggingManager = invocation.getContext().getLogging();
        loggingManager.captureStandardOutput(LogLevel.INFO);
        loggingManager.captureStandardError(LogLevel.WARN);

        try {
            Map<ProguardInput, Set<File>> inputs = getByInputType(invocation);
            shrinkWithProguard(inputs, proguardComponentsJarFile);
            List<File> allInputFiles =
                    new ArrayList<>(
                            inputs.get(ProguardInput.LIBRARY_JAR).size()
                                    + inputs.get(ProguardInput.INPUT_JAR).size());
            allInputFiles.addAll(inputs.get(ProguardInput.LIBRARY_JAR));
            allInputFiles.addAll(inputs.get(ProguardInput.INPUT_JAR));
            Set<String> classes =
                    computeList(
                            allInputFiles,
                            proguardComponentsJarFile,
                            userMainDexKeepFile,
                            keepRuntimeAnnotatedClasses);
            Files.write(mainDexListFile.toPath(), classes);
        } catch (ParseException | ProcessException e) {
            throw new TransformException(e);
        }
    }

    @NonNull
    static List<String> getPlatformRules() {
        return ImmutableList.of(
                "public class * extends android.app.Instrumentation {\n"
                        + "  <init>(); \n"
                        + "  void onCreate(...);\n"
                        + "}",
                "public class * extends android.app.Application { "
                        + "  <init>();\n"
                        + "  void attachBaseContext(android.content.Context);\n"
                        + "}",
                "public class * extends android.app.backup.BackupAgent { <init>(); }",
                "public class * implements java.lang.annotation.Annotation { *;}",
                "public class * extends android.test.InstrumentationTestCase { <init>(); }");
    }

    @NonNull
    static Map<ProguardInput, Set<File>> getByInputType(@NonNull TransformInvocation invocation) {
        Map<ProguardInput, Set<File>> grouped = Maps.newHashMap();
        ImmutableSet<Scope> libraryScopes =
                Sets.immutableEnumSet(Scope.PROVIDED_ONLY, Scope.TESTED_CODE);
        for (TransformInput input : invocation.getReferencedInputs()) {
            for (QualifiedContent content :
                    Iterables.concat(input.getDirectoryInputs(), input.getJarInputs())) {
                ProguardInput type;
                if (Sets.difference(content.getScopes(), libraryScopes).isEmpty()) {
                    type = ProguardInput.LIBRARY_JAR;
                } else {
                    type = ProguardInput.INPUT_JAR;
                }
                Set<File> current = grouped.getOrDefault(type, new HashSet<>());
                current.add(content.getFile());
                grouped.put(type, current);
            }
        }
        grouped.putIfAbsent(ProguardInput.INPUT_JAR, new HashSet<>());
        grouped.putIfAbsent(ProguardInput.LIBRARY_JAR, new HashSet<>());

        // If the same file is in both input and library, the input takes precedence. This is
        // possible with e.g. multidex support library which will be in the TESTED_CODE scope, and
        // EXTERNAL_LIBRARIES scope
        Sets.SetView<File> librariesNotInput =
                Sets.difference(
                        grouped.get(ProguardInput.LIBRARY_JAR),
                        grouped.get(ProguardInput.INPUT_JAR));
        grouped.put(ProguardInput.LIBRARY_JAR, librariesNotInput);
        return grouped;
    }

    private void shrinkWithProguard(
            @NonNull Map<ProguardInput, Set<File>> inputs, @NonNull File outJar)
            throws IOException, ParseException {
        configuration.obfuscate = false;
        configuration.optimize = false;
        configuration.preverify = false;
        dontwarn();
        dontnote();
        forceprocessing();

        applyConfigurationFile(BuildableArtifactUtil.singleFile(manifestKeepListProguardFile));
        if (userMainDexKeepProguard != null) {
            applyConfigurationFile(userMainDexKeepProguard);
        }

        getPlatformRules().forEach(this::keep);

        // handle inputs
        libraryJar(findShrinkedAndroidJar());
        inputs.get(ProguardInput.LIBRARY_JAR).forEach(this::libraryJar);
        inputs.get(ProguardInput.INPUT_JAR).forEach(jar -> this.inJar(jar, MAIN_DEX_LIST_FILTER));

        // outputs.
        outJar(outJar);
        printconfiguration(configFileOut);

        // run proguard
        runProguard();
    }

    @NonNull
    private File findShrinkedAndroidJar() {
        Preconditions.checkNotNull(
                variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo());
        File shrinkedAndroid = new File(
                variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo()
                        .getBuildTools()
                        .getLocation(),
                "lib" + File.separatorChar + "shrinkedAndroid.jar");

        if (!shrinkedAndroid.isFile()) {
            shrinkedAndroid = new File(
                    variantScope.getGlobalScope().getAndroidBuilder().getTargetInfo()
                            .getBuildTools().getLocation(),
                    "multidex" + File.separatorChar + "shrinkedAndroid.jar");
        }
        return shrinkedAndroid;
    }

    @VisibleForTesting
    static ImmutableSet<String> computeList(
            @NonNull Collection<File> allClasses,
            @NonNull File jarOfRoots,
            @Nullable File userMainDexKeepFile,
            boolean keepRuntimeAnnotatedClasses)
            throws ProcessException, IOException, InterruptedException {
        ImmutableSet.Builder<String> mainDexClasses = ImmutableSet.builder();

        // manifest components plus immediate dependencies must be in the main dex.
        mainDexClasses.addAll(callDx(allClasses, jarOfRoots));

        if (userMainDexKeepFile != null) {
            mainDexClasses.addAll(Files.readAllLines(userMainDexKeepFile.toPath(), Charsets.UTF_8));
        }

        if (keepRuntimeAnnotatedClasses) {
            RuntimeAnnotatedClassCollector collector =
                    new RuntimeAnnotatedClassCollector(
                            RuntimeAnnotatedClassDetector::hasRuntimeAnnotations);
            mainDexClasses.addAll(
                    collector.collectClasses(
                            allClasses.stream().map(File::toPath).collect(Collectors.toList())));
        }

        return mainDexClasses.build();
    }

    @NonNull
    private static ImmutableSet<String> callDx(
            @NonNull Collection<File> allClasses, @NonNull File jarOfRoots) throws IOException {
        String pathList =
                allClasses
                        .stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
        // RuntimeAnnotatedClassDetector replaces MainDexListBuilder's keepAnnotated.
        MainDexListBuilder builder =
                new MainDexListBuilder(false, jarOfRoots.getAbsolutePath(), pathList);
        Set<String> mainDexList =
                builder.getMainDexList()
                        .stream()
                        // Dx prefixes classes read from directories with forward slash.
                        .map(input -> input.startsWith("/") ? input.substring(1) : input)
                        .collect(Collectors.toSet());
        return ImmutableSet.copyOf(mainDexList);
    }

}
