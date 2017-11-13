/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class PerformanceTestUtil {

    private PerformanceTestUtil() {
        // Static utility class
    }

    /**
     * The benchmark modes that don't make sense.
     *
     * <p>These are blacklisted from uploading too.
     */
    private static final EnumSet<Logging.BenchmarkMode> BLACKLISTED_BENCHMARK_MODES =
            EnumSet.of(
                    // Proto enum implementation detail
                    Logging.BenchmarkMode.UNRECOGNIZED,
                    Logging.BenchmarkMode.MODE_UNSPECIFIED,
                    // Deprecated benchmark modes.
                    Logging.BenchmarkMode.INSTANT_RUN_BUILD_INC_JAVA_DEPRECATED);

    /**
     * All benchmark modes that make sense.
     *
     * <p>This is just all elements of the enum from the proto with the {@link
     * #BLACKLISTED_BENCHMARK_MODES} removed.
     */
    public static final EnumSet<Logging.BenchmarkMode> BENCHMARK_MODES =
            EnumSet.complementOf(BLACKLISTED_BENCHMARK_MODES);

    /** Returns the type of change that should be made for a benchmark mode. */
    @NonNull
    static EditType getEditType(@NonNull Logging.BenchmarkMode benchmarkMode) {
        switch (benchmarkMode) {
            case BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                return EditType.JAVA__IMPLEMENTATION_CHANGE;
            case BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE:
                return EditType.JAVA__API_CHANGE;
            case BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                return EditType.JAVA__IMPLEMENTATION_CHANGE;
            case BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE:
                return EditType.JAVA__API_CHANGE;
            case BUILD_INC__MAIN_PROJECT__RES__EDIT:
                return EditType.RES__EDIT;
            case BUILD_INC__MAIN_PROJECT__RES__ADD:
                return EditType.RES__ADD;
            case BUILD_INC__SUB_PROJECT__RES__EDIT:
                return EditType.RES__EDIT;
            case BUILD_INC__SUB_PROJECT__RES__ADD:
                return EditType.RES__ADD;
            case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                return EditType.JAVA__IMPLEMENTATION_CHANGE;
            case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE:
                return EditType.JAVA__API_CHANGE;
            case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                return EditType.JAVA__IMPLEMENTATION_CHANGE;
            case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__API_CHANGE:
                return EditType.JAVA__API_CHANGE;
            case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__EDIT:
                return EditType.RES__EDIT;
            case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__ADD:
                return EditType.RES__ADD;
            case INSTANT_RUN_BUILD__SUB_PROJECT__RES__EDIT:
                return EditType.RES__EDIT;
            case INSTANT_RUN_BUILD__SUB_PROJECT__RES__ADD:
                return EditType.RES__ADD;
            default:
                throw new IllegalStateException(
                        "Should not be an edit for benchmarkmode " + benchmarkMode);
        }
    }

    /** Returns whether the change should be made in the main 'app' project or a subproject. */
    @NonNull
    static SubProjectType getSubProjectType(@NonNull Logging.BenchmarkMode benchmarkMode) {
        switch (benchmarkMode) {
            case BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
            case BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE:
                return SubProjectType.MAIN_PROJECT;
            case BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
            case BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE:
                return SubProjectType.SUB_PROJECT;
            case BUILD_INC__MAIN_PROJECT__RES__EDIT:
            case BUILD_INC__MAIN_PROJECT__RES__ADD:
                return SubProjectType.MAIN_PROJECT;
            case BUILD_INC__SUB_PROJECT__RES__EDIT:
            case BUILD_INC__SUB_PROJECT__RES__ADD:
                return SubProjectType.SUB_PROJECT;
            case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
            case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE:
                return SubProjectType.MAIN_PROJECT;
            case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
            case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__API_CHANGE:
                return SubProjectType.SUB_PROJECT;
            case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__EDIT:
            case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__ADD:
                return SubProjectType.MAIN_PROJECT;
            case INSTANT_RUN_BUILD__SUB_PROJECT__RES__EDIT:
            case INSTANT_RUN_BUILD__SUB_PROJECT__RES__ADD:
                return SubProjectType.SUB_PROJECT;
            default:
                throw new IllegalStateException("No sub project type defined for " + benchmarkMode);
        }
    }

    enum SubProjectType {
        MAIN_PROJECT,
        SUB_PROJECT,
    }

    enum EditType {
        /** A hot swappable java change. */
        JAVA__IMPLEMENTATION_CHANGE,
        /**
         * A structural java change that is not hot swappable and needs downstream modules to be
         * recompiled.
         */
        JAVA__API_CHANGE,
        /** Changing a resource. */
        RES__EDIT,
        /** Adding a resource. */
        RES__ADD,
    }

    /**
     * Gets a subset of the given list based on a total number of shards and a given shard number.
     *
     * <p>This is used to distribute tests over multiple machines.
     */
    @NonNull
    public static <T> List<T> shard(@NonNull List<T> list, int shardNum, int shardTotal) {
        Preconditions.checkNotNull(list);
        Preconditions.checkArgument(
                shardNum < shardTotal, "shard number must be less than the total number of shards");

        List<T> shard = new ArrayList<>(list.size() / shardTotal + 1);
        for (int i = shardNum; i < list.size(); i += shardTotal) {
            shard.add(list.get(i));
        }
        return shard;
    }
}
