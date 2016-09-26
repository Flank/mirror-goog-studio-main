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

import static com.android.builder.core.JackProcessOptions.JACK_MIN_REV;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.JackProvider;
import com.android.jack.api.v01.Api01CompilationTask;
import com.android.jack.api.v01.ChainedException;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.DebugInfoLevel;
import com.android.jack.api.v01.MultiDexKind;
import com.android.jack.api.v01.ReporterKind;
import com.android.jack.api.v01.UnrecoverableException;
import com.android.jack.api.v02.Api02Config;
import com.android.jack.api.v03.Api03Config;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Features exposed by the Jack toolchain. This is used for invoking Jack to convert inputs (source
 * code, jars etc.) to jack library format, or .dex. It supports doing so in the current process, or
 * in a separate one.
 */
public class JackToolchain {
    @NonNull private BuildToolInfo buildToolInfo;
    @NonNull private ILogger logger;

    public JackToolchain(@NonNull BuildToolInfo buildToolInfo, @NonNull ILogger logger) {
        this.buildToolInfo = buildToolInfo;
        this.logger = logger;
    }

    /**
     * Converts Java source code into android byte codes using Jack.
     *
     * @param options Options for configuring Jack.
     * @param javaProcessExecutor java executor to be used for out of process execution
     * @param isInProcess Whether to run Jack in memory or spawn another Java process.
     */
    public void convert(
            @NonNull JackProcessOptions options,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            boolean isInProcess)
            throws ConfigNotSupportedException, ClassNotFoundException, CompilationException,
            ConfigurationException, UnrecoverableException, ProcessException {
        Revision revision = buildToolInfo.getRevision();
        if (revision.compareTo(JACK_MIN_REV, Revision.PreviewComparison.IGNORE) < 0) {
            throw new ConfigNotSupportedException(
                    "Jack requires Build Tools " + JACK_MIN_REV.toString() + " or later");
        }

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
                        "Cannot create %1$s directory, "
                                + "jack incremental support disabled", options.getIncrementalDir());
                // unset the incremental dir if it neither already exists nor can be created.
                options.setIncrementalDir(null);
            }
        }

        if (options.getAdditionalParameters().keySet().contains("jack.dex.optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        if (isInProcess) {
            convertUsingApis(options);
        } else {
            convertUsingCli(options, new LoggedProcessOutputHandler(logger), javaProcessExecutor);
        }
    }

    /**
     * Converts java source code into android byte codes using the jack integration APIs. Jack will
     * run in memory.
     */
    private void convertUsingApis(@NonNull JackProcessOptions options)
            throws ConfigNotSupportedException, ConfigurationException, CompilationException,
                    UnrecoverableException, ClassNotFoundException {

        BuildToolsServiceLoader.BuildToolServiceLoader buildToolServiceLoader =
                BuildToolsServiceLoader.INSTANCE.forVersion(buildToolInfo);

        Api01CompilationTask compilationTask = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Optional<JackProvider> jackProvider =
                buildToolServiceLoader.getSingleService(logger, BuildToolsServiceLoader.JACK);
        BuildToolInfo.JackApiVersion apiVersion = buildToolInfo.getSupportedJackApi();
        if (jackProvider.isPresent()) {

            // Get configuration object
            try {
                Api02Config config = createJackConfig(jackProvider.get());

                config.setDebugInfoLevel(
                        options.isDebuggable() ? DebugInfoLevel.FULL : DebugInfoLevel.NONE);

                config.setClasspath(options.getClasspaths());
                if (options.getDexOutputDirectory() != null) {
                    config.setOutputDexDir(options.getDexOutputDirectory());
                }
                if (options.getOutputFile() != null) {
                    config.setOutputJackFile(options.getOutputFile());
                }
                config.setImportedJackLibraryFiles(options.getImportFiles());
                if (options.getMinSdkVersion() > 0) {
                    config.setAndroidMinApiLevel(options.getMinSdkVersion());
                }

                config.setProguardConfigFiles(options.getProguardFiles());
                config.setJarJarConfigFiles(options.getJarJarRuleFiles());

                if (options.isMultiDex()) {
                    if (options.getMinSdkVersion()
                            < BuildToolInfo.SDK_LEVEL_FOR_MULTIDEX_NATIVE_SUPPORT) {
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

                config.setReporter(ReporterKind.DEFAULT, outputStream);

                if (options.getSourceCompatibility() != null) {
                    config.setProperty(
                            "jack.java.source.version", options.getSourceCompatibility());
                }

                if (options.getEncoding() != null
                        && !options.getEncoding().equals(Charset.defaultCharset().name())) {
                    logger.warning(
                            "Jack will use %s encoding for the source files. If you would like to "
                                    + "specify a different one, add org.gradle.jvmargs="
                                    + "-Dfile.encoding=<encoding> to the gradle.properties file."
                                    + "Alternatively, set jackInProcess = false, and "
                                    + "use android.compileOptions.encoding property.",
                            Charset.defaultCharset().name());
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

                if (apiVersion == BuildToolInfo.JackApiVersion.V2) {
                    config = api02Specific(config, options);
                } else if (apiVersion == BuildToolInfo.JackApiVersion.V3) {
                    config = api03Specific((Api03Config) config, options);
                }

                compilationTask = config.getTask();
            } catch (ConfigNotSupportedException e) {
                logger.error(
                        e,
                        "jack.jar from build tools "
                                + buildToolInfo.getRevision()
                                + " does not support Jack API v%d.",
                        apiVersion.getVersion());
                throw e;
            } catch (ConfigurationException e) {
                logger.error(e, "Jack APIs v%d configuration failed", apiVersion.getVersion());
                throw e;
            }
        }

        checkNotNull(compilationTask);

        // Run the compilation
        try {
            compilationTask.run();
            logger.verbose("Jack created dex: %1$s", outputStream.toString());
        } catch (ConfigurationException e) {
            // chained exceptions are configuration exceptions, might contain useful info
            for (ChainedException i : e) {
                logger.error(null, i.getMessage());
            }
            throw new RuntimeException("Jack configuration exception occurred.");
        } catch (UnrecoverableException e) {
            logger.error(e, "Something out of Jack control has happened: " + e.getMessage());
            throw e;
        } finally {
            // always show Jack output, it might contain useful warnings/errors
            processJackOutput(logger, outputStream);
        }
    }

    /**
     * Converts the inputs in a separate process.
     *
     * @param options options specified
     * @param processOutputHandler used for parsing the output
     * @param javaProcessExecutor executor used for the separate process
     * @throws ProcessException if the execution fails
     */
    private void convertUsingCli(
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

    private static void processJackOutput(
            @NonNull ILogger logger, @NonNull OutputStream outputStream) {
        Iterable<String> msgIterator =
                Splitter.on(SdkUtils.getLineSeparator()).split(outputStream.toString());

        for (String msg : msgIterator) {
            if (msg.startsWith("ERROR") || msg.startsWith("WARNING")) {
                // (ERROR|WARNING):file:position in file:message
                // TODO add JackParser to process the output; it will be used on studio side as well
                Pattern pattern = Pattern.compile("^(ERROR|WARNING):\\s*(.*):(\\d+):\\s*(.*)");
                Matcher matcher = pattern.matcher(msg);
                if (matcher.matches()) {
                    String msgType = matcher.group(1);
                    String content = matcher.group(4);

                    if (msgType.equals("ERROR")) {
                        logger.error(
                                null,
                                matcher.group(2) + ":" + matcher.group(3) + ": error: " + content);
                    } else if (msgType.equals("WARNING")) {
                        logger.warning(
                                matcher.group(2)
                                        + ":"
                                        + matcher.group(3)
                                        + ": warning: "
                                        + content);
                    }
                } else if (msg.startsWith("ERROR")) {
                    logger.error(null, msg);
                } else {
                    // starts with WARNING
                    logger.warning(msg);
                }
            } else {
                logger.info(msg);
            }
        }
    }

    private Api02Config createJackConfig(@NonNull JackProvider jackProvider)
            throws ConfigNotSupportedException {
        BuildToolInfo.JackApiVersion version = buildToolInfo.getSupportedJackApi();
        if (version == BuildToolInfo.JackApiVersion.V3) {
            return jackProvider.createConfig(Api03Config.class);
        } else if (version == BuildToolInfo.JackApiVersion.V2) {
            return jackProvider.createConfig(Api02Config.class);
        } else {
            throw new RuntimeException("Cannot determine Jack API version to use = " + version);
        }
    }

    private Api02Config api02Specific(
            @NonNull Api02Config config, @NonNull JackProcessOptions options)
            throws ConfigurationException {
        if (options.getCoverageMetadataFile() != null) {
            config.setProperty("jack.coverage", "true");
            config.setProperty(
                    "jack.coverage.metadata.file",
                    options.getCoverageMetadataFile().getAbsolutePath());
        }
        return config;
    }

    private Api03Config api03Specific(
            @NonNull Api03Config config, @NonNull JackProcessOptions options)
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
                }
            }
        }

        // jack plugins
        config.setPluginNames(Lists.newArrayList(options.getJackPluginNames()));
        config.setPluginPath(options.getJackPluginClassPath());
        return config;
    }
}
