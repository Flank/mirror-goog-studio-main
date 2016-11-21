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

package com.android.build.gradle.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.core.JackToolchain;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.builder.model.ApiVersion;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
 */
public class JackPreDexTransform extends Transform {
    private static final ILogger LOG = LoggerWrapper.getLogger(JackPreDexTransform.class);

    @NonNull private final Supplier<List<File>> bootClasspath;
    @NonNull private final Supplier<BuildToolInfo> buildToolInfo;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final JavaProcessExecutor javaProcessExecutor;
    @Nullable private String javaMaxHeapSize;
    private boolean forPackagedLibs;
    @NonNull
    private CoreJackOptions coreJackOptions;
    @NonNull private ApiVersion minSdkVersion;
    private final boolean debugJackInternals;
    private final boolean verboseProcessing;
    private final boolean debuggable;

    /** Gets the builder object for this class. */
    public static Builder builder() {
        return new Builder();
    }


    protected JackPreDexTransform(
            @NonNull Supplier<List<File>> bootClasspath,
            @NonNull Supplier<BuildToolInfo> buildToolInfo,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @Nullable String javaMaxHeapSize,
            @NonNull CoreJackOptions coreJackOptions,
            @NonNull ApiVersion minSdkVersion,
            boolean forPackagedLibs,
            boolean debugJackInternals,
            boolean verboseProcessing,
            boolean debuggable) {
        this.bootClasspath = bootClasspath;
        this.buildToolInfo = buildToolInfo;
        this.errorReporter = errorReporter;
        this.javaProcessExecutor = javaProcessExecutor;
        this.javaMaxHeapSize = javaMaxHeapSize;
        this.coreJackOptions = coreJackOptions;
        this.minSdkVersion = minSdkVersion;
        this.forPackagedLibs = forPackagedLibs;
        this.debugJackInternals = debugJackInternals;
        this.verboseProcessing = verboseProcessing;
        this.debuggable = debuggable;
    }

    @NonNull
    @Override
    public String getName() {
        return forPackagedLibs ? "preJackPackagedLibraries" : "preJackRuntimeLibraries";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_JACK;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        if (forPackagedLibs) {
            return TransformManager.SCOPE_FULL_PROJECT;
        } else {
            return Collections.singleton(QualifiedContent.Scope.PROVIDED_ONLY);
        }
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
        if (!forPackagedLibs) {
            // for the non-packaged libs add the boot classpath
            jarInputs = Iterables.concat(jarInputs, bootClasspath.get());
        }

        for (File file : jarInputs) {
            JackProcessOptions options = new JackProcessOptions();
            // for classpath libraries (the ones we are not packaging in the apk e.g. android.jar)
            // we use Jill to convert them to the Jack library format
            options.setUseJill(!forPackagedLibs);
            options.setImportFiles(ImmutableList.of(file));
            File outFile = outputProvider.getContentLocation(
                    getJackFileName(file),
                    getOutputTypes(),
                    getScopes(),
                    Format.JAR);
            options.setOutputFile(outFile);
            options.setJavaMaxHeapSize(javaMaxHeapSize);
            options.setAdditionalParameters(coreJackOptions.getAdditionalParameters());
            options.setMinSdkVersion(minSdkVersion);
            options.setDebugJackInternals(debugJackInternals);
            options.setVerboseProcessing(verboseProcessing);
            options.setDebuggable(debuggable);

            //noinspection ConstantConditions - jackInProcess has a default value if not set
            JackConversionCache.getCache()
                    .convertLibrary(
                            file,
                            outFile,
                            options,
                            coreJackOptions.isJackInProcess(),
                            buildToolInfo.get(),
                            LOG,
                            errorReporter,
                            javaProcessExecutor);
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
        return !forPackagedLibs;
    }

    /** Builder class for {@link com.android.build.gradle.tasks.JackPreDexTransform}. */
    public static class Builder {

        private Supplier<List<File>> bootClasspath = ImmutableList::of;
        private Supplier<BuildToolInfo> buildToolInfo;
        private ErrorReporter errorReporter;
        private JavaProcessExecutor javaProcessExecutor;
        private String javaMaxHeapSize;
        private CoreJackOptions coreJackOptions;
        private Boolean forPackagedLibs;
        private ApiVersion minApiVersion;
        private boolean debugJackInternals = false;
        private boolean verboseProcessing = false;
        private boolean debuggable = false;

        public Builder bootClasspath(@NonNull Supplier<List<File>> bootClasspath) {
            this.bootClasspath = bootClasspath;
            return this;
        }

        public Builder buildToolInfo(@NonNull Supplier<BuildToolInfo> buildToolInfo) {
            this.buildToolInfo = buildToolInfo;
            return this;
        }

        public Builder errorReporter(@NonNull ErrorReporter errorReporter) {
            this.errorReporter = errorReporter;
            return this;
        }

        public Builder javaProcessExecutor(@NonNull JavaProcessExecutor javaProcessExecutor) {
            this.javaProcessExecutor = javaProcessExecutor;
            return this;
        }

        public Builder javaMaxHeapSize(@Nullable String javaMaxHeapSize) {
            this.javaMaxHeapSize = javaMaxHeapSize;
            return this;
        }

        public Builder coreJackOptions(@NonNull CoreJackOptions coreJackOptions) {
            this.coreJackOptions = coreJackOptions;
            return this;
        }

        public Builder forPackagedLibs() {
            this.forPackagedLibs = true;
            return this;
        }

        public Builder forClasspathLibs() {
            this.forPackagedLibs = false;
            return this;
        }

        public Builder minApiVersion(@NonNull ApiVersion minApiVersion) {
            this.minApiVersion = minApiVersion;
            return this;
        }

        public Builder debugJackInternals(boolean debugJackInternals) {
            this.debugJackInternals = debugJackInternals;
            return this;
        }

        public Builder verboseProcessing(boolean verboseProcessing) {
            this.verboseProcessing = verboseProcessing;
            return this;
        }

        public Builder debuggable(boolean debuggable) {
            this.debuggable = debuggable;
            return this;
        }

        public JackPreDexTransform create() {
            checkNotNull(buildToolInfo);
            checkNotNull(errorReporter);
            checkNotNull(javaProcessExecutor);
            checkNotNull(coreJackOptions);
            checkNotNull(minApiVersion);
            return new JackPreDexTransform(
                    bootClasspath,
                    buildToolInfo,
                    errorReporter,
                    javaProcessExecutor,
                    javaMaxHeapSize,
                    coreJackOptions,
                    minApiVersion,
                    forPackagedLibs,
                    debugJackInternals,
                    verboseProcessing,
                    debuggable);
        }
    }
}
