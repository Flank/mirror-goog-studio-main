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
import com.android.testutils.TestUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationType;

/** A Gradle tooling api build builder. */
public final class GradleTaskExecutor extends BaseGradleExecutor<GradleTaskExecutor> {

    private boolean isExpectingFailure = false;
    private ImmutableMap<String, String> env;

    GradleTaskExecutor(
            @NonNull GradleTestProject gradleTestProject,
            @NonNull ProjectConnection projectConnection) {
        super(
                gradleTestProject,
                gradleTestProject.getLocation(),
                projectConnection,
                gradleTestProject::setLastBuildResult,
                gradleTestProject.getProfileDirectory(),
                gradleTestProject.getHeapSize(),
                gradleTestProject.getWithConfigurationCaching());
    }

    /**
     * Assert that the task called fails.
     *
     * <p>The resulting exception is stored in the {@link GradleBuildResult}.
     */
    public GradleTaskExecutor expectFailure() {
        isExpectingFailure = true;
        return this;
    }

    public GradleTaskExecutor withEnvironmentVariables(Map<String, String> env) {
        HashMap<String, String> myEnv;
        if (this.env == null) {
            // If specifying some env vars, make sure to copy the existing one first.
            myEnv = new HashMap<>(System.getenv());
        } else {
            myEnv = new HashMap<>(this.env);
        }
        myEnv.putAll(env);

        this.env = ImmutableMap.copyOf(myEnv);
        return this;
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

        if (!isExpectingFailure) {
            args.add("--stacktrace");
        }

        File tmpStdOut = File.createTempFile("stdout", "log");
        File tmpStdErr = File.createTempFile("stderr", "log");

        BuildLauncher launcher =
                projectConnection.newBuild().forTasks(Iterables.toArray(tasksList, String.class));

        setJvmArguments(launcher);

        CollectingProgressListener progressListener = new CollectingProgressListener();

        launcher.addProgressListener(progressListener, OperationType.TASK);

        launcher.withArguments(Iterables.toArray(args, String.class));

        launcher.setEnvironmentVariables(env);

        GradleConnectionException failure = null;
        try (OutputStream stdout = new BufferedOutputStream(new FileOutputStream(tmpStdOut));
                OutputStream stderr = new BufferedOutputStream(new FileOutputStream(tmpStdErr))) {

            String message =
                    "[GradleTestProject "
                            + projectLocation.getProjectDir()
                            + "] Executing tasks: \ngradle "
                            + Joiner.on(' ').join(args)
                            + " "
                            + Joiner.on(' ').join(tasksList)
                            + "\n\n";
            stdout.write(message.getBytes());

            setStandardOut(launcher, stdout);
            setStandardError(launcher, stderr);

            runBuild(launcher, BuildLauncher::run);
        } catch (GradleConnectionException e) {
            failure = e;
        }

        GradleBuildResult result =
                new GradleBuildResult(tmpStdOut, tmpStdErr, progressListener.getEvents(), failure);
        lastBuildResultConsumer.accept(result);

        if (isExpectingFailure && failure == null) {
            throw new AssertionError("Expecting build to fail");
        } else if (!isExpectingFailure && failure != null) {
            maybePrintJvmLogs(failure);
            throw failure;
        }
        return result;
    }
}
