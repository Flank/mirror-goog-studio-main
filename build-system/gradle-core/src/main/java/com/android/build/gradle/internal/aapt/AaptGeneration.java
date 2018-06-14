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

package com.android.build.gradle.internal.aapt;

import com.android.annotations.NonNull;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;

public enum AaptGeneration {
    AAPT_V2_DAEMON_MODE,
    AAPT_V2_DAEMON_SHARED_POOL,
    ;

    public static AaptGeneration fromProjectOptions(@NonNull ProjectOptions projectOptions) {
        if (projectOptions.get(BooleanOption.ENABLE_AAPT2_WORKER_ACTIONS)
                || projectOptions.get(BooleanOption.USE_AAPT2_FROM_MAVEN)) {
            return AAPT_V2_DAEMON_SHARED_POOL;
        } else {
            return AAPT_V2_DAEMON_MODE;
        }
    }
}
