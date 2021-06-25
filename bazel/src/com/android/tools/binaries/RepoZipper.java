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

package com.android.tools.binaries;

import com.android.tools.bazel.repolinker.RepoLinker;
import com.android.tools.utils.Zipper;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * Builds a maven repo inside a zip file.
 *
 * <p>Usage: RepoZipper output.zip repo.manifest
 *
 * <p>repo.manifest is a manifest file obtained from the output of a maven_repo.
 */
public class RepoZipper {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: RepoZipper <output> <manifest>");
            System.exit(1);
        }

        File output = new File(args[0]);
        Path manifest = Paths.get(args[1]);

        buildZip(output, manifest);
    }

    public static void buildZip(File output, Path manifest) throws Exception {
        // Use RepoLinker to resolve the repo paths for all input files.
        List<String> artifacts = Files.readAllLines(manifest);
        Map<Path, Path> resolved = new RepoLinker().resolve(artifacts);

        // Use the resolved paths to construct the output zip file.
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output))) {
            Zipper zipper = new Zipper();
            for (Map.Entry<Path, Path> entry : resolved.entrySet()) {
                File src = entry.getKey().toFile();
                Path dest = entry.getValue();

                // Replace Windows-style path separators (backslash) with forward slashes according
                // to the zip specification.
                String zipDest = dest.toString().replace('\\', '/');

                zipper.addFileToZip(src, out, zipDest, false);
            }
        }
    }
}
