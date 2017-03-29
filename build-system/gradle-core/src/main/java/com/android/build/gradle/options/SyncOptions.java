/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.builder.core.ErrorReporter;

public final class SyncOptions {

    private SyncOptions() {}

    public static ErrorReporter.EvaluationMode getModelQueryMode(@NonNull ProjectOptions options) {
        if (options.get(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED)) {
            return ErrorReporter.EvaluationMode.IDE;
        }

        if (options.get(BooleanOption.IDE_BUILD_MODEL_ONLY)) {
            return ErrorReporter.EvaluationMode.IDE_LEGACY;
        }

        return ErrorReporter.EvaluationMode.STANDARD;
    }

    public static ExtraModelInfo.ErrorFormatMode getErrorFormatMode(
            @NonNull ProjectOptions options) {
        if (options.get(BooleanOption.IDE_INVOKED_FROM_IDE)) {
            return ExtraModelInfo.ErrorFormatMode.MACHINE_PARSABLE;
        } else {
            return ExtraModelInfo.ErrorFormatMode.HUMAN_READABLE;
        }
    }
}
