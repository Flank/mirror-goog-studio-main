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
package com.android.tools.deployer;

import java.util.List;

public class DeployMetric {

    public static long UNFINISHED = -1;

    private final String name;
    private String status = null;
    private final long threadId;
    private final long startMs;
    private long endMs = UNFINISHED;

    public DeployMetric(String name) {
        this(name, System.currentTimeMillis(), Thread.currentThread().getId());
    }

    public DeployMetric(String name, long startMs) {
        this(name, startMs, Thread.currentThread().getId());
    }

    private DeployMetric(String name, long startMs, long threadId) {
        this.name = name;
        this.threadId = threadId;
        this.startMs = startMs;
    }

    public void finish(String status, List<DeployMetric> metrics) {
        this.finish(status);
        metrics.add(this);
    }

    public void finish(String status) {
        assert endMs == UNFINISHED;
        this.status = status;
        endMs = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public boolean hasStatus() {
        return status != null;
    }

    public long getThreadId() {
        return threadId;
    }

    public long getStartTimeMs() {
        return startMs;
    }

    public long getEndTimeMs() {
        assert endMs != UNFINISHED;
        return endMs;
    }
}
