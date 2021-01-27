/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.library;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class JarsInLibrariesConnectedTest {
    byte[] simpleJarDataA;
    byte[] simpleJarDataB;
    byte[] simpleJarDataC;
    byte[] simpleJarDataD;
    File assetsDir;
    File resRawDir;
    File resourcesDir;
    File libsDir;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("assets")
                    .create();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setUp() throws IOException, InterruptedException {
        simpleJarDataA =
                Resources.toByteArray(
                        Resources.getResource(
                                JarsInLibrariesConnectedTest.class,
                                "jars/simple-jar-with-A_DoIExist-class.jar"));
        simpleJarDataB =
                Resources.toByteArray(
                        Resources.getResource(
                                JarsInLibrariesConnectedTest.class,
                                "jars/simple-jar-with-B_DoIExist-class.jar"));
        simpleJarDataC =
                Resources.toByteArray(
                        Resources.getResource(
                                JarsInLibrariesConnectedTest.class,
                                "jars/simple-jar-with-C_DoIExist-class.jar"));
        simpleJarDataD =
                Resources.toByteArray(
                        Resources.getResource(
                                JarsInLibrariesConnectedTest.class,
                                "jars/simple-jar-with-D_DoIExist-class.jar"));

        // Make directories where we will place jars.
        assetsDir = project.file("lib/src/main/assets");
        FileUtils.mkdirs(assetsDir);
        resRawDir = project.file("lib/src/main/res/raw");
        FileUtils.mkdirs(resRawDir);
        resourcesDir = project.file("lib/src/main/resources");
        FileUtils.mkdirs(resourcesDir);
        libsDir = project.file("lib/libs");
        FileUtils.mkdirs(libsDir);

        // Add the libs dependency in the library build file.
        TestFileUtils.appendToFile(
                project.file("lib/build.gradle"),
                "\ndependencies {\napi fileTree(dir: 'libs', include: '*.jar')\n}\n"
                        .replaceAll("\n", System.getProperty("line.separator")));

        // Create some jars.
        Files.write(simpleJarDataA, new File(libsDir, "a1.jar"));
        Files.write(simpleJarDataB, new File(assetsDir, "b1.jar"));
        Files.write(simpleJarDataC, new File(resourcesDir, "c1.jar"));
        Files.write(simpleJarDataD, new File(resRawDir, "d1.jar"));

        // Run the project.
        project.execute("clean", "assembleDebug");

        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws IOException, InterruptedException {
        project.executor().run("connectedCheck");
    }
}
