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

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.LoggingManager;
import org.jetbrains.annotations.NotNull;
import proguard.ParseException;

/**
 * Transform for multi-dex.
 *
 * <p>This does not actually consume anything, rather it only reads streams and extract information
 * from them.
 */
public class MultiDexTransform extends BaseProguardAction implements MainDexListWriter {

    // Inputs
    @NonNull private final BuildableArtifact manifestKeepListProguardFile;
    @Nullable
    private final File userMainDexKeepProguard;
    @Nullable
    private final File userMainDexKeepFile;
    @NonNull
    private final VariantScope variantScope;

    private final boolean keepRuntimeAnnotatedClasses;

    // Outputs
    @NonNull
    private final File configFileOut;
    private File mainDexListFile;

    public MultiDexTransform(
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
    }

    @Override
    public void setMainDexListOutputFile(@NotNull File file) {
        mainDexListFile = file;
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
            Map<MainDexListTransform.ProguardInput, Set<File>> inputs =
                    MainDexListTransform.getByInputType(invocation);
            File input =
                    Iterables.getOnlyElement(
                            inputs.get(MainDexListTransform.ProguardInput.INPUT_JAR));
            shrinkWithProguard(input, inputs.get(MainDexListTransform.ProguardInput.LIBRARY_JAR));
            computeList(input);
        } catch (ParseException | ProcessException e) {
            throw new TransformException(e);
        }
    }

    private void shrinkWithProguard(@NonNull File input, @NonNull Set<File> libraryJars)
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

        MainDexListTransform.getPlatformRules().forEach(this::keep);

        // handle inputs
        libraryJar(findShrinkedAndroidJar());
        libraryJars.forEach(this::libraryJar);
        inJar(input, null);

        // outputs.
        outJar(variantScope.getProguardComponentsJarFile());
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

    private void computeList(File _allClassesJarFile) throws ProcessException, IOException {
        // manifest components plus immediate dependencies must be in the main dex.
        Set<String> mainDexClasses = callDx(
                _allClassesJarFile,
                variantScope.getProguardComponentsJarFile());

        if (userMainDexKeepFile != null) {
            mainDexClasses = ImmutableSet.<String>builder()
                    .addAll(mainDexClasses)
                    .addAll(Files.readLines(userMainDexKeepFile, Charsets.UTF_8))
                    .build();
        }

        String fileContent = Joiner.on(System.getProperty("line.separator")).join(mainDexClasses);

        Files.write(fileContent, mainDexListFile, Charsets.UTF_8);

    }

    private Set<String> callDx(File allClassesJarFile, File jarOfRoots) throws ProcessException {
        EnumSet<AndroidBuilder.MainDexListOption> mainDexListOptions =
                EnumSet.noneOf(AndroidBuilder.MainDexListOption.class);
        if (!keepRuntimeAnnotatedClasses) {
            mainDexListOptions.add(
                    AndroidBuilder.MainDexListOption.DISABLE_ANNOTATION_RESOLUTION_WORKAROUND);
            Logging.getLogger(MultiDexTransform.class).warn(
                    "Not including classes with runtime retention annotations in the main dex.\n"
                            + "This can cause issues with reflection in older platforms.");
        }

        return variantScope.getGlobalScope().getAndroidBuilder().createMainDexList(
                allClassesJarFile, jarOfRoots, mainDexListOptions);
    }
}
