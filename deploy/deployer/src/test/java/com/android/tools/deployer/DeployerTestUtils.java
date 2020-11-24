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
import com.android.utils.PathUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Assert;

public class DeployerTestUtils {
    public static File prepareInstaller() throws IOException {
        File root = TestUtils.getWorkspaceRoot().toFile();
        String testInstaller = "tools/base/deploy/installer/android-installer/test-installer";
        File installer = new File(root, testInstaller);
        if (!installer.exists()) {
            // Running from IJ
            File devRoot = new File(root, "bazel-bin/");
            installer = new File(devRoot, testInstaller);
        }
        File installers = Files.createTempDirectory("installers").toFile();
        File x86 = new File(installers, "x86");
        Assert.assertTrue(x86.mkdirs());
        FileUtils.copyFile(installer, new File(x86, "installer"));
        return installers;
    }

    public static void prepareStudioInstaller() throws IOException {
        File root = TestUtils.getWorkspaceRoot().toFile();
        String base = "tools/base/deploy/installer/android-installer";
        String testInstaller = "test-installer";
        File baseLocation = new File(root, base);
        File installer = new File(baseLocation, testInstaller);
        if (!installer.exists()) {
            // Running from IJ
            File devRoot = new File(root, "bazel-bin/");
            baseLocation = new File(devRoot, base);
            installer = new File(baseLocation, testInstaller);
        }
        File x86 = new File(root, "tools/idea/plugins/android/resources/installer/x86");
        if (!x86.exists()) {
            Assert.assertTrue(x86.mkdirs());
        }
        FileUtils.copyFile(installer, new File(x86, "installer"));
    }

    public static void removeStudioInstaller() throws IOException {
        PathUtils.deleteRecursivelyIfExists(
                TestUtils.getWorkspaceRoot().resolve("tools/idea/plugins/android"));
    }
}
