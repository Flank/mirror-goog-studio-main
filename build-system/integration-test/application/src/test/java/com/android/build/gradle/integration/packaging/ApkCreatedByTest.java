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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.testutils.apk.Apk;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;

public class ApkCreatedByTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void checkCreatedByInApk() throws Exception {
        project.executor().run("assembleDebug");

        Apk apk = project.getApk("debug");
        assertTrue(apk.exists());

        try (ZFile zf = new ZFile(apk.getFile().toFile())) {
            StoredEntry manifestEntry = zf.get("META-INF/MANIFEST.MF");
            assertNotNull(manifestEntry);

            Properties props = new Properties();

            try (InputStream is = manifestEntry.open()) {
                props.load(is);
            }

            /*
             * This is required to keep track of gradle builds.
             */
            assertTrue(props.getProperty("Created-By").startsWith("Android Gradle"));
        }
    }
}
