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

import com.android.tools.deployer.model.DexClass;
import com.android.tools.deployer.model.FileDiff;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** A comparator of dex files, that uses a dex splitter to obtain class information. */
public class DexComparator {

    /**
     * Compares a list of pair of files and returns the classes that were changed from the original
     * dex.
     *
     * @param dexDiffs the list of pair of dex files to compare
     * @param splitter the splitter to use to split a dex into classes
     * @return the classes that have changed.
     */
    public List<DexClass> compare(List<FileDiff> dexDiffs, DexSplitter splitter)
            throws DeployerException {
        // Iterate through the list of .dex files which have changed. We cannot trust dex filenames to be stable
        // since there have been instances where we receive:
        //
        // OLD FILENAME    CRC  |      NEW FILENAME     CRC
        // classes1.dex     A   |      classes1.dex      A
        // classes2.dex     B   |      classes2.dex      D
        // classes3.dex     C   |      classes3.dex      B
        //
        // classes2.dex and classes3.dex seem to have changed. However classes2.dex as only been renamed classes3.dex
        // and the only real changes are between the pair old classes3.dex and new classes2.dex.
        //
        // To solve this, we flatten all classes in the old apk into a single list and compare it with each dex list
        // received
        try (Trace ignored = Trace.begin("compare")) {

            // Flatten the list of old files.
            Map<String, Long> oldChecksums = new HashMap<>();
            for (FileDiff diff : dexDiffs) {
                // If the dex is new, there is no old dex to open.
                if (diff.status == FileDiff.Status.CREATED) {
                    continue;
                }
                List<DexClass> klasses = splitter.split(diff.oldFile, null);
                for (DexClass clz : klasses) {
                    oldChecksums.put(clz.name, clz.checksum);
                }
            }

            List<DexClass> toSwap = new ArrayList<>();
            for (FileDiff diff : dexDiffs) {
                // Memory optimization to discard not needed code
                Predicate<DexClass> keepCode =
                        (DexClass clz) -> {
                            Long oldChecksum = oldChecksums.get(clz.name);
                            return oldChecksum != null && clz.checksum != oldChecksum;
                        };

                List<DexClass> newClasses = splitter.split(diff.newFile, keepCode);
                for (DexClass klass : newClasses) {
                    if (!oldChecksums.containsKey(klass.name)) {
                        // This is a new class. This is not supported.
                        throw new DeployerException(
                                DeployerException.Error.CANNOT_SWAP_NEW_CLASS,
                                "Unable to swap new class '" + klass.name + "'");
                    }
                }

                toSwap.addAll(
                        newClasses
                                .stream()
                                .filter(c -> c.code != null)
                                .collect(Collectors.toList()));
            }
            return toSwap;
        }
    }
}
