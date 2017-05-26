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

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.truth.GradleOutputFileSubject;
import com.android.build.gradle.integration.common.truth.GradleOutputFileSubjectFactory;
import com.android.build.gradle.integration.common.truth.TaskStateList;
import com.android.utils.SdkUtils;
import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.api.client.repackaged.com.google.common.base.Throwables;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.serialize.PlaceholderException;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.events.ProgressEvent;

/**
 * The result from running a build.
 *
 * <p>See {@link GradleTestProject#executor()} and {@link RunGradleTasks}.
 */
public class GradleBuildResult {

    @NonNull
    private final String stdout;

    @NonNull
    private final String stderr;

    @NonNull private final ImmutableList<ProgressEvent> taskEvents;

    @Nullable
    private final GradleConnectionException exception;

    private TaskStateList taskStateList;

    public GradleBuildResult(
            @NonNull ByteArrayOutputStream stdout,
            @NonNull ByteArrayOutputStream stderr,
            @NonNull ImmutableList<ProgressEvent> taskEvents,
            @Nullable GradleConnectionException exception) {
        this.stdout = stdout.toString();
        this.stderr = stderr.toString();
        this.taskEvents = taskEvents;
        this.exception = exception;
    }

    /**
     * Returns the exception from the build, null if the build succeeded.
     */
    @Nullable
    public GradleConnectionException getException() {
        return exception;
    }

    /**
     * Returns the short (single-line) message that Gradle would print out in the console, without
     * {@code --stacktrace}. If the build succeeded, returns null.
     */
    @Nullable
    public String getFailureMessage() {
        if (exception == null) {
            return null;
        }

        List<Throwable> causalChain = Throwables.getCausalChain(exception);
        // Try the common scenarios: configuration or task failure.
        for (Throwable throwable : causalChain) {
            // Because of different class loaders involved, we are forced to do stringly-typed
            // programming.
            String throwableType = throwable.getClass().getName();
            if (throwableType.equals(ProjectConfigurationException.class.getName())) {
                return throwable.getCause().getMessage();
            } else if (throwableType.equals(PlaceholderException.class.getName())) {
                if (throwable.toString().startsWith(TaskExecutionException.class.getName())) {
                    return throwable.getCause().getMessage();
                }
            }
        }

        // Look for any BuildException, for other cases.
        for (Throwable throwable : causalChain) {
            String throwableType = throwable.getClass().getName();
            if (throwableType.equals(BuildException.class.getName())) {
                return throwable.getCause().getMessage();
            }
        }

        throw new AssertionError("Failed to determine the failure message.", exception);
    }

    @NonNull
    public String getStdout() {
        return stdout;
    }

    @NonNull
    public List<String> getStdoutAsLines() {
        Iterable<String> stdoutlines =
                Splitter.on(SdkUtils.getLineSeparator()).omitEmptyStrings().split(stdout);
        return Lists.newArrayList(stdoutlines);
    }

    @NonNull
    public String getStderr() {
        return stderr;
    }

    /**
     * Truth style assert to check changes to a file.
     */
    @NonNull
    public GradleOutputFileSubject assertThatFile(File subject) {
        return assert_().about(GradleOutputFileSubjectFactory.factory(stdout)).that(subject);
    }

    @NonNull
    public TaskStateList.TaskInfo getTask(String name) {
        Preconditions.checkArgument(name.startsWith(":"), "Task name must start with :");
        return initTaskStates().getTask(name);
    }

    @NonNull
    public Set<String> getUpToDateTasks() {
        return initTaskStates().getUpToDateTasks();
    }

    @NonNull
    public Set<String> getInputChangedTasks() {
        return initTaskStates().getInputChangedTasks();
    }

    public Set<String> getNotUpToDateTasks() {
        return initTaskStates().getNotUpToDateTasks();
    }

    private TaskStateList initTaskStates() {
        if (taskStateList == null) {
            taskStateList = new TaskStateList(taskEvents, stdout);
        }
        return taskStateList;
    }

}
