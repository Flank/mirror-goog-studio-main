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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AndroidTestResourcesConnectedTest {
    @Rule
    public GradleTestProject appProject =
            GradleTestProject.builder()
                    .withName("application")
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .create();

    @Before
    public void setUp() throws IOException {
        setUpProject(appProject);
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\n"
                        + "                apply plugin: 'com.android.application'\n"
                        + "                android {\n"
                        + "                    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "                }\n");
    }

    private static void setUpProject(GradleTestProject project) throws IOException {
        Path layout =
                project.getTestDir()
                        .toPath()
                        .resolve("src/androidTest/res/layout/test_layout_1.xml");

        String testLayout =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        android:layout_width=\"match_parent\"\n"
                        + "        android:layout_height=\"match_parent\"\n"
                        + "        android:orientation=\"vertical\" >\n"
                        + "    <TextView android:id=\"@+id/test_layout_1_textview\"\n"
                        + "            android:layout_width=\"wrap_content\"\n"
                        + "            android:layout_height=\"wrap_content\"\n"
                        + "            android:text=\"Hello, I am a TextView\" />\n"
                        + "</LinearLayout>\n";

        Files.createDirectories(layout.getParent());
        Files.write(layout, testLayout.getBytes());

        // This class exists to prevent the resource from being automatically removed,
        // if we start filtering test resources by default.
        Path resourcesTest =
                project.getTestDir()
                        .toPath()
                        .resolve(
                                "src/androidTest/java/com/example/helloworld/HelloWorldResourceTest.java");

        Files.createDirectories(resourcesTest.getParent());
        String sourcesTestContent =
                "package com.example.helloworld;\n"
                        + "                import android.test.ActivityInstrumentationTestCase2;\n"
                        + "                import android.test.suitebuilder.annotation.MediumTest;\n"
                        + "                import android.widget.TextView;\n"
                        + "\n"
                        + "                public class HelloWorldResourceTest extends\n"
                        + "                        ActivityInstrumentationTestCase2<HelloWorld> {\n"
                        + "                    private TextView mainAppTextView;\n"
                        + "                    private Object testLayout;\n"
                        + "\n"
                        + "                    public HelloWorldResourceTest() {\n"
                        + "                        super(\"com.example.helloworld\", HelloWorld.class);\n"
                        + "                    }\n"
                        + "\n"
                        + "                    @Override\n"
                        + "                    protected void setUp() throws Exception {\n"
                        + "                        super.setUp();\n"
                        + "                        final HelloWorld a = getActivity();\n"
                        + "                        mainAppTextView = (TextView) a.findViewById(\n"
                        + "                                com.example.helloworld.R.id.text);\n"
                        + "                        testLayout = getInstrumentation().getContext().getResources()\n"
                        + "                                .getLayout(com.example.helloworld.test.R.layout.test_layout_1);\n"
                        + "                    }\n"
                        + "\n"
                        + "                    @MediumTest\n"
                        + "                    public void testPreconditions() {\n"
                        + "                        assertNotNull(\"Should find test test_layout_1.\", testLayout);\n"
                        + "                        assertNotNull(\"Should find main app text view.\", mainAppTextView);\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                ";
        Files.write(resourcesTest, sourcesTestContent.getBytes());
    }

    @Test
    @Category(DeviceTests.class)
    public void checkTestLayoutCanBeUsedInDeviceTests() throws IOException, InterruptedException {
        appProject.executeConnectedCheck();
    }
}
