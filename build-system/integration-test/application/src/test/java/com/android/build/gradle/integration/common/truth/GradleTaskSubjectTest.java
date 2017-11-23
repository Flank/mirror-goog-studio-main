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
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult;
import org.junit.Before;
import org.junit.Test;

public class GradleTaskSubjectTest {

    private TaskStateList taskStateList;
    private FakeFailureStrategy failureStrategy;

    @Before
    public void setup() throws Exception {
        String output = Resources.toString(
                Resources.getResource(
                        "com/android/build/gradle/integration/common/truth/build-output.txt"),
                Charsets.UTF_8);
        ImmutableList.Builder<ProgressEvent> events = ImmutableList.builder();

        events.add(upToDate(":preBuild"));
        events.add(ran(":prepareDebugAtomDependencies"));
        events.add(ran(":generateDebugAtomResValues"));
        events.add(ran(":mergeDebugAtomResources"));
        events.add(ran(":dataBindingProcessLayoutsDebugAtom"));

        taskStateList = new TaskStateList(events.build(), output);
        failureStrategy = new FakeFailureStrategy();
    }

    @Test
    public void wasExecuted() throws Exception {
        validateTaskCheckResult(
                ":dataBindingProcessLayoutsDebugAtom",
                ":taskThatDidntRun",
                "wasExecuted",
                "Not true that :taskThatDidntRun was executed",
                GradleTaskSubject::wasExecuted);
    }

    @Test
    public void wasNotExecuted() throws Exception {
        validateTaskCheckResult(
                ":taskThatDidntRun",
                ":dataBindingProcessLayoutsDebugAtom",
                "wasNotExecuted",
                "Not true that :dataBindingProcessLayoutsDebugAtom was not executed",
                GradleTaskSubject::wasNotExecuted);
    }

    @Test
    public void wasUpToDate() throws Exception {
        validateTaskCheckResult(
                ":preBuild",
                ":prepareDebugAtomDependencies",
                "wasUpToDate",
                "Not true that :prepareDebugAtomDependencies was UP-TO-DATE",
                GradleTaskSubject::wasUpToDate);
    }

    @Test
    public void wasNotUpToDate() throws Exception {
        validateTaskCheckResult(
                ":prepareDebugAtomDependencies",
                ":preBuild",
                "wasNotUpToDate",
                "Not true that :preBuild was not UP-TO-DATE",
                GradleTaskSubject::wasNotUpToDate);
    }

    @Test
    public void hadChangedInputs() throws Exception {
        Set<String> inputChangedTasks = taskStateList.getInputChangedTasks();
        assertThat(inputChangedTasks).hasSize(1);
        assertThat(Iterables.getOnlyElement(inputChangedTasks))
                .isEqualTo(":mergeDebugAtomResources");
    }

    @Test
    public void ranBefore() throws Exception {
        validateTaskCheckResult(
                ":generateDebugAtomResValues", ":mergeDebugAtomResources",
                ":mergeDebugAtomResources", ":generateDebugAtomResValues",
                "ranBefore",
                "Not true that :mergeDebugAtomResources was executed before <:generateDebugAtomResValues>",
                GradleTaskSubject::ranBefore);
    }

    @Test
    public void ranAfter() throws Exception {
        validateTaskCheckResult(
                ":mergeDebugAtomResources", ":generateDebugAtomResValues",
                ":generateDebugAtomResValues", ":mergeDebugAtomResources",
                "ranAfter",
                "Not true that :generateDebugAtomResValues was executed after <:mergeDebugAtomResources>",
                GradleTaskSubject::ranAfter);
    }

    private void validateTaskCheckResult(
            @NonNull String validTask,
            @NonNull String invalidTask,
            @NonNull String functionName,
            @NonNull String errorMessageForInvalidTask,
            @NonNull Consumer<GradleTaskSubject> consumer) {
        // check valid task
        consumer.accept(FACTORY.getSubject(failureStrategy, taskStateList.getTask(validTask)));
        assertThat(failureStrategy.message)
                .named("Error for " + functionName + " on task " + validTask)
                .isNull();

        failureStrategy.reset();

        // and invalid task
        consumer.accept(FACTORY.getSubject(failureStrategy, taskStateList.getTask(invalidTask)));
        assertThat(failureStrategy.message)
                .named("Error for " + functionName + " on task " + invalidTask)
                .isEqualTo(errorMessageForInvalidTask);
    }

    private void validateTaskCheckResult(
            @NonNull String validTask, @NonNull String validTaskParam,
            @NonNull String invalidTask, @NonNull String invalidTaskParam,
            @NonNull String functionName,
            @NonNull String errorMessageForInvalidTask,
            @NonNull BiConsumer<GradleTaskSubject, String> consumer) {
        // check valid task
        consumer.accept(FACTORY.getSubject(failureStrategy, taskStateList.getTask(validTask)), validTaskParam);
        assertThat(failureStrategy.message)
                .named("Error for " + functionName + " on task " + validTask + " with param " + validTaskParam)
                .isNull();

        failureStrategy.reset();

        // and invalid task
        consumer.accept(FACTORY.getSubject(failureStrategy, taskStateList.getTask(invalidTask)), invalidTaskParam);
        assertThat(failureStrategy.message)
                .named("Error for " + functionName + " on task " + invalidTask + " with param " + invalidTaskParam)
                .isEqualTo(errorMessageForInvalidTask);
    }


    private static TaskProgressEvent upToDate(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " UP-TO-DATE",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, true, false));
    }

    private static TaskProgressEvent ran(String name) {
        return new DefaultTaskFinishEvent(
                0,
                "Task :" + name + " SUCCESS",
                new FakeTaskOperationDescriptor(name),
                new DefaultTaskSuccessResult(0, 5, false, false));
    }

    private static class FakeTaskOperationDescriptor
            implements TaskOperationDescriptor, OperationDescriptor {

        final String name;

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
