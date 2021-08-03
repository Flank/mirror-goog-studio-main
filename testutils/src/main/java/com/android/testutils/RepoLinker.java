/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.testutils;

import com.android.utils.PathUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Creates a Maven repository using symlinks. */
public class RepoLinker {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage RepoLinkerMain <destination_directory>");
            System.exit(1);
        }
        Path destination = Paths.get(args[0]);
        PathUtils.deleteRecursivelyIfExists(destination);
        List<String> artifacts =
                Files.readAllLines(Paths.get(System.getProperty("artifacts_manifest_file")));
        new RepoLinker().link(destination, artifacts);
    }

    /**
     * Creates a Maven repository using symlinks.
     *
     * @param destination The destination directory for the Maven repository.
     * @param artifacts The list of artifacts to symlink. The artifacts are given as a list of
     *     path_in_repo=path_to_file
     */
    public void link(Path destination, List<String> artifacts) throws Exception {
        for (String artifact : artifacts) {
            String[] split = artifact.split("=");
            if (split.length != 2) {
                throw new IllegalStateException("Invalid repository file " + artifact);
            }
            Path src = Paths.get(split[1]).toAbsolutePath();
            Path dest = destination.resolve(split[0]);

            Files.createDirectories(dest.getParent());
            if (Files.exists(dest)) {
                Files.delete(dest);
            }
            Files.createSymbolicLink(dest, src);
        }
    }
}
