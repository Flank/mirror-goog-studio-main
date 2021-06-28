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

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.manifest.parser.components.ManifestAppComponentInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** A verifier that determines whether or not a swap operation can be performed. */
class SwapVerifier {

    public List<FileDiff> verify(List<Apk> apks, List<FileDiff> diffs, boolean allChanges)
            throws DeployerException {
        for (Apk apk : apks) {
            List<String> isolatedProcessServicesNames = apk.services.stream()
                    .filter(s -> s.isolatedProcess)
                    .map(ManifestAppComponentInfo::getQualifiedName)
                    .collect(Collectors.toList());
            if (!isolatedProcessServicesNames.isEmpty()) {
                throw DeployerException.isolatedServiceNotSupported(isolatedProcessServicesNames);
            }
        }
        return verify(diffs, allChanges);
    }

    public List<FileDiff> verify(List<FileDiff> diffs, boolean allChanges)
            throws DeployerException {
        List<FileDiff> dexes = new ArrayList<>();
        // TODO: Support multiple errors
        for (FileDiff diff : diffs) {
            if (diff.status.equals(FileDiff.Status.MODIFIED)) {
                String name = diff.oldFile.getName();
                if (name.endsWith(".so")) {
                    throw DeployerException.changedSharedObject(name);
                }
                if (name.equals("AndroidManifest.xml")) {
                    throw DeployerException.changedManifest(name);
                }
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                if (name.endsWith(".dex")) {
                    dexes.add(diff);
                    continue;
                }
                if (!allChanges) {
                    if (name.endsWith("crashlytics-build.properties")) {
                        throw DeployerException.changedCrashlyticsBuildId(name);
                    } else {
                        throw DeployerException.changedResources(name);
                    }
                }
            }
        }
        return dexes;
    }
}
