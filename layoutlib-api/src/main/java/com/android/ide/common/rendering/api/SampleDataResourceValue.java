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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.regex.Pattern;

/**
 * A {@link ResourceValue} used to contain the value of a Sample Data source. This class avoids
 * having to split potentially large files multiple times since we store it as a list of lines.
 */
public abstract class SampleDataResourceValue extends ResourceValueImpl {
    /**
     * This splitter is used to split back the content into lines. The content is generated always
     * with \n as new line separator.
     */
    private static final Splitter NEW_LINE_SPLITTER =
            Splitter.on(Pattern.compile("\r?\n")).omitEmptyStrings();

    private final ImmutableList<String> myLines;
    private final ResourceReference myReference;

    public SampleDataResourceValue(@NonNull ResourceReference reference, @Nullable byte[] content) {
        super(reference, null);

        myReference = reference;
        myLines =
                content != null
                        ? ImmutableList.copyOf(
                                NEW_LINE_SPLITTER.splitToList(new String(content, Charsets.UTF_8)))
                        : ImmutableList.of();
    }

    @Nullable
    @Override
    public String getValue() {
        // SampleDataResourceValue are just references. Values are returned via getValueAsLines
        return myReference.getResourceUrl().toString();
    }

    @NonNull
    public ImmutableList<String> getValueAsLines() {
        return myLines;
    }

    @Nullable
    @Override
    public ResourceReference getReference() {
        return myReference;
    }
}
