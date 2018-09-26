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
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class which represents a specific perfgate analyzer. This class specifies a WindowDeviationInput
 * proto supporting multiple ToleranceChecks (see {@link MedianToleranceParams} and {@link
 * MeanToleranceParams}). The analyzer is considered failed iff all specified tolerance checks
 * failed.
 */
public class WindowDeviationAnalyzer implements Analyzer {

    @NonNull private final String type = "WindowDeviationAnalyzer";
    @NonNull private final MetricAggregate metricAggregate;
    private final int runInfoQueryLimit;
    private final int recentWindowSize;
    private final List<MeanToleranceParams> meanTolerances;
    private final List<MedianToleranceParams> medianTolerances;

    public WindowDeviationAnalyzer(@NonNull Builder builder) {
        metricAggregate = builder.metricAggregate;
        runInfoQueryLimit = builder.runInfoQueryLimit;
        recentWindowSize = builder.recentWindowSize;
        meanTolerances = new ArrayList<>(builder.meanTolerances);
        medianTolerances = new ArrayList<>(builder.medianTolerances);
        Preconditions.checkState(
                runInfoQueryLimit + 1 - recentWindowSize >= 3,
                "(runInfoQueryLimit + 1 - recentWindowSize) must be greater than or equal to 3.");
    }

    public static class Builder {
        @NonNull private MetricAggregate metricAggregate = MetricAggregate.MEAN;
        private int runInfoQueryLimit = 50;
        private int recentWindowSize = 11;
        private final List<MeanToleranceParams> meanTolerances = new ArrayList<>();
        private final List<MedianToleranceParams> medianTolerances = new ArrayList<>();

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
        public Builder addMedianTolerance(@NonNull MedianToleranceParams toleranceParams) {
            this.medianTolerances.add(toleranceParams);
            return this;
        }

        @NonNull
        public Builder addMeanTolerance(@NonNull MeanToleranceParams toleranceParams) {
            this.meanTolerances.add(toleranceParams);
            return this;
        }

        @NonNull
        public WindowDeviationAnalyzer build() {
            Preconditions.checkState(!meanTolerances.isEmpty() || !medianTolerances.isEmpty(),
                                     "At least one tolerance parameter must be set.");
            return new WindowDeviationAnalyzer(this);
        }
    }

    @Override
    public void outputJson(@NonNull JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("type").value(type);
        writer.name("metricAggregate").value(metricAggregate.name());
        writer.name("runInfoQueryLimit").value(String.valueOf(runInfoQueryLimit));
        writer.name("recentWindowSize").value(String.valueOf(recentWindowSize));
        writer.name("toleranceParams").beginArray();
        {
            for (MeanToleranceParams param : meanTolerances) {
                writer.beginObject();
                writer.name("type").value("Mean");
                writer.name("constTerm").value(String.valueOf(param.constTerm));
                writer.name("meanCoeff").value(String.valueOf(param.meanCoeff));
                writer.name("stddevCoeff").value(String.valueOf(param.stddevCoeff));
                writer.endObject();
            }
            for (MedianToleranceParams param : medianTolerances) {
                writer.beginObject();
                writer.name("type").value("Median");
                writer.name("constTerm").value(String.valueOf(param.constTerm));
                writer.name("medianCoeff").value(String.valueOf(param.medianCoeff));
                writer.name("madCoeff").value(String.valueOf(param.madCoeff));
                writer.endObject();
            }
        }
        writer.endArray();
        writer.endObject();
    }

    /**
     * <p>When this {@link MedianToleranceParams} is added to an {@link Analyzer}, the analyzer will
     * compare the medians of a recent window and a historic window each time data is uploaded. The
     * analyzer will flag a regression if (recent median) > (historic median) + threshold, where the
     * threshold equals constTerm + medianCoeff * (historic median) + madCoeff * (historic MAD).
     */
    public static class MedianToleranceParams {
        private final double constTerm;
        private final double medianCoeff;
        private final double madCoeff;

        public MedianToleranceParams(@NonNull Builder builder) {
            constTerm = builder.constTerm;
            medianCoeff = builder.medianCoeff;
            madCoeff = builder.madCoeff;
        }

        public static class Builder {
            private double constTerm = 0.0;
            private double medianCoeff = 0.05;
            private double madCoeff = 1.0;

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
            public MedianToleranceParams build() {
                Preconditions.checkState(
                        constTerm > 0 || medianCoeff > 0 || madCoeff > 0,
                        "One of constTerm, medianCoeff, or madCoeff must be positive");
                return new MedianToleranceParams(this);
            }
        }
    }

    /**
     * <p>When this {@link MeanToleranceParams} is added to an {@link Analyzer}, the analyzer will
     * compare the means of a recent window and a historic window each time data is uploaded. The
     * analyzer will flag a regression if (recent mean) > (historic mean) + threshold, where the
     * threshold equals constTerm + meanCoeff * (historic mean) + stddevCoeff * (historic stddev).
     */
    public static class MeanToleranceParams {
        private final double constTerm;
        private final double meanCoeff;
        private final double stddevCoeff;

        public MeanToleranceParams(@NonNull Builder builder) {
            constTerm = builder.constTerm;
            meanCoeff = builder.meanCoeff;
            stddevCoeff = builder.stddevCoeff;
        }

        public static class Builder {
            private double constTerm = 0.0;
            private double meanCoeff = 0.05;
            private double stddevCoeff = 2.0;

            @NonNull
            public Builder setConstTerm(double constTerm) {
                Preconditions.checkArgument(constTerm >= 0, "constTerm must be non-negative.");
                this.constTerm = constTerm;
                return this;
            }

            @NonNull
            public Builder setMeanCoeff(double meanCoeff) {
                Preconditions.checkArgument(meanCoeff >= 0, "meanCoeff must be non-negative.");
                this.meanCoeff = meanCoeff;
                return this;
            }

            @NonNull
            public Builder setStddevCoeff(double stddevCoeff) {
                Preconditions.checkArgument(stddevCoeff >= 0, "stddevCoeff must be non-negative.");
                this.stddevCoeff = stddevCoeff;
                return this;
            }

            @NonNull
            public MeanToleranceParams build() {
                Preconditions.checkState(
                        constTerm > 0 || meanCoeff > 0 || stddevCoeff > 0,
                        "One of constTerm, meanCoeff, or stddevCoeff must be positive");
                return new MeanToleranceParams(this);
            }
        }
    }
}
