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

import com.android.tools.deployer.model.FileDiff;
import java.util.ArrayList;
import java.util.List;

/** A verifier that determines whether or not a swap operation can be performed. */
class SwapVerifier {

    public static final String STATIC_LIB_MODIFIED_ERROR =
            "Modifying .so requires application restart.";
    public static final String MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED =
            "Modifying AndroidManifest.xml files not supported.";
    public static final String RESOURCE_MODIFICATION_NOT_ALLOWED =
            "Resource modification not allowed.";

    public List<FileDiff> verify(List<FileDiff> diffs, boolean allChanges)
            throws DeployerException {
        List<FileDiff> dexes = new ArrayList<>();
        // TODO: Support multiple errors
        for (FileDiff diff : diffs) {
            if (diff.status.equals(FileDiff.Status.MODIFIED)) {
                String name = diff.oldFile.name;
                if (name.endsWith(".so")) {
                    throw new DeployerException(
                            DeployerException.Error.CANNOT_SWAP_STATIC_LIB,
                            STATIC_LIB_MODIFIED_ERROR);
                }
                if (name.equals("AndroidManifest.xml")) {
                    throw new DeployerException(
                            DeployerException.Error.CANNOT_SWAP_MANIFEST,
                            MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED);
                }
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                if (name.endsWith(".dex")) {
                    dexes.add(diff);
                    continue;
                }
                if (!allChanges) {
                    throw new DeployerException(
                            DeployerException.Error.CANNOT_SWAP_RESOURCE,
                            RESOURCE_MODIFICATION_NOT_ALLOWED);
                }
            }
        }
        return dexes;
    }
}
