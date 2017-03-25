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

package com.android.tools.apk.analyzer.internal;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.Archive;
import com.google.common.primitives.Shorts;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

public class AndroidArtifact implements Archive {
    private final Path artifact;
    private final FileSystem contents;

    public AndroidArtifact(@NonNull Path artifact, @NonNull FileSystem contents) {
        this.artifact = artifact;
        this.contents = contents;
    }

    @NonNull
    @Override
    public Path getPath() {
        return artifact;
    }

    @Override
    @NonNull
    public Path getContentRoot() {
        return contents.getPath("/");
    }

    @Override
    public void close() throws IOException {
        contents.close();
    }

    @Override
    public boolean isBinaryXml(@NonNull Path p, @NonNull byte[] content) {
        if (!p.toString().endsWith(SdkConstants.DOT_XML)) {
            return false;
        }

        Path name = p.getFileName();
        if (name == null) {
            return false;
        }

        boolean manifest = p.equals(contents.getPath("/", SdkConstants.FN_ANDROID_MANIFEST_XML));
        boolean insideResFolder = p.startsWith(contents.getPath("/", SdkConstants.FD_RES));
        boolean insideResRaw =
                p.startsWith(contents.getPath("/", SdkConstants.FD_RES, SdkConstants.FD_RES_RAW));
        boolean xmlResource = insideResFolder && !insideResRaw;
        if (!manifest && !xmlResource) {
            return false;
        }

        short code = Shorts.fromBytes(content[1], content[0]);
        return code == 0x0003; // Chunk.Type.XML
    }
}
