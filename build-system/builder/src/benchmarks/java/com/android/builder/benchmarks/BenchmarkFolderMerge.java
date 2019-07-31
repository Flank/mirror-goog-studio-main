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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BenchmarkFolderMerge {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void run() throws IOException {
        System.out.println();
        System.out.println("Folder merging speed:");
        System.out.println("-------------");

        FolderBenchMarkWith(2, 10, 1 << 21);
        FolderBenchMarkWith(3, 10, 1 << 21);
        FolderBenchMarkWith(4, 10, 1 << 21);
    }

    private void FolderBenchMarkWith(int numFolders, int numFiles, int fileSize)
            throws IOException {

        Path tmpFolder = temporaryFolder.newFolder().toPath();

        TreeCreator.createTree(numFolders, numFiles, fileSize, tmpFolder);

        Path dst = new File(Long.toString(System.currentTimeMillis())).toPath().toAbsolutePath();
        System.out.println(
                String.format(
                        "Merging %s files from %d folder (filesize=%d)",
                        numFiles * numFolders, numFolders, fileSize));
        StopWatch w;

        w = new StopWatch();
        JarMerger jm = new JarMerger(Paths.get(dst.toString()));
        jm.addDirectory(tmpFolder);
        jm.close();
        System.out.println(String.format("Jarmerger  : %6d ms.", w.end() / 1_000_000));
        Files.delete(dst);

        w = new StopWatch();
        JarFlinger jf = new JarFlinger(Paths.get(dst.toString()));
        jf.addDirectory(tmpFolder);
        jf.close();
        System.out.println(String.format("Zipflinger : %6d ms.", w.end() / 1_000_000));
    }
}
