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

package com.android.builder.dexing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import java.nio.file.Path;

/**
 * A single .class file. Relative path matches the package directory structure, and the content is
 * the actual content of the file.
 */
public final class ClassFileEntry {
    @NonNull final Path relativePath;
    @NonNull final byte[] classFileContent;

    public ClassFileEntry(@NonNull Path relativePath, @NonNull byte[] classFileContent) {
        this.relativePath = relativePath;
        this.classFileContent = classFileContent;
    }

    /**
     * Takes the specified .class file, and changes its extension to .dex. It fails if invoked with
     * a file name that does not end in .class.
     */
    @NonNull
    public static Path withDexExtension(@NonNull Path classFilePath) {
        String fileName = classFilePath.getFileName().toString();
        Preconditions.checkState(
                fileName.endsWith(SdkConstants.DOT_CLASS),
                "Dex archives: setting .DEX extension only for .CLASS files");

        return classFilePath.resolveSibling(
                Files.getNameWithoutExtension(fileName) + SdkConstants.DOT_DEX);
    }
}
