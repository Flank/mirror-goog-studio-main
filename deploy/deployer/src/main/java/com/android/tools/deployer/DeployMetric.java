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

import java.util.Collection;

public class DeployMetric {

    public static long UNFINISHED = -1;

    private final String name;
    private String status = null;
    private final long threadId;
    private final long startNs;
    private long endNs;

    public DeployMetric(String name) {
        this(name, System.nanoTime());
    }

    public DeployMetric(String name, long startNs) {
        this(name, startNs, UNFINISHED);
    }

    public DeployMetric(String name, long startNs, long endNs) {
        this(name, startNs, endNs, Thread.currentThread().getId());
    }

    private DeployMetric(String name, long startNs, long endNs, long threadId) {
        this.name = name;
        this.threadId = threadId;
        this.startNs = startNs;
        this.endNs = endNs;
    }

    public void finish(String status, Collection<DeployMetric> metrics) {
        this.finish(status);
        metrics.add(this);
    }

    public void finish(String status) {
        assert endNs == UNFINISHED;
        this.status = status;
        endNs = System.nanoTime();
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

    public long getStartTimeNs() {
        return startNs;
    }

    public long getEndTimeNs() {
        assert endNs != UNFINISHED;
        return endNs;
    }
}
