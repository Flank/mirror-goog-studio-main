/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Definition of a queued job. A job has a title, a task to execute, a latch to signal its
 * completion and a boolean result for success or failure.
 */
public class Job<T> {

    private final String jobTitle;
    private final Task<T> task;
    private final ListenableFuture<?> resultFuture;

    public Job(String jobTile, Task<T> task, ListenableFuture<?> resultFuture) {
        this.jobTitle = jobTile;
        this.task = task;
        this.resultFuture = resultFuture;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void runTask(@NonNull JobContext<T> jobContext) throws IOException {
        task.run(this, jobContext);
    }

    public void finished() {
        task.finished();
    }

    public void error(@Nullable Throwable e) {
        task.error(e);
    }

    public boolean await() throws InterruptedException {

        try {
            resultFuture.get();
            return true;
        } catch (ExecutionException e) {
            return false;
        }
    }

    public void awaitRethrowExceptions()
            throws InterruptedException, ExecutionException {
        resultFuture.get();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("\ntitle", jobTitle)
                .add("\ntask", task)
                .add("\nfuture", resultFuture)
                .toString();
    }
}
