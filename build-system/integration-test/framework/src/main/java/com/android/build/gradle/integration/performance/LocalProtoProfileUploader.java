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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalProtoProfileUploader implements ProfileUploader {
    @Nullable private final Path dir;

    @VisibleForTesting
    @NonNull
    public static LocalProtoProfileUploader toDir(@Nullable Path dir) {
        return new LocalProtoProfileUploader(dir);
    }

    @NonNull
    public static LocalProtoProfileUploader fromEnvironment() {
        return new LocalProtoProfileUploader(System.getenv("LOCAL_PROTO_BENCHMARK_LOCATION"));
    }

    private LocalProtoProfileUploader(@Nullable String dir) {
        this(dir == null ? null : Paths.get(dir));
    }

    private LocalProtoProfileUploader(@Nullable Path dir) {
        this.dir = dir;
    }

    @Override
    public void uploadData(@NonNull List<GradleBenchmarkResult> results) throws IOException {
        if (dir == null) {
            // Do nothing if the user hasn't set a directory to write these protos to.
            return;
        }

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (Files.exists(dir) && !Files.isDirectory(dir)) {
            Files.delete(dir);
            Files.createDirectories(dir);
        }

        for (GradleBenchmarkResult result : results) {
            Files.write(dir.resolve(ProfileUtils.filename(result)), result.toByteArray());
        }
    }
}
