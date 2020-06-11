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

import android.graphics.Rect;
import androidx.annotation.NonNull;
import java.util.List;

/** ComposeView represents a separate temporary tree of nodes of interest. */
public class ComposeView {
    private long mDrawId;
    private final Source mSource;
    private final Rect mBounds;
    private final List<ComposeView> mChildren;

    public ComposeView(
            long drawId,
            @NonNull Source source,
            @NonNull Rect bounds,
            @NonNull List<ComposeView> children) {
        mDrawId = drawId;
        mSource = source;
        mBounds = bounds;
        mChildren = children;
    }

    public long getDrawId() {
        return mDrawId;
    }

    public void setDrawId(long drawId) {
        mDrawId = drawId;
    }

    @NonNull
    public Source getSource() {
        return mSource;
    }

    @NonNull
    public String getMethod() {
        return mSource.getMethod();
    }

    @NonNull
    public String getFilename() {
        return mSource.getFilename();
    }

    public int getLineNumber() {
        return mSource.getLineNumber();
    }

    @NonNull
    public Rect getBounds() {
        return mBounds;
    }

    @NonNull
    public List<ComposeView> getChildren() {
        return mChildren;
    }

    /**
     * Source represents a source location as received from the Compose tooling API.
     *
     * <p>This will hold the result of parsing the method received. Here is an example:
     *
     * <p>"androidx.compose.Composer.start (Composer.kt:998)"
     */
    public static class Source {
        /** The method itself example: "androidx.compose.Composer.start" */
        private final String mMethod;

        /** The file name example: "Composer.kt" */
        private final String mFilename;

        /** The line number example: 998 */
        private final int mLineNumber;

        public Source(@NonNull String method, @NonNull String filename, int lineNumber) {
            mMethod = method;
            mFilename = filename;
            mLineNumber = lineNumber;
        }

        @NonNull
        public String getMethod() {
            return mMethod;
        }

        @NonNull
        public String getFilename() {
            return mFilename;
        }

        public int getLineNumber() {
            return mLineNumber;
        }

        public boolean isEmpty() {
            return mLineNumber == -1 && mMethod.isEmpty() && mFilename.isEmpty();
        }
    }
}
