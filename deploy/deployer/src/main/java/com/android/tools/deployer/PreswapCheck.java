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
package com.android.tools.deployer;

import java.util.HashMap;
import java.util.Map;

/**
 * Do some verification before sending off to the VM.
 *
 * <p>TODO: Return some sort of error object instead of a String.
 */
class PreswapCheck {

    /** @return An error message should we abort swap request, otherwise, null; */
    public static String verify(HashMap<String, ApkDiffer.ApkEntryStatus> diffs) {
        for (Map.Entry<String, ApkDiffer.ApkEntryStatus> diff : diffs.entrySet()) {
            String name = diff.getKey();
            ApkDiffer.ApkEntryStatus status = diff.getValue();

            if (status.equals(ApkDiffer.ApkEntryStatus.MODIFIED)) {
                String err = verifyModified(name);
                if (err != null) {
                    return err;
                }
            }
        }
        return null;
    }

    private static String verifyModified(String name) {
        if (name.endsWith(".so")) {
            return "Modifying .so requires application restart";
        }
        return null;
    }
}
