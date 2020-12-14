/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.builder.benchmarks;

import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BenchmarkList {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void run() throws IOException {
        runTest(2000, 1000);
        runTest(4000, 1000);
        runTest(10000, 1000);
        runTest(20000, 1000);
        runTest(30000, 1000);
        runTest(40000, 1000);
        runTest(50000, 1000);
        runTest(60000, 1000);
    }

    private void runTest(int numEntries, int entrySize) throws IOException {
        String tmpFolder = temporaryFolder.newFolder().getAbsolutePath();
        Path zipPath = Paths.get(tmpFolder, "app" + numEntries + ".apk");
        ZipCreator.createZip(numEntries, entrySize, zipPath.toAbsolutePath().toString());

        System.out.println();
        System.out.println(
                String.format(
                        "Listing speed (%dk entries, %s KiB):",
                        numEntries / 1000,
                        NumberFormat.getInstance().format(numEntries * entrySize / 1000)));
        System.out.println("---------------------------------------");
        runZipFlinger(zipPath);
        runApkzlib(zipPath);
    }

    private void runZipFlinger(Path zipPath) throws IOException {
        long[] times = new long[Utils.BENCHMARK_SAMPLE_SIZE];
        for (int i = 0; i < times.length; i++) {
            StopWatch watch = new StopWatch();
            ZipMap map = ZipMap.from(zipPath, false);
            long nameSize = 0;
            for (Entry entry : map.getEntries().values()) {
                nameSize += entry.getName().length();
            }
            long runtime = watch.end();
            times[i] = runtime;
        }

        System.out.println(String.format("Zipflinger:    %4d ms", Utils.median(times) / 1000000));
    }

    private void runApkzlib(Path zipPath) throws IOException {
        long[] times = new long[Utils.BENCHMARK_SAMPLE_SIZE];
        for (int i = 0; i < times.length; i++) {
            StopWatch watch = new StopWatch();
            ZFile zFile = new ZFile(zipPath.toFile());
            long nameSize = 0;
            for (StoredEntry entry : zFile.entries()) {
                nameSize += entry.getCentralDirectoryHeader().getName().length();
            }
            zFile.close();
            long runtime = watch.end();
            times[i] = runtime;
        }
        System.out.println(String.format("Apkzlib   :    %4d ms", Utils.median(times) / 1000000));
    }
}
