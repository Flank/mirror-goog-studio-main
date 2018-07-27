/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.perflogger;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class MedianWindowDeviationAnalyzer implements Analyzer {

    @NonNull private final String type = "MedianWindowDeviationAnalyzer";
    @NonNull private final MetricAggregate metricAggregate;
    private final int runInfoQueryLimit;
    private final int recentWindowSize;
    private final double constTerm;
    private final double medianCoeff;
    private final double madCoeff;

    public MedianWindowDeviationAnalyzer(@NonNull Builder builder) {
        metricAggregate = builder.metricAggregate;
        runInfoQueryLimit = builder.runInfoQueryLimit;
        recentWindowSize = builder.recentWindowSize;
        constTerm = builder.constTerm;
        medianCoeff = builder.medianCoeff;
        madCoeff = builder.madCoeff;
        Preconditions.checkState(
                runInfoQueryLimit + 1 - recentWindowSize >= 3,
                "(runInfoQueryLimit + 1 - recentWindowSize) must be greater than or equal to 3.");
        Preconditions.checkState(
                constTerm > 0 || medianCoeff > 0 || madCoeff > 0,
                "One of constTerm, medianCoeff, or madCoeff must be positive");
    }

    public static class Builder {
        @NonNull private MetricAggregate metricAggregate = MetricAggregate.MEDIAN;
        private int runInfoQueryLimit = 50;
        private int recentWindowSize = 11;
        private double constTerm = 0.0;
        private double medianCoeff = 0.05;
        private double madCoeff = 1.0;

        @NonNull
        public Builder setMetricAggregate(@NonNull MetricAggregate metricAggregate) {
            this.metricAggregate = metricAggregate;
            return this;
        }

        @NonNull
        public Builder setRunInfoQueryLimit(int runInfoQueryLimit) {
            Preconditions.checkArgument(
                    runInfoQueryLimit > 0, "runInfoQueryLimit must be a positive integer.");
            this.runInfoQueryLimit = runInfoQueryLimit;
            return this;
        }

        @NonNull
        public Builder setRecentWindowSize(int recentWindowSize) {
            Preconditions.checkArgument(
                    recentWindowSize > 0, "recentWindowSize must be a positive integer.");
            this.recentWindowSize = recentWindowSize;
            return this;
        }

        @NonNull
        public Builder setConstTerm(double constTerm) {
            Preconditions.checkArgument(constTerm >= 0, "constTerm must be non-negative.");
            this.constTerm = constTerm;
            return this;
        }

        @NonNull
        public Builder setMedianCoeff(double medianCoeff) {
            Preconditions.checkArgument(medianCoeff >= 0, "medianCoeff must be non-negative.");
            this.medianCoeff = medianCoeff;
            return this;
        }

        @NonNull
        public Builder setMadCoeff(double madCoeff) {
            Preconditions.checkArgument(madCoeff >= 0, "madCoeff must be non-negative.");
            this.madCoeff = madCoeff;
            return this;
        }

        @NonNull
        public MedianWindowDeviationAnalyzer build() {
            return new MedianWindowDeviationAnalyzer(this);
        }
    }

    @Override
    public Map<String, String> getNameValueMap() {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        builder.put("type", type);
        builder.put("metricAggregate", metricAggregate.name());
        builder.put("runInfoQueryLimit", String.valueOf(runInfoQueryLimit));
        builder.put("recentWindowSize", String.valueOf(recentWindowSize));
        builder.put("constTerm", String.valueOf(constTerm));
        builder.put("medianCoeff", String.valueOf(medianCoeff));
        builder.put("madCoeff", String.valueOf(madCoeff));
        return builder.build();
    }
}
