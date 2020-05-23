/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.os;

/**
 * During testing this is used instead of the version in android.jar, since all the methods there
 * are stubbed out.
 */
public abstract class AsyncTask<Params, Progress, Result> {
    private boolean cancelled = false;
    private Status status = Status.PENDING;

    public final boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        return true;
    }

    public final boolean isCancelled() {
        return cancelled;
    }

    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        new Thread(
                        () -> {
                            status = Status.RUNNING;
                            doInBackground(params);
                            status = Status.FINISHED;
                        })
                .start();
        return null;
    }

    protected abstract Result doInBackground(Params... var1);

    public final AsyncTask.Status getStatus() {
        return status;
    }

    public enum Status {
        PENDING,
        RUNNING,
        FINISHED;
    }
}
