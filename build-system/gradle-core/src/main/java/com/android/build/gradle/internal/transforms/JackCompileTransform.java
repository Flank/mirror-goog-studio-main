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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.core.JackToolchain;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;

/**
 * Transform for compiling the sources using Jack. This transform will use packaged libraries and
 * classpath libraries as classpath when compiling. In case that the produced .dex file can be used
 * in the final packaging step, it will be generated. Also, annotation processors will ran as part
 * of this transform, if there are any.
 *
 * <p>This transform is not consuming anything form the transform pipeline. Instead, its inputs are:
 *
 * <ul>
 *   <li>all .jack library files in the pipeline - just references these, not consumes
 *   <li>files containing JarJar rules - secondary input
 *   <li>annotation processor classpath files - secondary input
 *   <li>source files - secondary input
 *   <li>jack jar from the build tools - secondary input
 * </ul>
 *
 * <p>Output contains intermediary Jack format (.jayce). These can be either in a jack library file,
 * or in an incremental directory that was specified when compiling the sources. In case that .dex
 * file is being generated for the sources (see {@link this#baseOptions} field for more details), it
 * will be added to the secondary outputs. More precisely, this transform will output:
 *
 * <ul>
 *   <li>optional DEX files, if this is a multiDex variant, in the transform pipeline
 *   <li>JACK compiled files which can be either in the incremental dir, or JACK library format.
 *       Both of these are part of the secondary outputs of this transform.
 * </ul>
 *
 * <p>Dex file for the sources is generated when the {@link JackProcessOptions#isGenerateDex()}
 * returns {@code true}.
 */
public class JackCompileTransform extends Transform {

    private static final ILogger logger = LoggerWrapper.getLogger(JackCompileTransform.class);

    @NonNull private final Supplier<BuildToolInfo> buildToolInfo;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final JavaProcessExecutor javaProcessExecutor;
    @NonNull private final JackProcessOptions baseOptions;

    @NonNull private final List<ConfigurableFileTree> generatedSources = Lists.newArrayList();

    @NonNull private final FileCollection annotationClasspath;
    @NonNull private final FileCollection pluginsClasspath;

    public JackCompileTransform(
            @NonNull JackProcessOptions baseOptions,
            @NonNull Supplier<BuildToolInfo> buildToolInfo,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull FileCollection annotationClasspath,
            @NonNull FileCollection pluginsClasspath) {
        this.buildToolInfo = buildToolInfo;
        this.errorReporter = errorReporter;
        this.javaProcessExecutor = javaProcessExecutor;
        this.annotationClasspath = annotationClasspath;
        this.pluginsClasspath = pluginsClasspath;

        this.baseOptions = setUpIncremental(baseOptions);
    }

    @NonNull
    private static JackProcessOptions setUpIncremental(@NonNull JackProcessOptions baseOptions) {
        boolean incremental = baseOptions.getIncrementalDir() != null;
        if (incremental) {
            try {
                if (!baseOptions.getIncrementalDir().isDirectory()) {
                    FileUtils.mkdirs(baseOptions.getIncrementalDir());
                }
            } catch (RuntimeException ignored) {
                logger.warning(
                        "Cannot create %1$s directory, jack incremental support disabled",
                        baseOptions.getIncrementalDir());
                incremental = false;
            }
        }

        JackProcessOptions.Builder builder = JackProcessOptions.builder(baseOptions);
        if (incremental) {
            // do not produce jack library file
            builder.setJackOutputFile(null);
        } else {
            // we are producing jack library ]file, disable incremental mode
            builder.setIncrementalDir(null);
        }
        return builder.build();
    }

    @NonNull
    @Override
    public String getName() {
        return "jackCompile";
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> builder = ImmutableList.builder();
        builder.addAll(
                baseOptions
                        .getJarJarRuleFiles()
                        .stream()
                        .map(SecondaryFile::nonIncremental)
                        .collect(Collectors.toList()));
        builder.add(SecondaryFile.nonIncremental(annotationClasspath));
        builder.add(SecondaryFile.nonIncremental(pluginsClasspath));
        builder.addAll(
                getGeneratedSources()
                        .stream()
                        .map(SecondaryFile::nonIncremental)
                        .collect(Collectors.toList()));

        builder.add(
                SecondaryFile.nonIncremental(
                        new File(buildToolInfo.get().getPath(BuildToolInfo.PathId.JACK))));

        return builder.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        if (baseOptions.getIncrementalDir() != null) {
            return ImmutableSet.of(baseOptions.getIncrementalDir());
        } else {
            return ImmutableSet.of();
        }
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        if (baseOptions.getJackOutputFile() != null) {
            return ImmutableSet.of(baseOptions.getJackOutputFile());
        } else {
            return ImmutableSet.of();
        }
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("javaResourcesFolder", baseOptions.getResourceDirectories());
        params.put("isDebuggable", baseOptions.isDebuggable());
        params.put("multiDexEnabled", baseOptions.isMultiDex());
        params.put("minSdkVersion", baseOptions.getMinSdkVersion().getApiString());
        params.put("javaMaxHeapSize", baseOptions.getJavaMaxHeapSize());
        params.put("sourceCompatibility", baseOptions.getSourceCompatibility());
        params.put("buildToolsRev", buildToolInfo.get().getRevision().toString());
        params.put("minSdkVersion", baseOptions.getMinSdkVersion().toString());
        params.put("minified", baseOptions.isMinified());
        params.put("annotationProcessorClasspath", baseOptions.getAnnotationProcessorClassPath());
        params.put("annotationProcessorNames", baseOptions.getAnnotationProcessorNames());
        return params;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(
                ExtendedContentType.JACK, ExtendedContentType.JAVA_SOURCES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        if (baseOptions.isGenerateDex()) {
            return TransformManager.CONTENT_DEX;
        } else {
            return TransformManager.CONTENT_JACK;
        }
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.PROVIDED_ONLY,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                QualifiedContent.Scope.TESTED_CODE);
    }

