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

package com.android.builder.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.JsonEncodedGradleMessageParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.JackProvider;
import com.android.jack.api.v01.ChainedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.DebugInfoLevel;
import com.android.jack.api.v01.MultiDexKind;
import com.android.jack.api.v01.ReporterKind;
import com.android.jack.api.v01.UnrecoverableException;
import com.android.jack.api.v02.VerbosityLevel;
import com.android.jack.api.v04.Api04Config;
import com.android.jill.api.JillProvider;
import com.android.jill.api.v01.Api01TranslationTask;
import com.android.jill.api.v01.TranslationException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Features exposed by the Jack toolchain. This is used for invoking Jack to convert inputs (source
 * code, jars etc.) to Jack library format, or .dex. Besides Jack, one is also able to use Jill
 * to convert Java libraries (.jar) to Jack library format. Both Jack and Jill support conversions
 * in the current, or the separate process.
 *
 * <p>Jack is used for processing java libraries that are packaged in the final apk, and also for
 * the source compilation. It can produce Jack library format or .dex files.
 *
 * <p>Jill is used for processing classpath libraries (these are not in the final apk). It accepts
 * .jar file as input, and produces Jack library format as output. E.g. we are using Jill to
 * convert android.jar to android.jack. This .jack library is later on added to the classpath when
 * compiling the sources.
 */
public class JackToolchain {

    /** Jack toolchain exception. It just wraps to actual exception. */
    public static class ToolchainException extends Exception {
        public ToolchainException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @NonNull private BuildToolInfo buildToolInfo;
    @NonNull private ILogger logger;
    @NonNull private ErrorReporter errorReporter;

    public JackToolchain(
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull ILogger logger,
            @NonNull ErrorReporter errorReporter) {
        this.buildToolInfo = buildToolInfo;
        this.logger = logger;
        this.errorReporter = errorReporter;
    }

    /**
     * Converts Java source code or Java byte code into android byte code or Jack library format
     * using the Jack toolchain. It accepts source files, .jar or .jack as inputs, and produces
     * .jack or .dex files as outputs.
     *
     * @param options options for configuring Jack.
     * @param javaProcessExecutor java executor to be used for out of process execution
     * @param isInProcess whether to run Jack in memory or spawn another Java process.
     * @throws ToolchainException if there is an exception related to running Jack toolchain
     * @throws ProcessException if a process in which the conversion is run fails
     * @throws ClassNotFoundException if running in process, and unable to load the classes required
     *     for the conversion
     */
    public void convert(
            @NonNull JackProcessOptions options,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            boolean isInProcess)
            throws ToolchainException, ProcessException, ClassNotFoundException, IOException {

        // Create all the necessary directories if needed.
        if (options.getDexOutputDirectory() != null) {
            FileUtils.mkdirs(options.getDexOutputDirectory());
        }

        if (options.getOutputFile() != null) {
            FileUtils.mkdirs(options.getOutputFile().getParentFile());
        }

        if (options.getCoverageMetadataFile() != null) {
            try {
                FileUtils.mkdirs(options.getCoverageMetadataFile().getParentFile());
            } catch (RuntimeException ignored) {
                logger.warning(
                        "Cannot create %1$s directory.",
                        options.getCoverageMetadataFile().getParent());
            }
        }

        // set the incremental dir if set and either already exists or can be created.
        if (options.getIncrementalDir() != null) {
            try {
                FileUtils.mkdirs(options.getIncrementalDir());
            } catch (RuntimeException ignored) {
                logger.warning(
                        "Cannot create %1$s directory jack incremental support disabled",
                        options.getIncrementalDir());
                // unset the incremental dir if it neither already exists nor can be created.
                options.setIncrementalDir(null);
            }
        }

        if (options.getAdditionalParameters().keySet().contains("jack.dex.optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        ParsingProcessOutputHandler parser =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(
                                new JsonEncodedGradleMessageParser(), Message.Kind.ERROR, logger),
                        errorReporter);

        if (!isInProcess) {
            convertUsingCli(options, parser, javaProcessExecutor);
        } else {
            ProcessOutput output = parser.createOutput();
            try (Closer c = Closer.create()) {
                c.register(output);
                convertUsingApis(options, output);
            } finally {
                parser.handleOutput(output);
            }
        }
    }

    /**
     * Converts inputs in process. This is using the Jack toolchain APIs. See the {@link
     * #convertUsingJackApis(JackProcessOptions, ProcessOutput)} and {@link
     * #convertUsingJillApis(JackProcessOptions)} for more details.
     */
    private void convertUsingApis(
            @NonNull JackProcessOptions options, @NonNull ProcessOutput output)
            throws ToolchainException, ClassNotFoundException, ProcessException {

        if (options.getUseJill()) {
            convertUsingJillApis(options);
        } else {
            convertUsingJackApis(options, output);
        }
    }

