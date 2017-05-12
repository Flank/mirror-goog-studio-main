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
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.StringOption;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;

/** A Gradle tooling api build builder. */
public final class RunGradleTasks extends BaseGradleExecutor<RunGradleTasks> {

    @Nullable private final String buildToolsVersion;

    private boolean isExpectingFailure = false;
    private boolean allowStderr = true; // TODO: change default to false.

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
        buildToolsVersion = gradleTestProject.getBuildToolsVersion();
    }

    /**
     * Assert that the task called fails.
     *
     * <p>The resulting exception is stored in the {@link GradleBuildResult}.
     */
    public RunGradleTasks expectFailure() {
        isExpectingFailure = true;
        allowStderr(true);
        return this;
    }

    /** Disable or enable the assertion that there is no stderr. */
    public RunGradleTasks allowStderr(boolean allowStderr) {
        this.allowStderr = allowStderr;
        return this;
    }

    /**
     * Inject the instant run arguments.
     *
     * @param androidVersion The target device version
     * @param flags additional instant run flags, see {@link OptionalCompilationStep}.
     */
    public RunGradleTasks withInstantRun(
            AndroidVersion androidVersion, @NonNull OptionalCompilationStep... flags) {
        setInstantRunArgs(androidVersion, null /* density */, flags);
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
        return with(BooleanOption.ENABLE_SDK_DOWNLOAD, true);
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
        args.addAll(getArguments());

        if (buildToolsVersion != null) {
            args.add("-PCUSTOM_BUILDTOOLS=" + buildToolsVersion);
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String message =
                "[GradleTestProject "
                        + projectDirectory
                        + "] Executing tasks: \ngradle "
                        + Joiner.on(' ').join(args)
                        + " "
                        + Joiner.on(' ').join(tasksList)
                        + "\n\n";
        stdout.write(message.getBytes());

        BuildLauncher launcher =
                projectConnection.newBuild().forTasks(Iterables.toArray(tasksList, String.class));

        setJvmArguments(launcher);
        setStandardOut(launcher, stdout);
        setStandardError(launcher, stderr);

        CollectingProgressListener progressListener = new CollectingProgressListener();

        launcher.addProgressListener(progressListener, OperationType.TASK);

        ProfileCapturer profiler =
                new ProfileCapturer(benchmarkRecorder, benchmarkMode, profilesDirectory);
        launcher.withArguments(Iterables.toArray(args, String.class));
        WaitingResultHandler handler = new WaitingResultHandler();
        launcher.run(handler);
        GradleConnectionException failure = handler.waitForResult();
        GradleBuildResult result =
                new GradleBuildResult(stdout, stderr, progressListener.getEvents(), failure);
        lastBuildResultConsumer.accept(result);
        if (isExpectingFailure && failure == null) {
            throw new AssertionError("Expecting build to fail");
        } else if (!isExpectingFailure && failure != null) {
            throw failure;
        }
        if (!allowStderr && !result.getStderr().isEmpty()) {
            throw new AssertionError("Unexpected stderr: " + stderr);
        }
        profiler.recordProfile();

        return result;
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
        with(BooleanOption.ENABLE_DEX_ARCHIVE, useDexArchive);
        return this;
    }

    public RunGradleTasks withNewResourceProcessing(boolean useNewResourceProcessing) {
        with(BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING, useNewResourceProcessing);
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
        with(enableAapt2 ? AaptGeneration.AAPT_V2_JNI : AaptGeneration.AAPT_V1);
        return this;
    }

    /**
     * Makes the project execute with AAPT2 flag set to {@param enableAapt2}.
     *
     * <p>If param is {@code true} it will also trigger setting the new resource processing flag to
     * {@code true}. To run AAPT2 without new resource processing, after this method also call
     * {@link #withNewResourceProcessing(boolean)} with the {@code false} parameter.
     */
    public RunGradleTasks with(@NonNull AaptGeneration aaptGeneration) {
        switch (aaptGeneration) {
            case AAPT_V1:
                with(BooleanOption.ENABLE_AAPT2, false);
                break;
            case AAPT_V2:
                with(BooleanOption.ENABLE_AAPT2, true);
                with(BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING, true);
                with(BooleanOption.ENABLE_IN_PROCESS_AAPT2, false);
                break;
            case AAPT_V2_JNI:
                with(BooleanOption.ENABLE_AAPT2, true);
                with(BooleanOption.ENABLE_NEW_RESOURCE_PROCESSING, true);
                with(BooleanOption.ENABLE_IN_PROCESS_AAPT2, true);
                break;
            default:
                throw new IllegalArgumentException("Unknown AAPT Generation");
        }
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
            with(IntegerOption.IDE_TARGET_DEVICE_API, androidVersion.getApiLevel());
            if (androidVersion.getCodename() != null) {
                with(StringOption.IDE_TARGET_DEVICE_CODENAME, androidVersion.getCodename());
            }
        }

        if (density != null) {
            with(StringOption.IDE_BUILD_TARGET_DENISTY, density.getResourceValue());
        }

        Set<OptionalCompilationStep> steps = EnumSet.of(OptionalCompilationStep.INSTANT_DEV);
        steps.addAll(Arrays.asList(flags));

        with(
                StringOption.IDE_OPTIONAL_COMPILATION_STEPS,
                steps.stream().map(OptionalCompilationStep::name).collect(Collectors.joining(",")));
    }
}
