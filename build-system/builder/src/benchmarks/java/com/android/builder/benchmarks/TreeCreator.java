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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class TreeCreator {

    public static void createTree(int numFolders, int numFiles, int fileSize, Path path)
            throws IOException {
        Random r = new Random(1);
        for (int i = 0; i < numFolders; i++) {
            Path dstFolder = Files.createTempDirectory(path, Integer.toString(i));
            Files.createDirectories(dstFolder);
            for (int j = 0; j < numFiles; j++) {
                Path dstFile = Paths.get(dstFolder.toString() + "-" + j);
                FileOutputStream o = new FileOutputStream(dstFile.toFile());
                byte[] bytes = new byte[fileSize];
                r.nextBytes(bytes);
                o.write(bytes);
                o.close();
            }
        }
    }
}