    @Override
    public boolean isIncremental() {
        // We need all jack library files in order to compile, therefore we cannot support
        // incremental mode.
        return false;
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            runJack(transformInvocation);
        } catch (ProcessException | ClassNotFoundException | JackToolchain.ToolchainException e) {
            throw new TransformException(e);
        }
    }

    /** Gets location containing compiled sources in jack intermediary format. */
    @NonNull
    public File getJackCompilationOutput() {
        if (this.baseOptions.getIncrementalDir() != null) {
            return this.baseOptions.getIncrementalDir();
        } else {
            assert this.baseOptions.getJackOutputFile() != null;
            return this.baseOptions.getJackOutputFile();
        }
    }

    public void addGeneratedSource(@NonNull ConfigurableFileTree sourceFileTree) {
        generatedSources.add(sourceFileTree);
    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws ProcessException, IOException, JackToolchain.ToolchainException,
                    ClassNotFoundException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);
        JackProcessOptions.Builder optionsBuilder = JackProcessOptions.builder(baseOptions);
        if (baseOptions.isGenerateDex()) {
            final File outDirectory =
                    outputProvider.getContentLocation(
                            "main", TransformManager.CONTENT_DEX, getScopes(), Format.DIRECTORY);
            optionsBuilder.setDexOutputDirectory(outDirectory);
        } else {
            optionsBuilder.setMultiDex(false);
            optionsBuilder.setDexOutputDirectory(null);
        }

        optionsBuilder.setClassPaths(
                Lists.newArrayList(getAllClasspathJackFiles(transformInvocation)));

        // set the input sources
        List<File> javaSources =
                Lists.newArrayList(TransformInputUtil.getAllFiles(transformInvocation.getInputs()));
        javaSources.addAll(getGeneratedSources());
        optionsBuilder.setInputFiles(javaSources);

        optionsBuilder.setAnnotationProcessorClassPath(
                Lists.newArrayList(annotationClasspath.getFiles()));
        optionsBuilder.setJackPluginClassPath(Lists.newArrayList(pluginsClasspath.getFiles()));

        JackProcessOptions finalOptions = optionsBuilder.build();

        JackToolchain toolchain = new JackToolchain(buildToolInfo.get(), logger, errorReporter);
        toolchain.convert(finalOptions, javaProcessExecutor, finalOptions.isRunInProcess());
    }

    private List<File> getGeneratedSources() {
        List<File> sourceFiles = Lists.newArrayList();
        for (ConfigurableFileTree fileTree : generatedSources) {
            sourceFiles.addAll(fileTree.getFiles());
        }
        return sourceFiles;
    }

    private static Collection<File> getAllClasspathJackFiles(
            @NonNull TransformInvocation invocation) {
        Collection<File> classPathInputs = Lists.newArrayList();
        /*
          Jack files that we are referencing are produced by JackPreDexTransform or it might be the
          tested application (in case of test variant). Because referenced inputs are unable to
          filter per type, we may get more than we asked for i.e. DEX and JACK files. That is why we
          need to grab only the JACK content type files.
        */
        for (TransformInput transformInput : invocation.getReferencedInputs()) {
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                if (directoryInput.getContentTypes().size() == 1
                        && directoryInput.getContentTypes().contains(ExtendedContentType.JACK)) {
                    // we should add the Jack incremental dir root, and not all files individually
                    classPathInputs.add(directoryInput.getFile());
                }
            }

            for (JarInput jarInput : transformInput.getJarInputs()) {
                if (jarInput.getContentTypes().size() == 1
                        && jarInput.getContentTypes().contains(ExtendedContentType.JACK)) {
                    classPathInputs.add(jarInput.getFile());
                }
            }
        }
        return classPathInputs;
    }
}
