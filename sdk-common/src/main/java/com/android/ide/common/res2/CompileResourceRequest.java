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

package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import java.io.File;

/** A request for the {@link ResourceCompiler}. */
public class CompileResourceRequest {
    private final File in;
    private final File out;
    private final String folderName;
    private final boolean pseudoLocalize;
    private final boolean pngCrunching;

    public CompileResourceRequest(@NonNull File in, @NonNull File out, @NonNull String folderName) {
        this(in, out, folderName, false, true);
    }

    public CompileResourceRequest(
            @NonNull File in,
            @NonNull File out,
            @NonNull String folderName,
            boolean pseudoLocalize,
            boolean pngCrunching) {
        this.in = in;
        this.out = out;
        this.folderName = folderName;
        this.pseudoLocalize = pseudoLocalize;
        this.pngCrunching = pngCrunching;
    }

    public File getInput() {
        return in;
    }

    public File getOutput() {
        return out;
    }

    public String getFolderName() {
        return folderName;
    }

    public boolean isPseudoLocalize() {
        return pseudoLocalize;
    }

    public boolean isPngCrunching() {
        return pngCrunching;
    }
}
