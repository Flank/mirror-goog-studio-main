/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import java.nio.file.Path;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for the cruncherEnabled settings. */
public class NoCruncherTest {

    @ClassRule
    public static GradleTestProject noPngCrunch =
            GradleTestProject.builder()
                    .withName("noPngCrunch")
                    .fromTestProject("noPngCrunch")
                    .create();

    @Test
    public void testPngFilesWereNotCrunchedByAapt() throws Exception {
        noPngCrunch.executor().withEnabledAapt2(false).run("clean", "assembleDebug");

        File srcFile = noPngCrunch.file("src/main/res/drawable/icon.png");
        File destFile =
                noPngCrunch.file(
                        "build/" + FD_INTERMEDIATES + "/res/merged/debug/drawable/icon.png");

        // assert size are unchanged.
        assertTrue(srcFile.exists());
        assertTrue(destFile.exists());
        assertEquals(srcFile.length(), destFile.length());

        // check the png files is changed.
        srcFile = noPngCrunch.file("src/main/res/drawable/lib_bg.9.png");
        destFile =
                noPngCrunch.file(
                        "build/" + FD_INTERMEDIATES + "/res/merged/debug/drawable/lib_bg.9.png");

        // assert size are changed.
        assertTrue(srcFile.exists());
        assertTrue(destFile.exists());
        assertNotSame(srcFile.length(), destFile.length());
    }

    @Test
    public void testPngFilesWereGeneratedByAapt2() throws Exception {
        // When using AAPT2 the intermediate files are in the ".flat" format,so we cannot check if
        // they are crunched or not. We can only check they are generated and exist.
        noPngCrunch.executor().withEnabledAapt2(true).run("clean", "assembleDebug");

        File srcFile = noPngCrunch.file("src/main/res/drawable/icon.png");
        File destFile =
                noPngCrunch.file(
                        "build/" + FD_INTERMEDIATES + "/res/merged/debug/drawable_icon.png.flat");

        // Check that the intermediate file was generated.
        assertThat(srcFile).exists();
        assertThat(destFile).exists();

        srcFile = noPngCrunch.file("src/main/res/drawable/lib_bg.9.png");
        destFile =
                noPngCrunch.file(
                        "build/"
                                + FD_INTERMEDIATES
                                + "/res/merged/debug/drawable_lib_bg.9.png.flat");

        // Check the png files exists.
        assertTrue(srcFile.exists());
        assertTrue(destFile.exists());

        // Check if the file is in the APK.
        Path apkPng = noPngCrunch.getApk("debug").getResource("drawable/lib_bg.9.png");
        assertThat(apkPng).exists();
    }
}