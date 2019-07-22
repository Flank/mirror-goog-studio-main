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

import static com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER;
import static com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.GradleTestProjectUtils;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.testutils.apk.Apk;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilterableParameterized.class)
public class ApkCreatedByTest {

    @FilterableParameterized.Parameters(name = "apkCreatorType_{0}")
    public static ApkCreatorType[] params() {
        return new ApkCreatorType[] {APK_Z_FILE_CREATOR, APK_FLINGER};
    }

    @FilterableParameterized.Parameter() public ApkCreatorType apkCreatorType;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Before
    public void setup() {
        GradleTestProjectUtils.setApkCreatorType(project, apkCreatorType);
    }

    @Test
    public void checkCreatedByInApk() throws Exception {
        project.executor().run("assembleDebug");

        Apk apk = project.getApk("debug");
        assertTrue(apk.exists());

        try (ZFile zf = ZFile.openReadOnly(apk.getFile().toFile())) {
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
