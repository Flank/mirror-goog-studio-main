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

package com.android.build.gradle.integration.packaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.apkzlib.zip.CompressionMethod;
import com.android.apkzlib.zip.StoredEntry;
import com.android.apkzlib.zip.ZFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

public class NoCompressTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void noCompressIsAccepted() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid {\naaptOptions {\nnoCompress = ['.no']\n}\n}");

        /*
         * Create two java resources, one with extension 'no' and one with extension 'yes'.
         */
        File resourcesDir = FileUtils.join(project.getTestDir(), "src", "main", "resources");
        FileUtils.mkdirs(resourcesDir);
        Files.write(new byte[1000], new File(resourcesDir, "jres.yes"));
        Files.write(new byte[1000], new File(resourcesDir, "jres.no"));

        /*
         * Create two assets, one with extension 'no' and one with extension 'yes'.
         */
        File assetsDir = FileUtils.join(project.getTestDir(), "src", "main", "assets");
        FileUtils.mkdirs(assetsDir);
        Files.write(new byte[1000], new File(assetsDir, "a.yes"));
        Files.write(new byte[1000], new File(assetsDir, "a.no"));

        /*
         * Create two resources, one with extension 'no' and one with extension 'yes'.
         */
        File rawDir = FileUtils.join(project.getTestDir(), "src", "main", "res", "raw");
        FileUtils.mkdirs(rawDir);
        Files.write(new byte[1000], new File(rawDir, "r_yes.yes"));
        Files.write(new byte[1000], new File(rawDir, "r_no.no"));

        /*
         * Package the apk.
         */
        project.execute(":assembleDebug");

        /*
         * Get the apk.
         */
        Apk apk = project.getApk("debug");
        assertTrue(apk.exists());
        try (ZFile zf = new ZFile(apk.getFile().toFile())) {
            StoredEntry jres_yes = zf.get("jres.yes");
            assertNotNull(jres_yes);
            assertEquals(
                    CompressionMethod.DEFLATE,
                    jres_yes.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            StoredEntry jres_no = zf.get("jres.no");
            assertNotNull(jres_no);
            assertEquals(
                    CompressionMethod.STORE,
                    jres_no.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            StoredEntry a_yes = zf.get("assets/a.yes");
            assertNotNull(a_yes);
            assertEquals(
                    CompressionMethod.DEFLATE,
                    a_yes.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            StoredEntry a_no = zf.get("assets/a.no");
            assertNotNull(a_no);
            assertEquals(
                    CompressionMethod.STORE,
                    a_no.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            StoredEntry r_yes = zf.get("res/raw/r_yes.yes");
            assertNotNull(r_yes);
            assertEquals(
                    CompressionMethod.DEFLATE,
                    r_yes.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());

            StoredEntry r_no = zf.get("res/raw/r_no.no");
            assertNotNull(r_no);
            assertEquals(
                    CompressionMethod.STORE,
                    r_no.getCentralDirectoryHeader().getCompressionInfoWithWait().getMethod());
        }
    }

}
