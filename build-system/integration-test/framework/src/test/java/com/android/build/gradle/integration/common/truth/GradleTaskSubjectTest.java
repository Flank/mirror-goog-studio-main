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

import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.FACTORY;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskSkippedResult;
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult;
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
                        + ":didWorkTaskWithChangedInputs (Thread[Daemon worker,5,main]) started.\n"
                        + ":didWorkTaskWithChangedInputs\n"
                        + "Executing task ':didWorkTaskWithChangedInputs' (up-to-date check took 0.005 secs) due to:\n"
                        + "  Value of input property 'someFakeInput' has changed for task ':didWorkTaskWithChangedInputs'\n"
                        + "All input files are considered out-of-date for incremental task ':didWorkTaskWithChangedInputs'.\n"
                        + "Unable do incremental execution: full task run\n"
                        + ":didWorkTaskWithChangedInputs (Thread[Daemon worker,5,main]) completed. Took 0.135 secs.\n"
                        + "\n"
                        + ":skippedTask SKIPPED\n"
                        + "\n"
                        + ":fromCacheTask FAILED";

        ImmutableList.Builder<ProgressEvent> events = ImmutableList.builder();
        events.add(upToDate(":upToDateTask"));
        events.add(fromCache(":fromCacheTask"));
        events.add(didWork(":didWorkTask"));
        events.add(didWork(":didWorkTaskWithChangedInputs"));
        events.add(skipped(":skippedTask"));
        events.add(failed(":failedTask"));

        taskStateList = new TaskStateList(events.build(), fakeGradleOutput);
    }

    @Test
    public void wasPlannedForExecution() {
        assertSuccess(":didWorkTask", GradleTaskSubject::wasPlannedForExecution);
        assertFailure(
                ":taskThatDidNotRun",
                GradleTaskSubject::wasPlannedForExecution,
                "Not true that :taskThatDidNotRun was executed");
    }

    @Test
    public void wasExecuted() {
        assertSuccess(":didWorkTask", GradleTaskSubject::wasExecuted);
        assertFailure(
                ":taskThatDidNotRun",
                GradleTaskSubject::wasExecuted,
                "Not true that :taskThatDidNotRun was executed");
    }

    @Test
    public void wasNotPlannedForExecution() {
        assertSuccess(":taskThatDidNotRun", GradleTaskSubject::wasNotPlannedForExecution);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::wasNotPlannedForExecution,
                "Not true that :didWorkTask was not executed");
    }

    @Test
    public void wasNotExecuted() {
        assertSuccess(":taskThatDidNotRun", GradleTaskSubject::wasNotExecuted);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::wasNotExecuted,
                "Not true that :didWorkTask was not executed");
    }

    @Test
    public void wasUpToDate() {
        assertSuccess(":upToDateTask", GradleTaskSubject::wasUpToDate);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::wasUpToDate,
                "Not true that :didWorkTask was UP-TO-DATE");
    }

    @Test
    public void wasNotUpToDate() {
        assertSuccess(":didWorkTask", GradleTaskSubject::wasNotUpToDate);
        assertFailure(
                ":upToDateTask",
                GradleTaskSubject::wasNotUpToDate,
                "Not true that :upToDateTask was not UP-TO-DATE");
    }

    @Test
    public void wasFromCache() {
        assertSuccess(":fromCacheTask", GradleTaskSubject::wasFromCache);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::wasFromCache,
                "Not true that :didWorkTask was FROM-CACHE");
    }

    @Test
    public void wasNotFromCache() {
        assertSuccess(":didWorkTask", GradleTaskSubject::wasNotFromCache);
        assertFailure(
                ":fromCacheTask",
                GradleTaskSubject::wasNotFromCache,
                "Not true that :fromCacheTask was not FROM-CACHE");
    }

    @Test
    public void didWork() {
        assertSuccess(":didWorkTask", GradleTaskSubject::didWork);
        assertFailure(
                ":upToDateTask",
                GradleTaskSubject::didWork,
                "Not true that :upToDateTask did work");
    }

    @Test
    public void didNoWork() {
        assertSuccess(":upToDateTask", GradleTaskSubject::didNoWork);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::didNoWork,
                "Not true that :didWorkTask did no work");
    }

    @Test
    public void wasSkipped() {
        assertSuccess(":skippedTask", GradleTaskSubject::wasSkipped);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::wasSkipped,
                "Not true that :didWorkTask was skipped");
    }

    @Test
    public void wasNotSkipped() {
        assertSuccess(":didWorkTask", GradleTaskSubject::wasNotSkipped);
        assertFailure(
                ":skippedTask",
                GradleTaskSubject::wasNotSkipped,
                "Not true that :skippedTask was not skipped");
    }

    @Test
    public void failed() {
        assertSuccess(":failedTask", GradleTaskSubject::failed);
        assertFailure(
                ":didWorkTask", GradleTaskSubject::failed, "Not true that :didWorkTask failed ");
    }

    @Test
    public void didNotFail() {
        assertSuccess(":didWorkTask", GradleTaskSubject::didNotFail);
        assertFailure(
                ":failedTask",
                GradleTaskSubject::didNotFail,
                "Not true that :failedTask did not fail");
    }

    @Test
    public void hadChangedInputs() {
        assertSuccess(":didWorkTaskWithChangedInputs", GradleTaskSubject::hadChangedInputs);
        assertFailure(
                ":didWorkTask",
                GradleTaskSubject::hadChangedInputs,
                "Not true that :didWorkTask had changed inputs");
    }

    @Test
    public void hadNoChangedInputs() {
        assertSuccess(":didWorkTask", GradleTaskSubject::hadNoChangedInputs);
        assertFailure(
                ":didWorkTaskWithChangedInputs",
                GradleTaskSubject::hadNoChangedInputs,
                "Not true that :didWorkTaskWithChangedInputs had no changed inputs");
    }

    @Test
    public void ranBefore() {
        FakeFailureStrategy failureStrategy = new FakeFailureStrategy();
        GradleTaskSubject taskSubject =
                FACTORY.getSubject(failureStrategy, taskStateList.getTask(":upToDateTask"));
        taskSubject.ranBefore(":didWorkTask");
        assertThat(failureStrategy.message).isNull();

        failureStrategy = new FakeFailureStrategy();
        taskSubject = FACTORY.getSubject(failureStrategy, taskStateList.getTask(":didWorkTask"));
        taskSubject.ranBefore(":upToDateTask");
        assertThat(failureStrategy.message)
                .isEqualTo("Not true that :didWorkTask was executed before <:upToDateTask>");
    }

    @Test
    public void ranAfter() {
        FakeFailureStrategy failureStrategy = new FakeFailureStrategy();
        GradleTaskSubject taskSubject =
                FACTORY.getSubject(failureStrategy, taskStateList.getTask(":didWorkTask"));
        taskSubject.ranAfter(":upToDateTask");
        assertThat(failureStrategy.message).isNull();

        failureStrategy = new FakeFailureStrategy();
        taskSubject = FACTORY.getSubject(failureStrategy, taskStateList.getTask(":upToDateTask"));
        taskSubject.ranAfter(":didWorkTask");
        assertThat(failureStrategy.message)
                .isEqualTo("Not true that :upToDateTask was executed after <:didWorkTask>");
    }

    private void assertSuccess(
            @NonNull String taskName, @NonNull Consumer<GradleTaskSubject> taskSubjectConsumer) {
        FakeFailureStrategy failureStrategy = new FakeFailureStrategy();
        GradleTaskSubject taskSubject =
                FACTORY.getSubject(failureStrategy, taskStateList.getTask(taskName));
        taskSubjectConsumer.accept(taskSubject);
        assertThat(failureStrategy.message).isNull();
    }

    private void assertFailure(
            @NonNull String taskName,
            @NonNull Consumer<GradleTaskSubject> taskSubjectConsumer,
            @NonNull String failureMessage) {
        FakeFailureStrategy failureStrategy = new FakeFailureStrategy();
        GradleTaskSubject taskSubject =
                FACTORY.getSubject(failureStrategy, taskStateList.getTask(taskName));
        taskSubjectConsumer.accept(taskSubject);
        assertThat(failureStrategy.message).isEqualTo(failureMessage);
    }

    private static TaskProgressEvent upToDate(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " UP-TO-DATE",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, true, false));
    }

    private static TaskProgressEvent fromCache(String name) {
        // When the task's output is retrieved from the cache, Gradle returns both upToDate() and
        // fromCache() as true (see https://github.com/gradle/gradle/issues/5252).
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " FROM_CACHE",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, true, true));
    }

    private static TaskProgressEvent didWork(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " SUCCESS",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, false, false));
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
                new DefaultTaskFailureResult(0, 5, null));
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
