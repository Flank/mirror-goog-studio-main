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


import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.ExpectFailure;
import java.util.Scanner;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.PluginIdentifier;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskSkippedResult;
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class GradleTaskSubjectTest {

    private TaskStateList taskStateList;

    @Before
    public void setup() {
        String fakeGradleOutput =
                ":updateToDateTask UP-TO-DATE\n"
                        + "\n"
                        + ":fromCacheTask FROM-CACHE\n"
                        + "\n"
                        + ":didWorkTask (Thread[Daemon worker,5,main]) started.\n"
                        + ":didWorkTask\n"
                        + "Executing task ':didWorkTask' (up-to-date check took 0.0 secs) due to:\n"
                        + "  No history is available.\n"
                        + ":didWorkTask (Thread[Daemon worker,5,main]) completed. Took 0.004 secs.\n"
                        + "\n"
                        + ":skippedTask SKIPPED\n"
                        + "\n"
                        + ":fromCacheTask FAILED";

        ImmutableList.Builder<ProgressEvent> events = ImmutableList.builder();
        events.add(upToDate(":upToDateTask"));
        events.add(fromCache(":fromCacheTask"));
        events.add(didWork(":didWorkTask"));
        events.add(skipped(":skippedTask"));
        events.add(failed(":failedTask"));


        taskStateList = new TaskStateList(events.build(), new Scanner(fakeGradleOutput));
    }

    @Test
    public void wasUpToDate() {
        assertThat(taskStateList.getTask(":upToDateTask")).wasUpToDate();
        assertFailure(
                whenTesting ->
                        whenTesting.that(taskStateList.getTask(":didWorkTask")).wasUpToDate(),
                "Not true that <:didWorkTask> was UP-TO-DATE");
    }

    @Test
    public void wasFromCache() {
        assertThat(taskStateList.getTask(":fromCacheTask")).wasFromCache();
        assertFailure(
                whenTesting ->
                        whenTesting.that(taskStateList.getTask(":didWorkTask")).wasFromCache(),
                "Not true that <:didWorkTask> was FROM-CACHE");
    }

    @Test
    public void didWork() {
        assertThat(taskStateList.getTask(":didWorkTask")).didWork();
        assertFailure(
                whenTesting -> whenTesting.that(taskStateList.getTask(":upToDateTask")).didWork(),
                "Not true that <:upToDateTask> did work");
    }

    @Test
    public void wasSkipped() {
        assertThat(taskStateList.getTask(":skippedTask")).wasSkipped();
        assertFailure(
                whenTesting -> whenTesting.that(taskStateList.getTask(":didWorkTask")).wasSkipped(),
                "Not true that <:didWorkTask> was skipped");
    }

    @Test
    public void failed() {
        assertThat(taskStateList.getTask(":failedTask")).failed();
        assertFailure(
                whenTesting -> whenTesting.that(taskStateList.getTask(":didWorkTask")).failed(),
                "Not true that <:didWorkTask> failed ");
    }

    @Test
    public void ranBefore() {
        assertThat(taskStateList.getTask(":upToDateTask")).ranBefore(":didWorkTask");

        assertFailure(
                whenTesting ->
                        whenTesting
                                .that(taskStateList.getTask(":didWorkTask"))
                                .ranBefore(":upToDateTask"),
                "Not true that <:didWorkTask> was executed before <:upToDateTask>");
    }


    @Test
    public void ranAfter() {
        assertThat(taskStateList.getTask(":didWorkTask")).ranAfter(":upToDateTask");

        assertFailure(
                whenTesting ->
                        whenTesting
                                .that(taskStateList.getTask(":upToDateTask"))
                                .ranAfter(":didWorkTask"),
                "Not true that <:upToDateTask> was executed after <:didWorkTask>");
    }

    private void assertFailure(
            @NonNull
                    ExpectFailure.SimpleSubjectBuilderCallback<
                                    GradleTaskSubject, TaskStateList.TaskInfo>
                            callback,
            @NonNull String failureMessage) {
        AssertionError assertionError =
                ExpectFailure.expectFailureAbout(GradleTaskSubject.gradleTasks(), callback);
        assertThat(assertionError.toString()).isEqualTo(failureMessage);
    }

    private static TaskProgressEvent upToDate(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " UP-TO-DATE",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, true, false, null));
    }

    private static TaskProgressEvent fromCache(String name) {
        // When the task's output is retrieved from the cache, Gradle returns both upToDate() and
        // fromCache() as true (see https://github.com/gradle/gradle/issues/5252).
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " FROM_CACHE",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, true, true, null));
    }

    private static TaskProgressEvent didWork(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " SUCCESS",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, false, false, null));
    }

    private static TaskProgressEvent skipped(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " SKIPPED",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSkippedResult(0, 5, "SKIPPED"));
    }

    private static TaskProgressEvent failed(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " FAILED",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskFailureResult(0, 5, null, null));
    }

    private static class FakeTaskOperationDescriptor
            implements TaskOperationDescriptor, OperationDescriptor {

        private final String name;

        private FakeTaskOperationDescriptor(@NonNull String name) {
            this.name = name;
        }

        @Override
        public String getTaskPath() {
            return name;
        }

        @Override
        public Set<? extends OperationDescriptor> getDependencies()
                throws UnsupportedMethodException {
            return null;
        }

        @Nullable
        @Override
        public PluginIdentifier getOriginPlugin() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Task" + name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public OperationDescriptor getParent() {
            return null;
        }
    }
}
