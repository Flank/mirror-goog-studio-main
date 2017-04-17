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

import static com.android.SdkConstants.DOT_DEX;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.core.JackProcessOptions.ProcessingTool;
import com.android.builder.core.JackToolchain;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.file.FileCollection;

/**
 * Predex Java libraries and convert the .jar to Jack library format using Jack for the import
 * libraries, and Jill for the classpath ones.
 *
 * <p>For the classpath libraries, we are only interested in compiling the sources against them.
 * These will not end up in the final .apk, and because of that we only produce the Jack library
 * file containing the .jayce files.
 *
 * <p>Packaged libraries will be converted using Jack, as we would like to pre-dex class files.
 * Final Jack library file will contain .jayce files (one per type), and .dex files (one per type).
 * In case we can benefit from the .dex file containing all types from the input jar, we will create
 * that one as well. For the native multidex variants, we will end up packaging those in the .apk
 * file. Please see {@link #getOutputTypes()} for more details about the generated output.
 *
 * <p>In case this transform generates DEX files, there is one additional action it performs.
 * Because Jack supports only specification of the directory where the DEX will be placed, and names
 * all DEX files classes&lt;N&gt;.dex we will rename them so the packaging step can differentiate
 * between the source DEX file, and library DEX file.
 */
public class JackPreDexTransform extends Transform {

    public enum InputType {
        // for classpath libraries (the ones we are not packaging in the apk e.g. android.jar)
        // we use Jill to convert them to the Jack library format
        CLASSPATH_LIBRARY(ProcessingTool.JILL),
        // for the packaged libraries we use Jack for processing
        PACKAGED_LIBRARY(ProcessingTool.JACK);

        private final ProcessingTool processingTool;

        InputType(ProcessingTool processingTool) {
            this.processingTool = processingTool;
        }

        @NonNull
        public ProcessingTool getProcessingTool() {
            return processingTool;
        }
    }

    private static final ILogger LOG = LoggerWrapper.getLogger(JackPreDexTransform.class);

    @NonNull private final Supplier<List<File>> bootClasspath;
    @NonNull private final Supplier<BuildToolInfo> buildToolInfo;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final JavaProcessExecutor javaProcessExecutor;
    @NonNull private final JackProcessOptions baseOptions;

    @NonNull private final InputType inputType;
    @NonNull private final FileCollection jackPluginsClassPath;

    public JackPreDexTransform(
            @NonNull JackProcessOptions baseOptions,
            @NonNull Supplier<List<File>> bootClasspath,
            @NonNull Supplier<BuildToolInfo> buildToolInfo,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull InputType inputType,
            @NonNull FileCollection jackPluginsClassPath) {
        this.baseOptions = baseOptions;
        this.bootClasspath = bootClasspath;
        this.buildToolInfo = buildToolInfo;
        this.errorReporter = errorReporter;
        this.javaProcessExecutor = javaProcessExecutor;
        this.inputType = inputType;
        this.jackPluginsClassPath = jackPluginsClassPath;
    }

    @NonNull
    @Override
    public String getName() {
        if (inputType == InputType.PACKAGED_LIBRARY) {
            return "preJackPackagedLibraries";
        } else {
            return "preJackRuntimeLibraries";
        }
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        if (baseOptions.isGenerateDex()) {
            return ImmutableSet.of(ExtendedContentType.JACK, ExtendedContentType.DEX);
        } else {
            return TransformManager.CONTENT_JACK;
        }
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        if (inputType == InputType.PACKAGED_LIBRARY) {
            return Sets.immutableEnumSet(
                    QualifiedContent.Scope.SUB_PROJECTS,
                    QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        } else {
            return Sets.immutableEnumSet(
                    QualifiedContent.Scope.PROVIDED_ONLY, QualifiedContent.Scope.TESTED_CODE);
        }
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return ImmutableList.of(SecondaryFile.nonIncremental(jackPluginsClassPath));
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of("buildToolsRev", buildToolInfo.get().getRevision().toString());
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            runJack(transformInvocation);
        } catch (ProcessException
                | ClassNotFoundException
                | JackToolchain.ToolchainException e) {
            throw new TransformException(e);
        }
    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws JackToolchain.ToolchainException,
            ClassNotFoundException,
            ProcessException,
            InterruptedException,
            IOException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);

        Iterable<File> jarInputs = TransformInputUtil.getJarFiles(transformInvocation.getInputs());
        if (inputType == InputType.CLASSPATH_LIBRARY) {
            // for the non-packaged libs add the boot classpath
            jarInputs = Iterables.concat(jarInputs, bootClasspath.get());
        }

        List<File> resolvedClassPath = Lists.newArrayList(jackPluginsClassPath.getFiles());

        for (File file : jarInputs) {
            File jackOutputFile =
                    outputProvider.getContentLocation(
                            getJackFileName(file),
                            TransformManager.CONTENT_JACK,
                            getScopes(),
                            Format.JAR);

            File dexOutputDir = null;
            if (inputType == InputType.PACKAGED_LIBRARY && baseOptions.isGenerateDex()) {
                // for native multidex, generate the .dex files
                dexOutputDir =
                        outputProvider.getContentLocation(
                                getJackFileName(file),
                                TransformManager.CONTENT_DEX,
                                getScopes(),
                                Format.DIRECTORY);
            }

            JackProcessOptions jackOptions =
                    JackProcessOptions.builder(baseOptions)
                            .setProcessingToolUsed(inputType.getProcessingTool())
                            .setImportFiles(ImmutableList.of(file))
                            .setJackOutputFile(jackOutputFile)
                            .setDexOutputDirectory(dexOutputDir)
                            .setJackPluginClassPath(resolvedClassPath)
                            .build();

            // TODO: cache both .dex and .jack files - gavra@
            //noinspection ConstantConditions - jackInProcess has a default value if not set
            JackConversionCache.getCache()
                    .convertLibrary(
                            file,
                            jackOutputFile,
                            jackOptions,
                            buildToolInfo.get(),
                            LOG,
                            errorReporter,
                            javaProcessExecutor);

            if (jackOptions.getDexOutputDirectory() != null) {
                // Generated dex files will be names classes.dex, and if we pass that directly
                // to the packaging task, it won't be able to select the main dex file correctly.
                // Therefore, we will rename the .dex files.
                File[] dexFiles = jackOptions.getDexOutputDirectory().listFiles();
                if (dexFiles != null) {
                    for (File dexFile : dexFiles) {
                        String parentName = dexFile.getParentFile().getName();
                        Files.move(
                                dexFile,
                                FileUtils.join(dexFile.getParentFile(), parentName + DOT_DEX));
                    }
                }
            }
        }
    }

    /**
     * Returns a unique file name for the converted library, even if there are 2 libraries with the
     * same file names (but different paths)
     *
     * @param inputFile the library
     */
    @NonNull
    public static String getJackFileName(@NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        // add a hash of the original file path.
        String input = inputFile.getAbsolutePath();
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(input, Charsets.UTF_16LE);

        return name + "-" + hashCode.toString();
    }

    public boolean isForRuntimeLibs() {
        return inputType == InputType.CLASSPATH_LIBRARY;
    }
}
