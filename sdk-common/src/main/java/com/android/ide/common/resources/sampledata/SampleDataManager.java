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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SampleDataManager {
    /**
     * Holds the current cursor position for the sample data file so a consistent view is provided
     * for a given resolver (i.e. entries are not repeated and they are different for each element).
     */
    private final Map<String, AtomicInteger> mSampleDataPosition = new HashMap<>();

    /**
     * Returns a line of sample data content from the given content and fileName. The resourceName
     * is used to track the current cursor position within that file.
     */
    public String getSampleDataLine(@NonNull String resourceName, @NonNull String content) {
        int contentSize = content.length();
        if (contentSize == 0) {
            return "";
        }

        AtomicInteger position = mSampleDataPosition.get(resourceName);
        if (position == null) {
            position = new AtomicInteger(0);
            mSampleDataPosition.put(resourceName, position);
        }

        int cursorPosition = position.get() % contentSize;
        int nextLineBreak = content.indexOf(System.lineSeparator(), cursorPosition);
        // Get content until the next line break or the end of the content
        String nextLine =
                content.substring(
                        cursorPosition, nextLineBreak != -1 ? nextLineBreak : contentSize);
        position.addAndGet(
                nextLine.length() + (nextLineBreak != -1 ? System.lineSeparator().length() : 0));

        return nextLine;
    }
}
