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
package com.android.ide.common.resources.sampledata;

import com.android.annotations.NonNull;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SampleDataManager {
    /** Cache of sample data content keyed by resource name */
    private static final Cache<String, SampleDataHolder> sSampleDataCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(2, TimeUnit.MINUTES)
                    .softValues()
                    .weigher((String key, SampleDataHolder value) -> value.getFileSizeMb())
                    .maximumWeight(50) // MB
                    .build();

    /**
     * Holds the current cursor position for the sample data file so a consistent view is provided
     * for a given resolver (i.e. entries are not repeated and they are different for each element).
     */
    private final Map<String, AtomicInteger> mSampleDataPosition = new HashMap<>();

    /**
     * Returns a line of sample data content from the given resourceName and fileName or null if the
     * line couldn't be retrieved. This method also handles the caching and the current position of
     * the cursor for the sample data file.
     */
    public String getSampleDataLine(@NonNull String resourceName, @NonNull String fileName) {
        AtomicInteger cursorPosition = mSampleDataPosition.get(resourceName);
        if (cursorPosition == null) {
            cursorPosition = new AtomicInteger(0);
            mSampleDataPosition.put(resourceName, cursorPosition);
        }

        File sampleFile = new File(fileName);
        try {
            SampleDataHolder value = sSampleDataCache.getIfPresent(resourceName);
            if (value == null
                    || value.getLastModification() == 0
                    || value.getLastModification() != sampleFile.lastModified()) {
                List<String> splitResourceName =
                        Splitter.on('/').limit(2).splitToList(resourceName);
                String path = splitResourceName.size() > 1 ? splitResourceName.get(1) : "";
                value = SampleDataHolder.getFromFile(sampleFile, path);
                sSampleDataCache.put(fileName, value);
            }

            int lineCount = value.getContents().size();
            return lineCount > 0
                    ? value.getContents().get(cursorPosition.getAndIncrement() % lineCount)
                    : null;
        } catch (IOException ignore) {
        }
        return null;
    }
}
