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
    public List<DexClass> compare(List<FileDiff> dexDiffs, CachedDexSplitter splitter)
            throws DeployerException {
        try (Trace ignored = Trace.begin("compare")) {
            List<DexClass> toSwap = new ArrayList<>();
            for (FileDiff diff : dexDiffs) {
                List<DexClass> oldClasses = splitter.split(diff.oldFile, false, null);
                Map<String, Long> checksums = new HashMap<>();
                for (DexClass clz : oldClasses) {
                    checksums.put(clz.name, clz.checksum);
                }
                // Memory optimization to discard not needed code
                Predicate<DexClass> needsCode =
                        (DexClass clz) -> {
                            Long oldChecksum = checksums.get(clz.name);
                            return oldChecksum != null && clz.checksum != oldChecksum;
                        };

                List<DexClass> newClasses = splitter.split(diff.newFile, true, needsCode);
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
