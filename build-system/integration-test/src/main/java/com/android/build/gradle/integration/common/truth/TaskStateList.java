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

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.TaskSkippedResult;
import org.gradle.tooling.events.task.TaskSuccessResult;

/**
 * List of the task state for a build.
 */
public class TaskStateList {

    public static class TaskInfo {

        private final String taskName;
        private final boolean wasExecuted;
        private final boolean upToDate;
        private final boolean inputChanged;
        private final boolean failed;
        private final boolean skipped;
        private final TaskStateList taskStateList;

        public TaskInfo(
                String taskName,
                boolean wasExecuted,
                boolean upToDate,
                boolean inputChanged,
                boolean failed,
                boolean skipped,
                TaskStateList taskStateList) {
            this.taskName = taskName;
            this.wasExecuted = wasExecuted;
            this.upToDate = upToDate;
            this.inputChanged = inputChanged;
            this.failed = failed;
            this.skipped = skipped;
            this.taskStateList = taskStateList;
        }

        public String getTaskName() {
            return taskName;
        }

        public boolean isWasExecuted() {
            return wasExecuted;
        }

        public boolean isUpToDate() {
            if (!wasExecuted) {
                throw new IllegalStateException("Task " + getTaskName() + " was not executed");
            }
            return upToDate;
        }

        public boolean isInputChanged() {
            if (!wasExecuted) {
                throw new IllegalStateException("Task " + getTaskName() + " was not executed");
            }
            return inputChanged;
        }

        public boolean isSkipped() {
            if (!wasExecuted) {
                throw new IllegalStateException("Task " + getTaskName() + " was not executed");
            }
            return skipped;
        }

        public boolean failed() {
            if (!wasExecuted) {
                throw new IllegalStateException("Task " + getTaskName() + " was not executed");
            }
            return failed;
        }

        TaskStateList getTaskStateList() {
            return taskStateList;
        }
    }


    private static final Pattern INPUT_CHANGED_PATTERN =
            Pattern.compile("Value of input property '.*' has changed for task '(\\S+)'");

    public static final Pattern EXECUTED_PATTERN
            = Pattern.compile("Tasks to be executed: \\[(.*)\\]");

    @NonNull
    private final Map<String, TaskInfo> taskInfoList;
    @NonNull private final ImmutableSet<String> allTasks;
    @NonNull private final ImmutableList<String> orderedTasks;
    @NonNull private final ImmutableSet<String> upToDateTasks;
    @NonNull private final ImmutableSet<String> notUpToDateTasks;
    @NonNull private final ImmutableSet<String> inputChangedTasks;
    @NonNull private final ImmutableSet<String> skippedTasks;
    @NonNull private final ImmutableSet<String> failedTasks;

    public TaskStateList(
            @NonNull List<ProgressEvent> progressEvents, @NonNull String gradleOutput) {

        ImmutableList.Builder<String> orderedTasksBuilder = ImmutableList.builder();
        ImmutableSet.Builder<String> upToDateTasksBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> notUpToDateTasksBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> skippedTasksBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> failedTasksBuilder = ImmutableSet.builder();

        for (ProgressEvent progressEvent : progressEvents) {
            if (progressEvent instanceof TaskFinishEvent) {
                String name = progressEvent.getDescriptor().getName();
                orderedTasksBuilder.add(name);

                TaskFinishEvent taskFinishEvent = (TaskFinishEvent) progressEvent;
                TaskOperationResult result = taskFinishEvent.getResult();

                if (result instanceof TaskSuccessResult) {
                    TaskSuccessResult successResult = (TaskSuccessResult) result;
                    if (successResult.isUpToDate()) {
                        upToDateTasksBuilder.add(name);
                    } else {
                        notUpToDateTasksBuilder.add(name);
                    }
                } else if (result instanceof TaskFailureResult) {
                    failedTasksBuilder.add(name);
                } else if (result instanceof TaskSkippedResult) {
                    skippedTasksBuilder.add(name);
                }
            }
        }

        orderedTasks = orderedTasksBuilder.build();
        allTasks = ImmutableSet.copyOf(orderedTasks);
        upToDateTasks = upToDateTasksBuilder.build();
        notUpToDateTasks = notUpToDateTasksBuilder.build();
        failedTasks = failedTasksBuilder.build();
        skippedTasks = skippedTasksBuilder.build();
        inputChangedTasks = getInputChangedTasks(gradleOutput, notUpToDateTasks);


        taskInfoList = Maps.newHashMapWithExpectedSize(orderedTasks.size());

        for (String taskName : orderedTasks) {
            taskInfoList.put(
                    taskName,
                    new TaskInfo(
                            taskName,
                            true,
                            upToDateTasks.contains(taskName),
                            inputChangedTasks.contains(taskName),
                            failedTasks.contains(taskName),
                            skippedTasks.contains(taskName),
                            this));
        }
    }

    private static ImmutableSet<String> getInputChangedTasks(
            @NonNull String gradleOutput, @NonNull ImmutableSet<String> notUpToDateTasks) {
        if (!EXECUTED_PATTERN.matcher(gradleOutput).find()) {
            throw new RuntimeException("Unable to determine task lists from Gradle output");
        }

        ImmutableSet.Builder<String> result = ImmutableSet.builder();
        Matcher matcher = INPUT_CHANGED_PATTERN.matcher(gradleOutput);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!notUpToDateTasks.contains(candidate)) {
                throw new RuntimeException("Found unexpected input changed task " + candidate);
            }
            result.add(candidate);
        }
        return result.build();
    }

    @NonNull
    public TaskInfo getTask(@NonNull String name) {
        // if the task-info is missing, then create one for a non executed task.
        return taskInfoList.computeIfAbsent(
                name,
                k -> new TaskInfo(name, false /*wasExecuted*/, false, false, false, false, this));
    }

    @NonNull
    public Set<String> getUpToDateTasks() {
        return upToDateTasks;
    }

    @NonNull
    public Set<String> getInputChangedTasks() {
        return inputChangedTasks;
    }

    @NonNull
    public Set<String> getNotUpToDateTasks() {
        return notUpToDateTasks;
    }

    @NonNull
    public List<String> getAllTasks() {
        return orderedTasks;
    }

    int getTaskIndex(String taskName) {
        Preconditions.checkArgument(allTasks.contains(taskName), "Task %s not run", taskName);
        return orderedTasks.indexOf(taskName);
    }

}