    /**
     * Convert the inputs using Jack in-process and its APIs. Inputs are sources, .jar files and
     * Jack library format (.jack) files. Depending on the options specified, this can produce Jack
     * library format or .dex output.
     *
     * @param options options how to run Jack
     */
    private void convertUsingJackApis(
            @NonNull JackProcessOptions options, @NonNull ProcessOutput output)
            throws ClassNotFoundException, ToolchainException {

        BuildToolsServiceLoader.BuildToolServiceLoader buildToolServiceLoader =
                BuildToolsServiceLoader.INSTANCE.forVersion(buildToolInfo);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JackProvider jackProvider =
                buildToolServiceLoader
                        .getSingleService(logger, BuildToolsServiceLoader.JACK)
                        .orElseThrow(() ->
                                new IllegalStateException("Cannot load Jill from build tools."));
        BuildToolInfo.JackVersion apiVersion = buildToolInfo.getSupportedJackApi();


        // Get configuration object
        try {
            setJackLogLevels(options);
            Api04Config config = createJackConfig(jackProvider, apiVersion);

            config.setDebugInfoLevel(
                    options.isDebuggable() ? DebugInfoLevel.FULL : DebugInfoLevel.NONE);

            if (options.isVerboseProcessing()) {
                config.setVerbosityLevel(VerbosityLevel.INFO);
            }

            config.setClasspath(options.getClasspaths());
            if (options.getDexOutputDirectory() != null) {
                config.setOutputDexDir(options.getDexOutputDirectory());
            }
            if (options.getOutputFile() != null) {
                config.setOutputJackFile(options.getOutputFile());
            }
            config.setImportedJackLibraryFiles(options.getImportFiles());
            if (!DefaultApiVersion.isPreview(options.getMinSdkVersion())) {
                config.setAndroidMinApiLevel(options.getMinSdkVersion().getApiLevel());
            }

            config.setProguardConfigFiles(options.getProguardFiles());
            config.setJarJarConfigFiles(options.getJarJarRuleFiles());

            if (options.isMultiDex()) {
                if (DefaultApiVersion.isLegacyMultidex(options.getMinSdkVersion())) {
                    config.setMultiDexKind(MultiDexKind.LEGACY);
                } else {
                    config.setMultiDexKind(MultiDexKind.NATIVE);
                }
            }

            config.setSourceEntries(options.getInputFiles());
            if (options.getMappingFile() != null) {
                config.setProperty("jack.obfuscation.mapping.dump", "true");
                config.setObfuscationMappingOutputFile(options.getMappingFile());
            }

            config.setProperty("jack.import.type.policy", "keep-first");
            config.setProperty("jack.import.resource.policy", "keep-first");

            config.setReporter(ReporterKind.SDK, output.getStandardOutput());

            if (options.getSourceCompatibility() != null) {
                config.setProperty(
                        "jack.java.source.version", options.getSourceCompatibility());
            }

            if (options.getIncrementalDir() != null && options.getIncrementalDir().exists()) {
                config.setIncrementalDir(options.getIncrementalDir());
            }

            ImmutableList.Builder<File> resourcesDir = ImmutableList.builder();
            for (File file : options.getResourceDirectories()) {
                if (file.exists()) {
                    resourcesDir.add(file);
                }
            }
            config.setResourceDirs(resourcesDir.build());

            // due to b.android.com/82031
            config.setProperty("jack.dex.optimize", "true");

            if (!options.getAnnotationProcessorNames().isEmpty()) {
                config.setProcessorNames(options.getAnnotationProcessorNames());
            }
            if (options.getAnnotationProcessorOutputDirectory() != null) {
                FileUtils.mkdirs(options.getAnnotationProcessorOutputDirectory());
                config.setProperty(
                        "jack.annotation-processor.source.output",
                        options.getAnnotationProcessorOutputDirectory().getAbsolutePath());
            }
            try {
                config.setProcessorPath(options.getAnnotationProcessorClassPath());
            } catch (Exception e) {
                logger.error(e, "Could not resolve annotation processor path.");
                throw new RuntimeException(e);
            }

            config.setProcessorOptions(options.getAnnotationProcessorOptions());

            // apply all additional parameters
            for (String paramKey : options.getAdditionalParameters().keySet()) {
                String paramValue = options.getAdditionalParameters().get(paramKey);
                config.setProperty(paramKey, paramValue);
            }

            if (apiVersion.getVersion() >= BuildToolInfo.JackVersion.V4.getVersion()) {
                config = api04Specific(config, options);
            }

            config.getTask().run();

            logger.verbose("Jack created dex: %1$s", outputStream.toString());
        } catch (ConfigNotSupportedException e) {
            String errorMessage =
                    String.format(
                            "jack.jar from build tools %s does not support Jack API v%d.",
                            buildToolInfo.getRevision().toString(),
                            apiVersion.getVersion());
            throw new ToolchainException(errorMessage, e);
        }  catch (ConfigurationException e) {
            // configuration exceptions are chained exceptions, might contain useful info
            StringBuilder stringBuilder = new StringBuilder("Jack configuration exception.");
            for (ChainedException i : e) {
                stringBuilder.append(System.lineSeparator());
                stringBuilder.append(i.getMessage());
            }
            throw new ToolchainException(stringBuilder.toString(), e);
        } catch (UnrecoverableException e) {
            throw new ToolchainException(
                    "Something out of Jack control has happened: " + e.getMessage(), e);
        } catch (CompilationException e) {
            throw new ToolchainException("Jack compilation exception", e);
        }
    }

