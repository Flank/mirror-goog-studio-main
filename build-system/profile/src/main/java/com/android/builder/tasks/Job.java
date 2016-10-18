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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Definition of a queued job. A job has a title, a task to execute, a latch to signal its
 * completion and a boolean result for success or failure.
 */
public class Job<T> {

    private final String mJobTitle;
    private final Task<T> mTask;
    private final BooleanLatch mBooleanLatch;
    private final AtomicBoolean mResult = new AtomicBoolean(false);
    private final AtomicReference<Exception> mException;

    public Job(String jobTile, Task<T> task) {
        mJobTitle = jobTile;
        mTask = task;
        mBooleanLatch = new BooleanLatch();
        mException = new AtomicReference(null);
    }

    public String getJobTitle() {
        return mJobTitle;
    }

    public void runTask(@NonNull JobContext<T> jobContext) throws IOException {
        mTask.run(this, jobContext);
    }

    public void finished() {
        mResult.set(true);
        mBooleanLatch.signal();
    }

    public void error(@Nullable Exception e) {
        mResult.set(false);
        mException.set(e);
        mBooleanLatch.signal();
    }

    @Nullable
    public Exception getFailureReason() {
        return mException.get();
    }

    public boolean await() throws InterruptedException {

        mBooleanLatch.await();
        return mResult.get();
    }

    public boolean awaitRethrowExceptions() throws InterruptedException, RuntimeException {
        boolean result = await();
        if (!result && mException.get() != null) {
            throw new RuntimeException(mException.get());
        }
        return result;
    }

    public boolean failed() {
        return !mResult.get();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("\ntitle", mJobTitle)
                .add("\ntask", mTask)
                .add("\nlatch", mBooleanLatch)
                .add("\nresult", mResult.get())
                .toString();
    }
}
