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

package com.android.tools.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility functions for dealing with Bazel workspaces. */
public class WorkspaceUtils {

    /**
     * Bazel puts a file with this name inside the execroot directory, when using {@code bazel run}.
     * It contains a single line with the absolute path of the workspace.
     */
    public static final String DO_NOT_BUILD_HERE = "DO_NOT_BUILD_HERE";

    private WorkspaceUtils() {}

    /**
     * Finds the workspace that the invoking binary was build from. Only works if the binary was
     * invoked through {@code bazel run}.
     */
    public static Path findWorkspace() throws IOException {
        Path workingDirectory = Paths.get("").toAbsolutePath();
        if (!workingDirectory.getFileName().toString().equals("__main__")
                || !workingDirectory.getParent().getFileName().toString().endsWith("runfiles")) {
            throw new IllegalStateException(
                    "Can't find workspace root, the binary was not invoked through 'bazel run'.");
        }

        Path parentDir = workingDirectory.getParent();

        while (parentDir != null) {
            Path doNotBuildHere = parentDir.resolve(DO_NOT_BUILD_HERE);
            if (Files.exists(doNotBuildHere)) {
                return Paths.get(
                        new String(
                                Files.readAllBytes(doNotBuildHere), StandardCharsets.ISO_8859_1));
            }

            parentDir = parentDir.getParent();
        }

        throw new RuntimeException(
                "Can't find the " + DO_NOT_BUILD_HERE + " file in any of the parent directories.");
    }

    /** Finds the absolute path to the prebuilts repository. */
    public static Path findPrebuiltsRepository() throws IOException {
        Path workspace = findWorkspace();
        return workspace.resolve("prebuilts/tools/common/m2/repository");
    }
}
