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

import com.android.builder.packaging.JarFlinger;
import com.android.builder.packaging.JarMerger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BenchmarkJarMerge {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private void BenchMarkWith(int numFiles, int fileSize) throws IOException {
        System.out.println(
                String.format(
                        "With 2 jars of %s files each. Each file is %s bytes. "
                                + "Total data per jar = %s bytes",
                        NumberFormat.getInstance().format(numFiles),
                        NumberFormat.getInstance().format(fileSize),
                        NumberFormat.getInstance().format(numFiles * fileSize)));
        Path tmpFolder = temporaryFolder.newFolder().toPath();

        File zipInput1 = new File(tmpFolder + "/zip1.jar");
        ZipCreator.createZip(numFiles, fileSize, zipInput1.getPath());
        File zipInput2 = new File(tmpFolder + "/zip2.jar");
        ZipCreator.createZip(numFiles, fileSize, zipInput2.getPath());
        File dst = new File(tmpFolder + "/result.jar");

        StopWatch w;

        long[] times = new long[Utils.BENCHMARK_SAMPLE_SIZE];
        for (int i = 0; i < times.length; i++) {
            w = new StopWatch();
            JarMerger jarMerger = new JarMerger(dst.toPath());
            jarMerger.addJar(zipInput1.toPath());
            jarMerger.addJar(zipInput2.toPath());
            jarMerger.close();
            times[i] = w.end() / 1_000_000;
            Files.delete(dst.toPath());
        }

        System.out.println(String.format("Jarmerger : %6d ms.", Utils.median(times)));

        for (int i = 0; i < times.length; i++) {
            w = new StopWatch();
            JarFlinger jarFlinger = new JarFlinger(dst.toPath());
            jarFlinger.addJar(zipInput1.toPath());
            jarFlinger.addJar(zipInput2.toPath());
            jarFlinger.close();
            times[i] = w.end() / 1_000_000;
            Files.delete(dst.toPath());
        }

        System.out.println(String.format("Zipflinger: %6d ms.", Utils.median(times)));
    }

    @Test
    public void run() throws IOException {
        System.out.println();
        System.out.println("Jar Merging speed:");
        System.out.println("--------------");
        BenchMarkWith(1000, 1 << 10); // 1000 files of   1 KiB
        BenchMarkWith(1000, 1 << 11); // 1000 files of   2 KiB
        BenchMarkWith(1000, 1 << 12); // 1000 files of   4 KiB
        BenchMarkWith(1000, 1 << 13); // 1000 files of   8 KiB
        BenchMarkWith(1000, 1 << 14); // 1000 files of  16 KiB
        BenchMarkWith(1000, 1 << 15); // 1000 files of  32 KiB
        BenchMarkWith(1000, 1 << 16); // 1000 files of  64 KiB
        BenchMarkWith(1000, 1 << 17); // 1000 files of 128 KiB

    }
}
