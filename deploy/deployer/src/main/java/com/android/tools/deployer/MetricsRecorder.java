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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.tracer.Trace;
import java.util.ArrayList;
import java.util.List;

// TODO: At the moment, this class exposes mutable lists for easy compatibility with existing
//  metrics code. This should ideally be refactored to let this class handle most of the internals
//  of recording metrics.
public class MetricsRecorder {
    private final ArrayList<DeployMetric> deployMetrics;
    private final ArrayList<Deploy.AgentExceptionLog> agentExceptionLogs;

    public MetricsRecorder() {
        this.deployMetrics = new ArrayList<>();
        this.agentExceptionLogs = new ArrayList<>();
    }

    public void start(String name) {
        deployMetrics.add(new DeployMetric(name));
        Trace.begin(name);
    }

    public void finish() {
        currentMetric().finish("Success");
        Trace.end();
    }

    public void finish(Enum<?> status) {
        currentMetric().finish(status.name());
        Trace.end();
    }

    public List<DeployMetric> getDeployMetrics() {
        return deployMetrics;
    }

    public List<Deploy.AgentExceptionLog> getAgentFailures() {
        return agentExceptionLogs;
    }

    void add(DeployMetric metric) {
        deployMetrics.add(metric);
    }

    void add(List<Deploy.AgentExceptionLog> logs) {
        agentExceptionLogs.addAll(logs);
    }

    private DeployMetric currentMetric() {
        return deployMetrics.get(deployMetrics.size() - 1);
    }
}
