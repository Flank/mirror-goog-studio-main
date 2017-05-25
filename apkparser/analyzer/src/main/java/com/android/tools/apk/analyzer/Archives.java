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
import com.android.tools.apk.analyzer.internal.ZipArtifact;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;

public class Archives {

    @NonNull
    public static Archive open(@NonNull Path archive) throws IOException {
        URI uri = URI.create("jar:" + archive.toUri().toString());
        FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
        //we assume this is an AIA bundle, which we give special handling
        if (archive.getFileName().toString().toLowerCase().endsWith(".zip")) {
            return new ZipArtifact(archive, fileSystem);
        } else {
            return new AndroidArtifact(archive, fileSystem);
        }
    }

    @NonNull
    static Archive openInnerZip(@NonNull Path archive) throws IOException {
        URI uri = URI.create("jar:" + archive.toUri().toString());
        FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
        return new AndroidArtifact(archive, fileSystem);
    }
}
