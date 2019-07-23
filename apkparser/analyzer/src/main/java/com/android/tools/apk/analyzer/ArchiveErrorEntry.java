/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import java.nio.file.Path;

public class ArchiveErrorEntry extends ArchiveEntry {
    @NonNull private final Throwable myError;

    public ArchiveErrorEntry(
            @NonNull Archive archive,
            @NonNull Path path,
            @NonNull String pathPrefix,
            @NonNull Throwable error) {
        super(archive, path, pathPrefix);
        myError = error;
    }

    @Override
    @NonNull
    public String getNodeDisplayString() {
        String errorMessage = myError.toString();
        return errorMessage == null ? "ERROR" : errorMessage;
    }

    @Override
    @NonNull
    public String getSummaryDisplayString() {
        return getPathPrefix()
                + getPath().toString()
                + "/ - Error processing entry: "
                + getNodeDisplayString();
    }
}
