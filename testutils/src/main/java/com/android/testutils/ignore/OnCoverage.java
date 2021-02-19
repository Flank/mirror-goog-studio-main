/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.testutils.ignore;

/**
 * A condition that is present when executing from
 * //tools/base/bazel/studio_coverage.sh
 */
public class OnCoverage implements IgnoreCondition {

    @Override
    public boolean present() {
        // This environment variable is expected to be set when tests are instrumented with a
        // coverage runner. Setting this variable using bazel can be done using
        // the bazel argument: --test_env=coverage_runner=1
        return System.getenv("coverage_runner") != null;
    }
}