    /**
     * Sets logging levels for Jack internals which is useful when debugging Jack issues. This will
     * set appropriate logging level per namespace.
     * <p>N.B. This is not an ideal solution, but until Jack exposes API,
     * we need to set it this way.
     */
    private static void setJackLogLevels(@NonNull JackProcessOptions options) {
        Map<String, Level> namespaceToLevel;
        if (options.isDebugJackInternals()) {
            namespaceToLevel = ImmutableMap.of("", Level.FINE, "com.android.sched", Level.WARNING);
        } else {
            namespaceToLevel = ImmutableMap.of("", Level.SEVERE);
        }

        LogManager logManager = LogManager.getLogManager();
        Enumeration<String> loggerNames = logManager.getLoggerNames();

        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            Logger logger = logManager.getLogger(loggerName);
            if (logger != null && namespaceToLevel.containsKey(loggerName)) {
                logger.setLevel(namespaceToLevel.get(loggerName));
            }
        }
    }

    /**
     * It performs the same operation like {@link #convertUsingApis(JackProcessOptions,
     * ProcessOutput)} , but it performs the conversion in a separate process. See {@link
     * #convertUsingJackCli(JackProcessOptions, ProcessOutputHandler, JavaProcessExecutor)} and
     * {@link #convertUsingJillCli(JackProcessOptions, ProcessOutputHandler, JavaProcessExecutor)}
     * for details.
     *
     * @param options options how to run Jack
     * @param processOutputHandler handler for the output
     * @param javaProcessExecutor executor for running the process
     * @throws ProcessException in case that running the process fails
     */
    private void convertUsingCli(
            @NonNull JackProcessOptions options,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull JavaProcessExecutor javaProcessExecutor)
            throws ProcessException {
        if (options.getUseJill()) {
            convertUsingJillCli(options, processOutputHandler, javaProcessExecutor);
        } else {
            convertUsingJackCli(options, processOutputHandler, javaProcessExecutor);
        }
    }

    /**
     * It performs the same operation like {@link #convertUsingJackApis(JackProcessOptions,
     * ProcessOutput)} but it does that in a separate process.
     */
    private void convertUsingJackCli(
            @NonNull JackProcessOptions options,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull JavaProcessExecutor javaProcessExecutor)
            throws ProcessException {
        JackProcessBuilder builder = new JackProcessBuilder(options, logger);
        javaProcessExecutor
                .execute(builder.build(buildToolInfo), processOutputHandler)
                .rethrowFailure()
                .assertNormalExitValue();
    }

    private Api04Config createJackConfig(
            @NonNull JackProvider jackProvider, @NonNull BuildToolInfo.JackVersion apiVersion)
            throws ConfigNotSupportedException {
        if (apiVersion == BuildToolInfo.JackVersion.V4) {
            return jackProvider.createConfig(Api04Config.class);
        } else {
            throw new RuntimeException("Cannot determine Jack API version to use = " + apiVersion);
        }
    }

