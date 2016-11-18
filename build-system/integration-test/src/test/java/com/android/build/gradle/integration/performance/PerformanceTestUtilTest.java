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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

public final class PerformanceTestUtilTest {

    @Test
    public void checkGetEditType() {
        Set<PerformanceTestUtil.EditType> editTypes =
                EnumSet.allOf(PerformanceTestUtil.EditType.class);
        for (Logging.BenchmarkMode mode: PerformanceTestUtil.BENCHMARK_MODES) {
            try {
                PerformanceTestUtil.EditType editType =
                        PerformanceTestUtil.getEditType(mode);
                // This is essentially a typo check.
                assertThat(mode.name()).contains(editType.name());
                editTypes.remove(editType);
            } catch (IllegalStateException ignored) {
                // Some benchmark modes don't have edit types.
            }
        }

        assertThat(editTypes).named("Edit types without a benchmark mode").isEmpty();
    }

    @Test
    public void checkGetgetSubProjectType() {
        Set<PerformanceTestUtil.SubProjectType> subprojectTypes =
                EnumSet.allOf(PerformanceTestUtil.SubProjectType.class);
        for (Logging.BenchmarkMode mode: PerformanceTestUtil.BENCHMARK_MODES) {
            try {
                PerformanceTestUtil.SubProjectType subProjectType =
                        PerformanceTestUtil.getSubProjectType(mode);
                // This is essentially a typo check.
                assertThat(mode.name()).contains(subProjectType.name());
                subprojectTypes.remove(subProjectType);
            } catch (IllegalStateException ignored) {
                // Some benchmark modes don't have subproject types types.
            }
        }

        assertThat(subprojectTypes).named("Subproject types without a benchmark mode").isEmpty();
    }
}
