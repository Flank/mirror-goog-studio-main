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

package com.android.build.gradle.integration.common.fixture;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation;
import com.android.build.gradle.integration.common.utils.JacocoAgent;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.Option;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.prefs.AbstractAndroidLocations;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.io.FilesKt;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ConfigurableLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;

/**
 * Common flags shared by {@link ModelBuilder} and {@link GradleTaskExecutor}.
 *
 * @param <T> The concrete implementing class.
 */
@SuppressWarnings("unchecked") // Returning this as <T> in most methods.
public abstract class BaseGradleExecutor<T extends BaseGradleExecutor> {

    private static final long TIMEOUT_MINUTES = 10;

    private static Path jvmLogDir;

    static {
        try {
            jvmLogDir = Files.createTempDirectory("GRADLE_JVM_LOGS");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final boolean VERBOSE =
            !Strings.isNullOrEmpty(System.getenv().get("CUSTOM_TEST_VERBOSE"));
    static final boolean CAPTURE_JVM_LOGS = false;

    @NonNull
    final ProjectConnection projectConnection;
    @Nullable protected final GradleTestProject project;
    @NonNull protected final ProjectLocation projectLocation;
    @NonNull final Consumer<GradleBuildResult> lastBuildResultConsumer;
    @NonNull private final List<String> arguments = Lists.newArrayList();
    @NonNull private final ProjectOptionsBuilder options = new ProjectOptionsBuilder();
    @NonNull private final GradleTestProjectBuilder.MemoryRequirement memoryRequirement;
    @NonNull private LoggingLevel loggingLevel = LoggingLevel.INFO;
    private boolean offline = true;
    private boolean localPrefsRoot = false;
    private boolean perTestPrefsRoot = false;
    private boolean failOnWarning = true;
    private ConfigurationCaching configurationCaching;

    BaseGradleExecutor(
            @Nullable GradleTestProject project,
            @NonNull ProjectLocation projectLocation,
            @NonNull ProjectConnection projectConnection,
            @NonNull Consumer<GradleBuildResult> lastBuildResultConsumer,
            @Nullable Path profileDirectory,
            @NonNull GradleTestProjectBuilder.MemoryRequirement memoryRequirement,
            @NonNull ConfigurationCaching configurationCaching) {
        this.project = project;
        this.projectLocation = projectLocation;
        this.lastBuildResultConsumer = lastBuildResultConsumer;
        this.projectConnection = projectConnection;
        File buildFile = (project != null) ? project.getBuildFile() : null;
        if (buildFile != null && !buildFile.getName().equals("build.gradle")) {
            arguments.add("--build-file=" + buildFile.toString());
        }
        this.memoryRequirement = memoryRequirement;
        this.configurationCaching = configurationCaching;

        if (profileDirectory != null) {
            with(StringOption.PROFILE_OUTPUT_DIR, profileDirectory.toString());
        }
    }

    /** Return the default build cache location for a project. */
    public final File getBuildCacheDir() {
        return new File(projectLocation.getProjectDir(), ".buildCache");
    }

    public final T with(@NonNull BooleanOption option, boolean value) {
        options.booleans.put(option, value);
        return (T) this;
    }

    public final T with(@NonNull OptionalBooleanOption option, boolean value) {
        options.optionalBooleans.put(option, value);
        return (T) this;
    }

    public final T with(@NonNull IntegerOption option, int value) {
        options.integers.put(option, value);
        return (T) this;
    }

    public final T with(@NonNull StringOption option, @NonNull String value) {
        options.strings.put(option, value);
        return (T) this;
    }

    public final T suppressOptionWarning(@NonNull Option option) {
        options.suppressWarnings.add(option);
        return (T) this;
    }

    @Deprecated
    @NonNull
    public T withProperty(@NonNull String propertyName, @NonNull String value) {
        withArgument("-P" + propertyName + "=" + value);
        return (T) this;
    }

    /** Add additional build arguments. */
    public final T withArguments(@NonNull List<String> arguments) {
        for (String argument : arguments) {
            withArgument(argument);
        }
        return (T) this;
    }

    /** Add an additional build argument. */
    public final T withArgument(String argument) {
        if (argument.startsWith("-Pandroid")
                && !argument.contains("testInstrumentationRunnerArguments")) {
            throw new IllegalArgumentException("Use with(Option, Value) instead.");
        }
        arguments.add(argument);
        return (T) this;
    }

    public T withEnableInfoLogging(boolean enableInfoLogging) {
        return withLoggingLevel(enableInfoLogging ? LoggingLevel.INFO : LoggingLevel.LIFECYCLE);
    }

    public T withLoggingLevel(@NonNull LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
        return (T) this;
    }

    /** Sets to run Gradle with the normal preference root (~/.android) */
    public final T withLocalPrefsRoot() {
        localPrefsRoot = true;
        return (T) this;
    }

    /** Sets to run Gradle with a per-test class preference root.
     *
     * The preference root outside of test is normally ~/.android.
     * Without this flag, the folder is located in the build output, common to all-tests.
     *
     * Turning this flag on, will make each test class use their own directory.
     */
    public final T withPerTestPrefsRoot() {
        perTestPrefsRoot = true;
        return (T) this;
    }

    public final T withoutOfflineFlag() {
        this.offline = false;
        return (T) this;
    }

    public final T withSdkAutoDownload() {
        return with(BooleanOption.ENABLE_SDK_DOWNLOAD, true);
    }

    public final T withFailOnWarning(boolean failOnWarning) {
        this.failOnWarning = failOnWarning;
        return (T) this;
    }

    public final T withConfigurationCaching(ConfigurationCaching configurationCaching) {
        this.configurationCaching = configurationCaching;
        return (T) this;
    }

    protected final List<String> getArguments() throws IOException {
        List<String> arguments = new ArrayList<>();
        arguments.addAll(this.arguments);
        arguments.addAll(options.getArguments());

        if (loggingLevel.getArgument() != null) {
            arguments.add(loggingLevel.getArgument());
        }

        arguments.add("-Dfile.encoding=" + System.getProperty("file.encoding"));
        arguments.add("-Dsun.jnu.encoding=" + System.getProperty("sun.jnu.encoding"));

        if (offline) {
            arguments.add("--offline");
        }
        // Avoid fail on warning if Kotlin is applied - b/169842093.
        if (failOnWarning && !ifAppliesKotlinPlugin(project)) {
            arguments.add("--warning-mode=fail");
        }

        switch (configurationCaching) {
            case NONE:
                break;
            case ON:
                arguments.add("--configuration-cache");
                arguments.add("--configuration-cache-problems=fail");
                break;
            case OFF:
                arguments.add("--no-configuration-cache");
                break;
            case WARN:
                arguments.add("--configuration-cache");
                arguments.add("--configuration-cache-problems=warn");
                break;
        }

        if (!localPrefsRoot) {
            File preferencesRootDir;
            if (perTestPrefsRoot) {
                preferencesRootDir =
                        new File(
                                projectLocation.getProjectDir().getParentFile(),
                                "android_prefs_root");
            } else {
                preferencesRootDir =
                        new File(
                                projectLocation.getTestLocation().getBuildDir(),
                                "android_prefs_root");
            }

            FileUtils.mkdirs(preferencesRootDir);

            this.preferencesRootDir = preferencesRootDir;

            arguments.add(
                    String.format(
                            "-D%s=%s",
                            AbstractAndroidLocations.ANDROID_PREFS_ROOT,
                            preferencesRootDir.getAbsolutePath()));
        }

        return arguments;
    }

    /*
     * A good-enough heuristic to check if the Kotlin plugin is applied.
     * This is needed because of b/169842093.
     */
    private boolean ifAppliesKotlinPlugin(GradleTestProject testProject) throws IOException {
        GradleTestProject rootProject = testProject.getRootProject();

        for (File buildFile :
                FileUtils.find(rootProject.getProjectDir(), Pattern.compile("build\\.gradle"))) {
            if (FilesKt.readLines(buildFile, Charsets.UTF_8).stream()
                    .anyMatch(
                            s ->
                                    s.contains("apply plugin: 'kotlin'")
                                            || s.contains("apply plugin: 'kotlin-android'"))) {
                return true;
            }
        }

        return false;
    }

    /** Location of the Android Preferences folder (normally in ~/.android) */
    @Nullable private File preferencesRootDir = null;

    @NonNull
    public File getPreferencesRootDir() {
        if (preferencesRootDir == null) {
            throw new RuntimeException(
                    "cannot call getPreferencesRootDir before it is initialized");
        }

        return preferencesRootDir;
    }

    protected final Set<String> getOptionPropertyNames() {
        return options.getOptions()
                .map(Option::getPropertyName)
                .collect(ImmutableSet.toImmutableSet());
    }

    protected final void setJvmArguments(@NonNull LongRunningOperation launcher)
            throws IOException {

        List<String> jvmArguments = new ArrayList<>(this.memoryRequirement.getJvmArgs());

        jvmArguments.add("-XX:+HeapDumpOnOutOfMemoryError");
        jvmArguments.add("-XX:HeapDumpPath=" + jvmLogDir.resolve("heapdump.hprof").toString());

        String debugIntegrationTest = System.getenv("DEBUG_INNER_TEST");
        if (!Strings.isNullOrEmpty(debugIntegrationTest)) {
            String serverArg = debugIntegrationTest.equalsIgnoreCase("socket-listen") ? "n" : "y";
            jvmArguments.add(
                    String.format(
                            "-agentlib:jdwp=transport=dt_socket,server=%s,suspend=y,address=5006",
                            serverArg));
        }

        if (JacocoAgent.isJacocoEnabled()) {
            jvmArguments.add(
                    JacocoAgent.getJvmArg(projectLocation.getTestLocation().getBuildDir()));
        }

        jvmArguments.add("-XX:ErrorFile=" + jvmLogDir.resolve("java_error.log").toString());
        if (CAPTURE_JVM_LOGS) {
            jvmArguments.add("-XX:+UnlockDiagnosticVMOptions");
            jvmArguments.add("-XX:+LogVMOutput");
            jvmArguments.add("-XX:LogFile=" + jvmLogDir.resolve("java_log.log").toString());
        }

        launcher.setJvmArguments(Iterables.toArray(jvmArguments, String.class));
    }

    protected static void setStandardOut(
            @NonNull LongRunningOperation launcher, @NonNull OutputStream stdout) {
        if (VERBOSE) {
            launcher.setStandardOutput(new TeeOutputStream(stdout, System.out));
        } else {
            launcher.setStandardOutput(stdout);
        }
    }

    protected static void setStandardError(
            @NonNull LongRunningOperation launcher, @NonNull OutputStream stderr) {
        if (VERBOSE) {
            launcher.setStandardError(new TeeOutputStream(stderr, System.err));
        } else {
            launcher.setStandardError(stderr);
        }
    }

    private void printJvmLogs() throws IOException {

        List<Path> files;
        try (Stream<Path> walk = Files.walk(jvmLogDir)) {
            files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        if (files.isEmpty()) {
            return;
        }

        Path projectDirectory = projectLocation.getProjectDir().toPath();

        Path outputs;
        if (TestUtils.runningFromBazel()) {

            // Put in test undeclared output directory.
            outputs =
                    TestUtils.getTestOutputDir()
                            .resolve(projectDirectory.getParent().getParent().getFileName())
                            .resolve(projectDirectory.getParent().getFileName())
                            .resolve(projectDirectory.getFileName());
        } else {
            outputs = projectDirectory.resolve("jvm_logs_outputs");
        }
        Files.createDirectories(outputs);

        System.err.println("----------- JVM Log start -----------");
        System.err.println("----- JVM log files being put in " + outputs.toString() + " ----");
        for (Path path : files) {
            System.err.print("---- Copying Log file: ");
            System.err.println(path.getFileName());
            Files.move(path, outputs.resolve(path.getFileName()));
        }
        System.err.println("------------ JVM Log end ------------");
    }

    protected void maybePrintJvmLogs(@NonNull GradleConnectionException failure)
            throws IOException {
        String stacktrace = Throwables.getStackTraceAsString(failure);
        if (stacktrace.contains("org.gradle.launcher.daemon.client.DaemonDisappearedException")
                || stacktrace.contains("java.lang.OutOfMemoryError")) {
                    printJvmLogs();
        }
    }

    protected static class CollectingProgressListener implements ProgressListener {
        final ConcurrentLinkedQueue<ProgressEvent> events;

        protected CollectingProgressListener() {
            events = new ConcurrentLinkedQueue<>();
        }

        @Override
        public void statusChanged(ProgressEvent progressEvent) {
            events.add(progressEvent);
        }

        ImmutableList<ProgressEvent> getEvents() {
            return ImmutableList.copyOf(events);
        }
    }

    protected interface RunAction<LauncherT, ResultT> {
        void run(@NonNull LauncherT launcher, @NonNull ResultHandler<ResultT> resultHandler);
    }

    public enum ConfigurationCaching {
        ON,
        OFF,
        NONE,
        WARN,
    }

    @NonNull
    protected static <LauncherT extends ConfigurableLauncher<LauncherT>, ResultT> ResultT runBuild(
            @NonNull LauncherT launcher, @NonNull RunAction<LauncherT, ResultT> runAction) {
        CancellationTokenSource cancellationTokenSource =
                GradleConnector.newCancellationTokenSource();
        launcher.withCancellationToken(cancellationTokenSource.token());
        SettableFuture<ResultT> future = SettableFuture.create();
        runAction.run(
                launcher,
                new ResultHandler<ResultT>() {
                    @Override
                    public void onComplete(ResultT result) {
                        future.set(result);
                    }

                    @Override
                    public void onFailure(GradleConnectionException e) {
                        future.setException(e);
                    }
                });
        try {
            return future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            throw (GradleConnectionException) e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            try {
                printThreadDumps();
            } catch (Throwable t) {
                e.addSuppressed(t);
            }
            cancellationTokenSource.cancel();
            // TODO(b/78568459) Gather more debugging info from Gradle daemon.
            throw new RuntimeException(e);
        }
    }

    private static void printThreadDumps() throws IOException, InterruptedException {
        if (SdkConstants.currentPlatform() != SdkConstants.PLATFORM_LINUX
                && SdkConstants.currentPlatform() != SdkConstants.PLATFORM_DARWIN) {
            // handle only Linux&Darwin for now
            return;
        }
        String javaHome = System.getProperty("java.home");
        String processes = runProcess(javaHome + "/bin/jps");

        String[] lines = processes.split(System.lineSeparator());
        for (String line : lines) {
            String pid = line.split(" ")[0];
            String threadDump = runProcess(javaHome + "/bin/jstack", "-l", pid);

            System.out.println("Fetching thread dump for: " + line);
            System.out.println("Thread dump is:");
            System.out.println(threadDump);
        }
    }

    private static String runProcess(String... commands) throws InterruptedException, IOException {
        ProcessBuilder processBuilder = new ProcessBuilder().command(commands);
        Process process = processBuilder.start();
        process.waitFor(5, TimeUnit.SECONDS);

        byte[] bytes = ByteStreams.toByteArray(process.getInputStream());
        return new String(bytes, Charsets.UTF_8);
    }
}
