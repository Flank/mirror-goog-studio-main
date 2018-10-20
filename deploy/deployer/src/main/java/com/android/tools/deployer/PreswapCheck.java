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

import com.android.annotations.NonNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Do some verification before sending off to the VM.
 *
 * <p>TODO: Return some sort of error object instead of a String.
 */
public class PreswapCheck {
    public static final String STATIC_LIB_MODIFIED_ERROR =
            "Modifying .so requires application restart.";
    public static final String MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED =
            "Modifying AndroidManifest.xml files not supported.";
    public static final String RESOURCE_MODIFICATION_NOT_ALLOWED =
            "Resource modification not allowed.";

    /**
     * @return A non-empty set of error messages should we abort swap/apply request, otherwise an
     *     empty set;
     */
    @NonNull
    public static Set<String> verify(
            HashMap<String, ApkDiffer.ApkEntryStatus> diffs, boolean allowResourceModification) {
        Set<String> errorSet = new HashSet<>();
        for (Map.Entry<String, ApkDiffer.ApkEntryStatus> diff : diffs.entrySet()) {
            String name = diff.getKey();
            ApkDiffer.ApkEntryStatus status = diff.getValue();

            String err = verifyStatus(name, status, allowResourceModification);
            if (err != null) {
                errorSet.add(err);
            }
        }
        return errorSet;
    }

    private static String verifyStatus(
            @NonNull String fileName,
            @NonNull ApkDiffer.ApkEntryStatus status,
            boolean allowResourceModification) {
        if (fileName.endsWith(".so")) {
            return status == ApkDiffer.ApkEntryStatus.MODIFIED ? STATIC_LIB_MODIFIED_ERROR : null;
        } else if (fileName.equals("AndroidManifest.xml")) {
            return MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED;
        } else if (fileName.startsWith("META-INF/") || fileName.endsWith(".dex")) {
            return null;
        } else {
            return allowResourceModification ? null : RESOURCE_MODIFICATION_NOT_ALLOWED;
        }
    }
}
