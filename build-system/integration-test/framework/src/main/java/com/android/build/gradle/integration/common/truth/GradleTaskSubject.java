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

    public static final String UP_TO_DATE = "UP-TO-DATE";

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

    public void wasExecuted() {
        if (!getSubject().isWasExecuted()) {
            failWithRawMessage("Not true that %s was executed", getDisplaySubject());
        }
    }

    public void wasNotExecuted() {
        if (getSubject().isWasExecuted()) {
            failWithRawMessage("Not true that %s was not executed", getDisplaySubject());
        }
    }

    public void wasUpToDate() {
        if (!getSubject().isUpToDate()) {
            failWithRawMessage("Not true that %s was " + UP_TO_DATE, getDisplaySubject());
        }
    }

    public void wasNotUpToDate() {
        if (getSubject().isUpToDate()) {
            failWithRawMessage("Not true that %s was not " + UP_TO_DATE, getDisplaySubject());
        }
    }

    public void failed() {
        if (!getSubject().failed()) {
            failWithRawMessage("Not true that %s failed ", getDisplaySubject());
        }
    }

    public void didNotFail() {
        if (getSubject().failed()) {
            failWithRawMessage("Not true that %s did not fail", getDisplaySubject());
        }
    }

    public void wasSkipped() {
        if (!getSubject().isSkipped()) {
            failWithRawMessage("Not true that %s was skipped", getDisplaySubject());
        }
    }

    public void wasNotSkipped() {
        if (getSubject().failed()) {
            failWithRawMessage("Not true that %s was not skipped", getDisplaySubject());
        }
    }

    public void hadChangedInputs() {
        if (!getSubject().isInputChanged()) {
            failWithRawMessage("Not true that %s has changed inputs", getDisplaySubject());
        }
    }

    public void hadNoChangedInputs() {
        if (getSubject().isInputChanged()) {
            failWithRawMessage("Not true that %s has no changed inputs", getDisplaySubject());
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
