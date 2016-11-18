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

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * List of the task state for a build.
 */
public class TaskStateList {

    public static class TaskInfo {
        private final String taskName;
        private final boolean wasExecuted;
        private final boolean upToDate;
        private final boolean inputChanged;
        private final TaskStateList taskStateList;

        public TaskInfo(String taskName,
                boolean wasExecuted,
                boolean upToDate,
                boolean inputChanged,
                TaskStateList taskStateList) {
            this.taskName = taskName;
            this.wasExecuted = wasExecuted;
            this.upToDate = upToDate;
            this.inputChanged = inputChanged;
            this.taskStateList = taskStateList;
        }

        public String getTaskName() {
            return taskName;
        }

        public boolean isWasExecuted() {
            return wasExecuted;
        }

        public boolean isUpToDate() {
            return upToDate;
        }

        public boolean isInputChanged() {
            return inputChanged;
        }

        TaskStateList getTaskStateList() {
            return taskStateList;
        }
    }

    private static final Pattern UP_TO_DATE_PATTERN = Pattern.compile("(\\S+)\\s+UP-TO-DATE");

    private static final Pattern INPUT_CHANGED_PATTERN = Pattern.compile(
            "Value of input property '.*' has changed for task ':(\\S+)'");

    public static final Pattern EXECUTED_PATTERN
            = Pattern.compile("Tasks to be executed: \\[(.*)\\]");

    @NonNull
    private final Map<String, TaskInfo> taskInfoList;
    @NonNull
    private final String gradleOutput;
    @NonNull
    private final List<String> orderedTasks;
    @NonNull
    private final Set<String> upToDateTasks;
    @NonNull
    private final Set<String> inputChangedTasks;

    public TaskStateList(@NonNull String gradleOutput) {
        this.gradleOutput = gradleOutput;
        List<String> lines = Arrays.stream(gradleOutput.split("\n"))
                .map(String::trim)
                .collect(Collectors.toList());

        Optional<String> taskLine = lines.stream()
                .map(EXECUTED_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(match -> match.group(1))
                .findFirst();
        if (!taskLine.isPresent()) {
            throw new RuntimeException("Unable to determine task lists from Gradle output");
        }

        orderedTasks = Arrays.stream(taskLine.get().split(", "))
                .map(task -> {
                    Matcher m = Pattern.compile("task \'(.*)\'").matcher(task);
                    checkState(m.matches());
                    return m.group(1);
                })
                .collect(Collectors.toList());

        upToDateTasks = getTasksMatching(UP_TO_DATE_PATTERN);
        inputChangedTasks = getTasksMatching(INPUT_CHANGED_PATTERN);

        taskInfoList = Maps.newHashMapWithExpectedSize(orderedTasks.size());

        for (String taskName : orderedTasks) {
            taskInfoList.put(taskName, new TaskInfo(
                    taskName,
                    true,
                    upToDateTasks.contains(taskName),
                    inputChangedTasks.contains(taskName),
                    this));
        }
    }

    @NonNull
    public TaskInfo getTask(@NonNull String name) {
        // if the task-info is missing, then create one for a non executed task.
        return taskInfoList.computeIfAbsent(name,
                k -> new TaskInfo(name, false /*wasExecuted*/, false, false, this));
    }

    @NonNull
    public Set<String> getUpToDateTasks() {
        return upToDateTasks;
    }

    @NonNull
    public Set<String> getInputChangedTasks() {
        return inputChangedTasks;
    }

    private Set<String> getTasksMatching(@NonNull Pattern pattern) {
        Set<String> result = Sets.newHashSet();
        Matcher matcher = pattern.matcher(gradleOutput);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    int getTaskIndex(String taskName) {
        return orderedTasks.indexOf(taskName);
    }

}
