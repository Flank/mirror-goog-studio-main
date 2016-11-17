/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.apkzlib.utils;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Utility functions for tests.
 */
public final class ApkZFileTestUtils {

    /**
     * Reads a portion of a file to memory.
     *
     * @param file the file to read data from
     * @param start the offset in the file to start reading
     * @param length the number of bytes to read
     * @return the bytes read
     * @throws Exception failed to read the file
     */
    @NonNull
    public static byte[] readSegment(@NonNull File file, long start, int length) throws Exception {
        Preconditions.checkArgument(start >= 0, "start < 0");
        Preconditions.checkArgument(length >= 0, "length < 0");

        byte data[];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);

            data = new byte[length];
            int tot = 0;
            while (tot < length) {
                int r = raf.read(data, tot, length - tot);
                if (r < 0) {
                    throw new EOFException();
                }

                tot += r;
            }
        }

        return data;
    }

    /**
     * Obtains the test resource with the given path.
     *
     * @param path the path
     * @return the test resource
     */
    @NonNull
    public static File getResource(@NonNull String path) {
        File resource = TestResources.getFile(ApkZFileTestUtils.class, path);
        assertTrue(resource.exists());
        return resource;
    }

    /**
     * Obtains the test resource with the given path.
     *
     * @param path the path
     * @return the test resource
     */
    @NonNull
    public static ByteSource getResourceBytes(@NonNull String path) {
        return Resources.asByteSource(Resources.getResource(ApkZFileTestUtils.class, path));
    }

    /**
     * Sleeps the current thread for enough time to ensure that the local file system had enough
     * time to notice a "tick". This method is usually called in tests when it is necessary to
     * ensure filesystem writes are detected through timestamp modification.
     *
     * @param currentTimestamp last timestamp read from disk
     * @throws InterruptedException waiting interrupted
     * @throws IOException issues creating a temporary file
     */
    public static void waitForFileSystemTick(long currentTimestamp)
            throws InterruptedException, IOException {
        while (getFreshTimestamp() <= currentTimestamp) {
            Thread.sleep(100);
        }
    }

    /**
     * Obtains the timestamp of a newly-created file.
     *
     * @return the timestamp
     * @throws IOException the I/O Exception
     */
    private static long getFreshTimestamp() throws IOException {
        File notUsed = File.createTempFile(ApkZFileTestUtils.class.getName(), "waitForFSTick");
        long freshTimestamp = notUsed.lastModified();
        assertTrue(notUsed.delete());
        return freshTimestamp;
    }
}
