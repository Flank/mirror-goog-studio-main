/*
 * Copyright (C) 2018 The Android Open Source Project
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
import java.nio.file.Path;

public class Buildifier {

    private Buildifier() {}

    /**
     * Runs buildifier (formatter for bazel BUILD files) on the given file.
     *
     * <p>(currently only on linux)
     */
    public static void runBuildifier(Path file) throws IOException {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            return;
        }
        try {
            Path workspace = WorkspaceUtils.findWorkspace();
            Path buildifier = workspace.resolve("prebuilts/tools/linux-x86/bazel/buildifier");
            Process process = new ProcessBuilder(buildifier.toString(), file.toString()).start();
            process.waitFor();
        } catch (InterruptedException | IOException e) {
            throw new IOException("Failed to run buildifier", e);
        }
    }
}
