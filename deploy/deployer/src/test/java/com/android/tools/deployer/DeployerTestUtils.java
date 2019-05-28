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
package com.android.tools.deployer;

import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Assert;

public class DeployerTestUtils {
    public static File prepareInstaller() throws IOException {
        File root = TestUtils.getWorkspaceRoot();
        String testInstaller = "tools/base/deploy/installer/android-installer/test-installer";
        File installer = new File(root, testInstaller);
        if (!installer.exists()) {
            // Running from IJ
            File devRoot = new File(root, "bazel-genfiles/");
            installer = new File(devRoot, testInstaller);
        }
        File installers = Files.createTempDirectory("installers").toFile();
        File x86 = new File(installers, "x86");
        Assert.assertTrue(x86.mkdirs());
        FileUtils.copyFile(installer, new File(x86, "installer"));
        return installers;
    }

    public static File getShell() {
        File root = TestUtils.getWorkspaceRoot();
        String path = "tools/base/deploy/installer/bash_bridge";
        File file = new File(root, path);
        if (!file.exists()) {
            // Running from IJ
            file = new File(root, "bazel-bin/" + path);
        }
        return file;
    }
}
