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

package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.internal.AndroidArtifact;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;

public class Archives {

    @NonNull
    public static Archive open(@NonNull Path archive) throws IOException {
        URI uri;
        try {
            uri = new URI("jar", archive.toUri().toString(), null);
        } catch (URISyntaxException e) {
            String msg =
                    "Unexpected error while constructing the path to the artifact's file system";
            throw new IllegalStateException(msg, e);
        }

        FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
        return new AndroidArtifact(archive, fileSystem);
    }
}
