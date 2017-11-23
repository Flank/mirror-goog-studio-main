/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.XmlUtils;
import com.android.xml.AndroidXPathFactory;
import com.google.common.io.Files;
import java.nio.charset.StandardCharsets;
import javax.xml.xpath.XPath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.w3c.dom.Document;

public class LibsTestTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libsTest").create();

    @Test
    public void testManifest() throws Exception {
        project.execute(":lib1:assembleAndroidTest");

        String manifestContent =
                Files.toString(
                        project.file(
                                "lib1/build/intermediates/manifest/androidTest/debug/AndroidManifest.xml"),
                        StandardCharsets.UTF_8);

        Document manifest = XmlUtils.parseDocument(manifestContent, true);
        XPath xPath = AndroidXPathFactory.newXPath();

        final String testApkPackage = "com.android.tests.libstest.lib1.test";
        assertThat(xPath.evaluate("/manifest/@package", manifest)).isEqualTo(testApkPackage);
        assertThat(xPath.evaluate("/manifest/instrumentation/@android:name", manifest))
                .isEqualTo("android.test.InstrumentationTestRunner");
        assertThat(xPath.evaluate("/manifest/instrumentation/@android:targetPackage", manifest))
                .isEqualTo(testApkPackage);
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }
}
