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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/** Holder for the sample data cache */
public class SampleDataHolder {
    private static final SampleDataHolder EMPTY_SAMPLE_DATA_HOLDER =
            new SampleDataHolder("", 0, 0, ImmutableList.of());

    private final String myName;
    private final long myLastModification;
    private final List<String> myContents;
    private final int myFileSizeMb;

    private SampleDataHolder(
            @NonNull String name,
            long lastModification,
            int contentSizeMb,
            @NonNull List<String> contents) {
        myName = name;
        myLastModification = lastModification;
        myFileSizeMb = contentSizeMb;
        myContents = contents;
    }

    public String getName() {
        return myName;
    }

    public long getLastModification() {
        return myLastModification;
    }

    public List<String> getContents() {
        return myContents;
    }

    public int getFileSizeMb() {
        return myFileSizeMb;
    }

    @NonNull
    public static SampleDataHolder getFromFile(@NonNull File input, @NonNull String contentPath)
            throws IOException {
        if (!input.isFile()) {
            throw new IOException("Sample data needs to be contained in a file");
        }

        String fileName = input.getName();
        String extension = "." + Files.getFileExtension(fileName);

        switch (extension) {
            case SdkConstants.DOT_JSON:
                return getFromJsonFile(input, contentPath);
            case ".":
                return new SampleDataHolder(
                        fileName,
                        input.lastModified(),
                        (int) (input.length() / 1_000_000),
                        Files.readLines(input, Charsets.UTF_8));
        }

        throw new IOException("Unsupported format type " + extension);
    }

    static SampleDataHolder getFromJsonFile(@NonNull File input, @NonNull String contentPath)
            throws IOException {
        if (contentPath.isEmpty()) {
            return EMPTY_SAMPLE_DATA_HOLDER;
        }

        SampleDataJsonParser parser = SampleDataJsonParser.parse(new FileReader(input));
        if (parser == null) {
            // Failed to parse the JSON file
            return EMPTY_SAMPLE_DATA_HOLDER;
        }

        List<String> content = parser.getContentFromPath(contentPath);

        if (content.isEmpty()) {
            return EMPTY_SAMPLE_DATA_HOLDER;
        }

        // TODO: Calculate size of the content
        return new SampleDataHolder(
                input.getName() + contentPath,
                input.lastModified(),
                (int) (input.length() / 1_000_000),
                content);
    }
}
