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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.truth.TaskStateList.TaskInfo;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

/**
 * Truth subject to verify execution of a Gradle task base on the stdout produced by Gradle.
 */
public class GradleTaskSubject extends Subject<GradleTaskSubject, TaskInfo> {

    public static final SubjectFactory<GradleTaskSubject, TaskInfo> FACTORY =
            new SubjectFactory<GradleTaskSubject, TaskInfo>() {
                @Override
                public GradleTaskSubject getSubject(
                        @NonNull FailureStrategy failureStrategy,
                        @NonNull TaskInfo subject) {
                    return new GradleTaskSubject(failureStrategy, subject);
                }
            };

    GradleTaskSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull TaskInfo taskInfo) {
        super(failureStrategy, taskInfo);
    }

    @NonNull
    public static GradleTaskSubject assertThat(@NonNull TaskStateList.TaskInfo taskInfo) {
        return assert_().about(GradleTaskSubject.FACTORY).that(taskInfo);
    }

    @Override
    protected String getDisplaySubject() {
        return getSubject().getTaskName();
    }

    /**
     * Asserts that the task was planned for execution (but it may or may not have actually run as
     * it may have been skipped for some reason).
     *
     * <p>To check that the task actually ran and completed successfully, use {@link #didWork()}.
     */
    public void wasPlannedForExecution() {
        if (!actual().wasPlannedForExecution()) {
            failWithRawMessage("Not true that %s was executed", getDisplaySubject());
        }
    }

    /**
     * Use {@link #wasPlannedForExecution()} or {@link #didWork()} instead for clearer semantics.
     */
    public void wasExecuted() {
        wasPlannedForExecution();
    }

    public void wasNotPlannedForExecution() {
        if (actual().wasPlannedForExecution()) {
            failWithRawMessage("Not true that %s was not executed", getDisplaySubject());
        }
    }

    /**
     * Use {@link #wasNotPlannedForExecution()} or {@link #didNoWork()} instead for clearer
     * semantics.
     */
    public void wasNotExecuted() {
        wasNotPlannedForExecution();
    }

    public void wasUpToDate() {
        if (!actual().wasUpToDate()) {
            failWithRawMessage("Not true that %s was UP-TO-DATE", getDisplaySubject());
        }
    }

    /** Use {@link #didWork()} instead for clearer semantics. */
    public void wasNotUpToDate() {
        if (actual().wasUpToDate()) {
            failWithRawMessage("Not true that %s was not UP-TO-DATE", getDisplaySubject());
        }
    }

    public void wasFromCache() {
        if (!actual().wasFromCache()) {
            failWithRawMessage("Not true that %s was FROM-CACHE", getDisplaySubject());
        }
    }

    public void wasNotFromCache() {
        if (actual().wasFromCache()) {
            failWithRawMessage("Not true that %s was not FROM-CACHE", getDisplaySubject());
        }
    }

    public void didWork() {
        if (!actual().didWork()) {
            failWithRawMessage("Not true that %s did work", getDisplaySubject());
        }
    }

    public void didNoWork() {
        if (actual().didWork()) {
            failWithRawMessage("Not true that %s did no work", getDisplaySubject());
        }
    }

    public void wasSkipped() {
        if (!actual().wasSkipped()) {
            failWithRawMessage("Not true that %s was skipped", getDisplaySubject());
        }
    }

    public void wasNotSkipped() {
        if (actual().wasSkipped()) {
            failWithRawMessage("Not true that %s was not skipped", getDisplaySubject());
        }
    }

    public void failed() {
        if (!actual().failed()) {
            failWithRawMessage("Not true that %s failed ", getDisplaySubject());
        }
    }

    public void didNotFail() {
        if (actual().failed()) {
            failWithRawMessage("Not true that %s did not fail", getDisplaySubject());
        }
    }

    public void ranBefore(String task) {
        TaskInfo taskInfo = getSubject();
        TaskStateList taskStateList = taskInfo.getTaskStateList();

        if (taskStateList.getTaskIndex(taskInfo.getTaskName()) >= taskStateList.getTaskIndex(task)) {
            fail("was executed before", task);
        }
    }

    public void ranAfter(String task) {
        TaskInfo taskInfo = getSubject();
        TaskStateList taskStateList = taskInfo.getTaskStateList();

        if (taskStateList.getTaskIndex(taskInfo.getTaskName()) <= taskStateList.getTaskIndex(task)) {
            fail("was executed after", task);
        }
    }
}
