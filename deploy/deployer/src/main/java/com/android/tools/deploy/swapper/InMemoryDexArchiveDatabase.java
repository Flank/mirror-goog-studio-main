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
package com.android.tools.deploy.swapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Provide a very basic implmentation of an {@link DexArchiveDatabase}. */
public class InMemoryDexArchiveDatabase extends DexArchiveDatabase implements Serializable {
    private List<InMemoryDexFileEntry> dexFilesTable = new ArrayList<>();
    private Map<Long, Integer> dexFileHashToIndex = new HashMap<>();
    private Map<String, List<Integer>> archiveTable = new HashMap<>();

    @Override
    public Map<String, Long> getClassesChecksum(int dexFileIndex) {
        return dexFilesTable.get(dexFileIndex).classesChecksum;
    }

    @Override
    public List<DexFileEntry> getDexFiles(String archiveChecksum) {

        if (!archiveTable.containsKey(archiveChecksum)) {
            return null;
        }

        return archiveTable
                .get(archiveChecksum)
                .stream()
                .map(index -> dexFilesTable.get(index))
                .collect(Collectors.toList());
    }

    @Override
    public int addDexFile(long dexFileChecksum, String name) {
        int index = dexFilesTable.size();
        dexFilesTable.add(new InMemoryDexFileEntry(index, dexFileChecksum, name));
        dexFileHashToIndex.put(dexFileChecksum, index);
        return index;
    }

    @Override
    public int getDexFileIndex(long dexFileChecksum) {
        Integer index = dexFileHashToIndex.get(dexFileChecksum);
        return index == null ? -1 : index;
    }

    @Override
    public void fillEntriesChecksum(int dexFileIndex, Map<String, Long> classesChecksum) {
        dexFilesTable.get(dexFileIndex).classesChecksum.putAll(classesChecksum);
    }

    @Override
    public void fillDexFileList(String archiveChecksum, List<Integer> dexFilesIndex) {
        archiveTable.put(archiveChecksum, dexFilesIndex);
    }

    private static final class InMemoryDexFileEntry extends DexFileEntry implements Serializable {
        private final Map<String, Long> classesChecksum = new HashMap<>();

        public InMemoryDexFileEntry(int index, long checksum, String name) {
            super(index, checksum, name);
        }
    }
}