    /** Apply Api04 specific values. */
    @NonNull
    private Api04Config api04Specific(
            @NonNull Api04Config config, @NonNull JackProcessOptions options)
            throws ConfigurationException {
        if (options.getCoverageMetadataFile() != null) {
            String coveragePluginPath =
                    buildToolInfo.getPath(BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN);
            if (coveragePluginPath == null) {
                logger.warning(
                        "Unknown path id %s.  Disabling code coverage.",
                        BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN);
            } else {
                File coveragePlugin = new File(coveragePluginPath);
                if (!coveragePlugin.isFile()) {
                    logger.warning(
                            "Unable to find coverage plugin '%s'.  Disabling code " + "coverage.",
                            coveragePlugin.getAbsolutePath());
                } else {
                    options.addJackPluginClassPath(new File(coveragePluginPath));
                    options.addJackPluginName(JackProcessOptions.COVERAGE_PLUGIN_NAME);
                    config.setProperty(
                            "jack.coverage.metadata.file",
                            options.getCoverageMetadataFile().getAbsolutePath());
                    config.setProperty("jack.coverage", "true");
                }
            }
        }

        // jack plugins
        config.setPluginNames(Lists.newArrayList(options.getJackPluginNames()));
        config.setPluginPath(options.getJackPluginClassPath());

        if (options.getEncoding() != null) {
            config.setDefaultCharset(Charset.forName(options.getEncoding()));
        }

        return config;
    }

    /**
     * Converts a .jar to the Jack library format, .jack. It uses Jill that is part of
     * the Jack toolchain. It does so in the current process, using
     * the {@link com.android.jill.api.v01.Api01Config} for configuration.
     * <p>It is being used for processing classpath libraries e.g. android.jar.
     */
    private void convertUsingJillApis(@NonNull JackProcessOptions jackOptions)
            throws ClassNotFoundException, ToolchainException {
        checkState(jackOptions.getImportFiles().size() == 1, "Jill can convert a file at a time.");
        checkNotNull(jackOptions.getOutputFile(), "Jill output file is required.");

        BuildToolsServiceLoader.BuildToolServiceLoader buildToolServiceLoader =
                BuildToolsServiceLoader.INSTANCE.forVersion(buildToolInfo);
        JillProvider jillProvider =
                buildToolServiceLoader
                        .getSingleService(logger, BuildToolsServiceLoader.JILL)
                        .orElseThrow(() ->
                                new IllegalStateException("Cannot load Jill from build tools."));

        File inputFile = jackOptions.getImportFiles().get(0);
        Api01TranslationTask translationTask;
        try {
            com.android.jill.api.v01.Api01Config config =
                    jillProvider.createConfig(com.android.jill.api.v01.Api01Config.class);
            config.setInputJavaBinaryFile(inputFile);
            config.setOutputJackFile(jackOptions.getOutputFile());
            config.setVerbose(jackOptions.isVerboseProcessing());
            config.setDebugInfo(jackOptions.isDebuggable());

            translationTask = config.getTask();

            translationTask.run();
        } catch (com.android.jill.api.ConfigNotSupportedException e) {
            String errorMessage =
                    String.format(
                            "jill.jar from build tools %s does not support Jill API v1.",
                            buildToolInfo.getRevision().toString());
            throw new ToolchainException(errorMessage, e);
        } catch (com.android.jill.api.v01.ConfigurationException e) {
            throw new ToolchainException("Jill APIs v1 configuration failed", e);
        } catch (TranslationException e) {
            throw new ToolchainException("Jill translation aborted", e);
        }
    }

    /**
     * It performs the same operation like {@link #convertUsingJillApis(JackProcessOptions)},
     * but it executes the conversion in the separate process.
     */
    private void convertUsingJillCli(
            @NonNull JackProcessOptions jackOptions,
            @NonNull ProcessOutputHandler outputHandler,
            @NonNull JavaProcessExecutor javaProcessExecutor)
            throws ProcessException {
        checkState(jackOptions.getImportFiles().size() == 1, "Jill can convert a file at a time.");
        checkNotNull(jackOptions.getOutputFile(), "Jill output file is required.");

        // launch jill: create the command line
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        String jill = buildToolInfo.getPath(BuildToolInfo.PathId.JILL);

        if (jill == null || !Files.isRegularFile(Paths.get(jill))) {
            throw new IllegalStateException("jill.jar is missing from the build tools.");
        }

        builder.setClasspath(jill);
        builder.setMain("com.android.jill.Main");

        if (jackOptions.getJavaMaxHeapSize() != null) {
            builder.addJvmArg("-Xmx" + jackOptions.getJavaMaxHeapSize());
        }
        builder.addArgs(jackOptions.getImportFiles().get(0).getAbsolutePath());
        builder.addArgs("--output");
        builder.addArgs(jackOptions.getOutputFile().getAbsolutePath());

        if (jackOptions.isVerboseProcessing()) {
            builder.addArgs("--verbose");
        }

        logger.verbose(builder.toString());
        JavaProcessInfo javaProcessInfo = builder.createJavaProcess();
        ProcessResult result = javaProcessExecutor.execute(javaProcessInfo, outputHandler);
        result.rethrowFailure().assertNormalExitValue();
    }
}
