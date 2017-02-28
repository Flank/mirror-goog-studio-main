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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.tasks.BooleanLatch;
import com.android.ddmlib.IDevice;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;

/** A Gradle tooling api build builder. */
public final class RunGradleTasks extends BaseGradleExecutor<RunGradleTasks> {

    private final boolean isUseJack;
    @Nullable private final String buildToolsVersion;

    private boolean isExpectingFailure = false;
    private boolean isSdkAutoDownload = false;
    private Boolean useDexArchive = true;
    private Boolean useNewResourceProcessing = true;
    private Boolean enableAapt2 = false;

    RunGradleTasks(
            @NonNull GradleTestProject gradleTestProject,
            @NonNull ProjectConnection projectConnection) {
        super(
                projectConnection,
                gradleTestProject::setLastBuildResult,
                gradleTestProject.getTestDir().toPath(),
                gradleTestProject.getBuildFile().toPath(),
                gradleTestProject.getBenchmarkRecorder(),
                gradleTestProject.getProfileDirectory(),
                gradleTestProject.isImprovedDependencyEnabled(),
                gradleTestProject.getHeapSize());
        isUseJack = gradleTestProject.isUseJack();
        buildToolsVersion = gradleTestProject.getBuildToolsVersion();
    }

    /**
     * Assert that the task called fails.
     *
     * <p>The resulting exception is stored in the {@link GradleBuildResult}.
     */
    public RunGradleTasks expectFailure() {
        isExpectingFailure = true;
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param apiLevel The device api level.
     * @param flags additional instant run flags, see {@link OptionalCompilationStep}.
     */
    public RunGradleTasks withInstantRun(
            int apiLevel,
            @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(
                new AndroidVersion(apiLevel, null), null /* density */, flags);
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param device The connected device.
     * @param flags additional instant run flags, see {@link OptionalCompilationStep}.
     */
    public RunGradleTasks withInstantRun(
            @NonNull IDevice device,
            @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(
                device.getVersion(), Density.getEnum(device.getDensity()), flags);
        return this;
    }

    public RunGradleTasks withSdkAutoDownload() {
        this.isSdkAutoDownload = true;
        return this;
    }

    /**
     * Call connected check.
     *
     * <p>Uses deviceCheck in the background to support the device pool.
     */
    public GradleBuildResult executeConnectedCheck() throws IOException, InterruptedException {
        return run("deviceCheck");
    }

    /** Execute the specified tasks */
    public GradleBuildResult run(@NonNull String... tasks)
            throws IOException, InterruptedException {
        return run(ImmutableList.copyOf(tasks));
    }

    public GradleBuildResult run(@NonNull List<String> tasksList)
            throws IOException, InterruptedException {
        assertThat(tasksList).named("tasks list").isNotEmpty();

        TestUtils.waitForFileSystemTick();

        List<String> args = Lists.newArrayList();
        args.addAll(getCommonArguments());

        if (enableInfoLogging) {
            args.add("-i"); // -i, --info Set log level to info.
        }
        args.add("-u"); // -u, --no-search-upward  Don't search in parent folders for a
        // settings.gradle file.
        args.add("-P" + AndroidGradleOptions.PROPERTY_BUILD_CACHE_DIR + "=" + getBuildCacheDir());
        args.add(
                "-Pcom.android.build.gradle.integrationTest.useJack="
                        + Boolean.toString(isUseJack));
        if (GradleTestProject.IMPROVED_DEPENDENCY_RESOLUTION) {
            args.add(
                    "-P"
                            + AndroidGradleOptions.PROPERTY_ENABLE_IMPROVED_DEPENDENCY_RESOLUTION
                            + "=true");
        }
        if (buildToolsVersion != null) {
            args.add("-PCUSTOM_BUILDTOOLS=" + buildToolsVersion);
        }

        if (!isSdkAutoDownload) {
            args.add(
                    String.format(
                            "-P%s=%s", AndroidGradleOptions.PROPERTY_USE_SDK_DOWNLOAD, "false"));
        }

        if (useDexArchive != null) {
            args.add(
                    String.format(
                            "-P%s=%s",
                            AndroidGradleOptions.PROPERTY_USE_DEX_ARCHIVE,
                            Boolean.toString(useDexArchive)));
        }

        if (useNewResourceProcessing != null) {
            args.add(
                    String.format(
                            "-P%s=%s",
                            BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING.getPropertyName(),
                            Boolean.toString(useNewResourceProcessing)));
        }

        if (enableAapt2 != null) {
            args.add(
                    String.format(
                            "-P%s=%s",
                            BooleanOption.ENABLE_AAPT2.getPropertyName(),
                            Boolean.toString(enableAapt2)));
        }

        args.addAll(arguments);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String message =
                "[GradleTestProject] Executing tasks: gradle "
                        + Joiner.on(' ').join(args)
                        + " "
                        + Joiner.on(' ').join(tasksList);
        stdout.write(message.getBytes());

        BuildLauncher launcher =
                projectConnection.newBuild().forTasks(Iterables.toArray(tasksList, String.class));

        setJvmArguments(launcher);
        setStandardOut(launcher, stdout);
        setStandardError(launcher, stderr);

        CollectingProgressListener progressListener = new CollectingProgressListener();

        launcher.addProgressListener(progressListener, OperationType.TASK);

        GradleConnectionException failure;

        // See ProfileCapturer javadoc for explanation.
        try (Closeable ignored =
                new ProfileCapturer(benchmarkRecorder, benchmarkMode, profilesDirectory)) {
            launcher.withArguments(Iterables.toArray(args, String.class));
            WaitingResultHandler handler = new WaitingResultHandler();
            launcher.run(handler);
            failure = handler.waitForResult();
            GradleBuildResult result =
                    new GradleBuildResult(stdout, stderr, progressListener.getEvents(), failure);
            lastBuildResultConsumer.accept(result);
            if (isExpectingFailure && failure == null) {
                throw new AssertionError("Expecting build to fail");
            } else if (!isExpectingFailure && failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static class CollectingProgressListener implements ProgressListener {
        ConcurrentLinkedQueue<ProgressEvent> events;

        private CollectingProgressListener() {
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

    public RunGradleTasks withUseDexArchive(boolean useDexArchive) {
        this.useDexArchive = useDexArchive;
        return this;
    }

    public RunGradleTasks withNewResourceProcessing(boolean useNewResourceProcessing) {
        this.useNewResourceProcessing = useNewResourceProcessing;
        return this;
    }

    /**
     * Makes the project execute with AAPT2 flag set to {@param enableAapt2}.
     *
     * <p>If param is {@code true} it will also trigger setting the new resource processing flag to
     * {@code true}. To run AAPT2 without new resource processing, after this method also call
     * {@link #withNewResourceProcessing(boolean)} with the {@code false} parameter.
     */
    public RunGradleTasks withEnabledAapt2(boolean enableAapt2) {
        this.enableAapt2 = enableAapt2;
        this.useNewResourceProcessing = enableAapt2 ? true : useNewResourceProcessing;
        return this;
    }

    private static class WaitingResultHandler implements ResultHandler<Void> {

        private final BooleanLatch latch = new BooleanLatch();
        private GradleConnectionException failure;

        @Override
        public void onComplete(Void aVoid) {
            latch.signal();
        }

        @Override
        public void onFailure(GradleConnectionException e) {
            failure = e;
            latch.signal();
        }

        /**
         * Waits for the build to complete.
         *
         * @return null if the build passed, the GradleConnectionException if the build failed.
         */
        @Nullable
        private GradleConnectionException waitForResult() throws InterruptedException {
            latch.await();
            return failure;
        }
    }

    private void setInstantRunArgs(
            @Nullable AndroidVersion androidVersion,
            @Nullable Density density,
            @NonNull OptionalCompilationStep[] flags) {
        if (androidVersion != null) {
            withProperty(AndroidProject.PROPERTY_BUILD_API, androidVersion.getFeatureLevel());
        }

        if (density != null) {
            withProperty(AndroidProject.PROPERTY_BUILD_DENSITY, density.getResourceValue());
        }

        StringBuilder optionalSteps =
                new StringBuilder()
                        .append("-P")
                        .append("android.optional.compilation")
                        .append('=')
                        .append("INSTANT_DEV");
        for (OptionalCompilationStep step : flags) {
            optionalSteps.append(',').append(step);
        }
        arguments.add(optionalSteps.toString());
    }
}
