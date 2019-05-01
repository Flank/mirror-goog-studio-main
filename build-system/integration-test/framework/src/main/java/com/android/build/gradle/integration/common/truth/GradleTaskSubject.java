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

import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.truth.TaskStateList.TaskInfo;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

/**
 * Truth subject to verify execution of a Gradle task base on the stdout produced by Gradle.
 */
public class GradleTaskSubject extends Subject<GradleTaskSubject, TaskInfo> {

    public static Subject.Factory<GradleTaskSubject, TaskInfo> gradleTasks() {
        return GradleTaskSubject::new;
    }

    GradleTaskSubject(@NonNull FailureMetadata failureMetadata, @NonNull TaskInfo taskInfo) {
        super(failureMetadata, taskInfo);
    }

    @NonNull
    public static GradleTaskSubject assertThat(@NonNull TaskStateList.TaskInfo taskInfo) {
        return assertAbout(gradleTasks()).that(taskInfo);
    }

    @Override
    protected String actualCustomStringRepresentation() {
        return actual().getTaskName();
    }

    public void wasUpToDate() {
        if (!actual().wasUpToDate()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("Not true that %s was UP-TO-DATE", actualAsString())));
        }
    }

    public void wasFromCache() {
        if (!actual().wasFromCache()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("Not true that %s was FROM-CACHE", actualAsString())));
        }
    }

    public void didWork() {
        if (!actual().didWork()) {
            failWithoutActual(
                    Fact.simpleFact(String.format("Not true that %s did work", actualAsString())));
        }
    }

    public void wasSkipped() {
        if (!actual().wasSkipped()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("Not true that %s was skipped", actualAsString())));
        }
    }

    public void failed() {
        if (!actual().failed()) {
            failWithoutActual(
                    Fact.simpleFact(String.format("Not true that %s failed ", actualAsString())));
        }
    }

    public void ranBefore(String task) {
        TaskInfo taskInfo = actual();
        TaskStateList taskStateList = taskInfo.getTaskStateList();

        if (taskStateList.getTaskIndex(taskInfo.getTaskName()) >= taskStateList.getTaskIndex(task)) {
            fail("was executed before", task);
        }
    }

    public void ranAfter(String task) {
        TaskInfo taskInfo = actual();
        TaskStateList taskStateList = taskInfo.getTaskStateList();

        if (taskStateList.getTaskIndex(taskInfo.getTaskName()) <= taskStateList.getTaskIndex(task)) {
            fail("was executed after", task);
        }
    }
}
