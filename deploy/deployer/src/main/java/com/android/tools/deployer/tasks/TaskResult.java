/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer.tasks;

import com.android.tools.deployer.DeployMetric;
import com.android.tools.deployer.DeployerException;
import java.util.List;
import java.util.stream.Collectors;

public class TaskResult {
    private final DeployerException exception;
    private final List<Task<?>> tasks;

    public TaskResult(List<Task<?>> tasks) {
        this(tasks, null);
    }

    public TaskResult(List<Task<?>> tasks, DeployerException exception) {
        this.tasks = tasks;
        this.exception = exception;
    }

    /** @return the metrics for completed tasks, filtering out non-started and dropped tasks. */
    public List<DeployMetric> getMetrics() {
        return tasks.stream()
                .map(Task::getMetric)
                .filter(TaskResult::shouldIncludeMetric)
                .collect(Collectors.toList());
    }

    /**
     * @return the exception that was thrown if the execution was not a success. Returns null if the
     *     execution succeeded.
     */
    public DeployerException getException() {
        return exception;
    }

    public boolean isSuccess() {
        return exception == null;
    }

    private static boolean shouldIncludeMetric(DeployMetric metric) {
        if (metric == null) {
            return false;
        }
        return "Success".equals(metric.getStatus()) || "Failed".equals(metric.getStatus());
    }
}
