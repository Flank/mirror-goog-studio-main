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
import java.io.File;
import java.util.Set;
import org.gradle.tooling.GradleConnectionException;

import java.io.ByteArrayOutputStream;

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

    @Nullable
    private final GradleConnectionException exception;

    private TaskStateList taskStateList;

    public GradleBuildResult(
            @NonNull ByteArrayOutputStream stdout,
            @NonNull ByteArrayOutputStream stderr,
            @Nullable GradleConnectionException exception) {
        this.stdout = stdout.toString();
        this.stderr = stderr.toString();
        this.exception = exception;
    }

    /**
     * Returns the exception from the build, null if the build succeeded.
     */
    @Nullable
    public GradleConnectionException getException() {
        return exception;
    }

    public String getStdout() {
        return stdout;
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

    private TaskStateList initTaskStates() {
        if (taskStateList == null) {
            taskStateList = new TaskStateList(stdout);
        }
        return taskStateList;
    }

}
