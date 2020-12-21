/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector;

import androidx.annotation.NonNull;

/** A lambda location found via JVMTI */
public class LambdaLocation {
    private final String mFileName; // the source file name
    private final int mStartLine; // the start line of the lambda
    private final int mEndLine; // the end line of the lambda

    public LambdaLocation(@NonNull String fileName, int startLine, int endLine) {
        this.mFileName = fileName;
        this.mStartLine = startLine;
        this.mEndLine = endLine;
    }

    @NonNull
    public String getFileName() {
        return mFileName;
    }

    public int getStartLine() {
        return mStartLine;
    }

    public int getEndLine() {
        return mEndLine;
    }
}
