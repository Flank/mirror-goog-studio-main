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

import android.databinding.tool.util.Preconditions;
import com.android.annotations.NonNull;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;

public enum AaptGeneration {
    AAPT_V1,
    AAPT_V2_JNI,
    AAPT_V2_DAEMON_MODE;

    public static AaptGeneration fromProjectOptions(@NonNull ProjectOptions projectOptions) {
        if (projectOptions.get(BooleanOption.ENABLE_AAPT2)) {
            Preconditions.check(
                    !(projectOptions.get(BooleanOption.ENABLE_IN_PROCESS_AAPT2)
                            && projectOptions.get(BooleanOption.ENABLE_DAEMON_MODE_AAPT2)),
                    "Both JNI and Daemon mode versions of AAPT2 cannot be enabled at the same time."
                            + " Please disable one of them (e.g. android.enableAapt2jni=false)");
            Preconditions.check(
                    projectOptions.get(BooleanOption.ENABLE_IN_PROCESS_AAPT2)
                            || projectOptions.get(BooleanOption.ENABLE_DAEMON_MODE_AAPT2),
                    "To use AAPT2 either JNI or Daemon mode versions need to be enabled. "
                            + "Please enable one (e.g. android.enableAapt2DaemonMode=true)");

            if (projectOptions.get(BooleanOption.ENABLE_IN_PROCESS_AAPT2)) {
                return AAPT_V2_JNI;
            } else {
                return AAPT_V2_DAEMON_MODE;
            }
        } else {
            return AAPT_V1;
        }
    }
}
