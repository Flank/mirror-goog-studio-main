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

package com.android.tools.aapt2;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Expect;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class Aapt2JniPlatformTest {

    @Rule public Expect expect = Expect.create();

    @Rule
    public TestWatcher testWatcher =
            new TestWatcher() {
                @Override
                protected void failed(Throwable e, Description description) {
                    throw new RuntimeException(
                            "Cache keys in the Aapt2JniPlatform enum must be updated when "
                                    + "updating the AAPT2 jni prebuilts.",
                            e);
                }
            };

    @Test
    public void checkSha1SumsMatchPrebuilts() throws IOException {
        FileSystem fs = Jimfs.newFileSystem();
        for (Aapt2JniPlatform platform : Aapt2JniPlatform.values()) {
            Path dir = fs.getPath(platform.getCacheKey().toString());
            Files.createDirectory(dir);
            platform.writeToDirectory(dir);
            HashCode actual = hashDirContents(dir);
            // Cache keys must be updated when updating the AAPT2 jni prebuilts.
            expect.that(platform.getCacheKey()).named(platform.toString()).isEqualTo(actual);
        }
    }

    @NonNull
    private static HashCode hashDirContents(@NonNull Path dir) throws IOException {
        Hasher hasher = Hashing.sha256().newHasher();
        Files.list(dir)
                .sorted()
                .forEach(
                        path -> {
                            hasher.putBytes(path.getFileName().toString().getBytes(Charsets.UTF_8));
                            try {
                                hasher.putBytes(Files.readAllBytes(path));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
        return hasher.hash();
    }
}
