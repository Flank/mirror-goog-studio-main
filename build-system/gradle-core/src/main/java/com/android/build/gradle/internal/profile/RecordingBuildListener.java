
/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.tasks.VariantAwareTask;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.ProfileRecordWriter;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleTaskExecution;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;

/**
 * Implementation of the {@link TaskExecutionListener} that records the execution span of
 * tasks execution and records such spans using the {@link Recorder} facilities.
 */
public class RecordingBuildListener implements TaskExecutionListener {

    @NonNull private final ProfileRecordWriter recordWriter;
    // map of outstanding tasks executing, keyed by their path.
    @NonNull private final Map<String, TaskProfilingRecord> taskRecords = new ConcurrentHashMap<>();
    @NonNull
    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(RecordingBuildListener.class);

    RecordingBuildListener(@NonNull ProfileRecordWriter recorder) {
        recordWriter = recorder;
    }

    @Override
    public void beforeExecute(@NonNull Task task) {
        GradleBuildProfileSpan.Builder builder = GradleBuildProfileSpan.newBuilder();
        builder.setType(ExecutionType.TASK_EXECUTION);
        builder.setId(recordWriter.allocateRecordId());
        builder.setThreadId(Thread.currentThread().getId());

        TaskProfilingRecord taskRecord =
                new TaskProfilingRecord(
                        recordWriter,
                        builder,
                        task.getPath(),
                        task.getProject().getPath(),
                        getVariantName(task));

        taskRecords.put(task.getPath(), taskRecord);
    }

    @Override
    public void afterExecute(@NonNull Task task, @NonNull TaskState taskState) {

        try {
            TaskProfilingRecord taskRecord = taskRecords.get(task.getPath());
            GradleBuildProfileSpan.Builder record = taskRecord.getSpanBuilder();

            //noinspection ThrowableResultOfMethodCallIgnored Just logging the failure.

            record.setTask(
                    GradleTaskExecution.newBuilder()
                            .setType(
                                    AnalyticsUtil.getTaskExecutionType(task.getClass()).getNumber())
                            .setDidWork(taskState.getDidWork())
                            .setSkipped(taskState.getSkipped())
                            .setUpToDate(taskState.getUpToDate())
                            .setFailed(taskState.getFailure() != null));

            taskRecord.setTaskFinished();
            // check that all workers are done before closing this span.
            if (taskRecord.allWorkersFinished()) {
                taskRecord.writeTaskSpan();
                closeTaskRecord(task.getPath());
            }
        } finally {
            ProcessProfileWriter.recordMemorySample();
        }
    }

    public TaskProfilingRecord getTaskRecord(String taskPath) {
        return taskRecords.get(taskPath);
    }


    @Nullable
    private static String getVariantName(@NonNull Task task) {
        if (!(task instanceof VariantAwareTask)) {
            return null;
        }
        String variantName = ((VariantAwareTask) task).getVariantName();
        if (variantName.isEmpty()) {
            return null;
        }
        return variantName;
    }

    public void closeTaskRecord(@NonNull String taskPath) {
        taskRecords.remove(taskPath);
    }

    @Nullable
    public WorkerProfilingRecord getWorkerRecord(@NonNull String taskPath, @NonNull String worker) {
        TaskProfilingRecord taskRecord = getTaskRecord(taskPath);
        if (taskRecord == null) {
            logger.warning("No task by the name " + taskPath);
        }
        return taskRecord.get(worker);
    }
}
