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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.gradle.api.JavaVersion;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Jack test for Java 8 features. Also testing the additional parameters property of the
 * jack options DSL block.
 */
public class JackJava8Test {

    private AndroidTestApp app = HelloWorldApp.forPlugin("com.android.application");
    private File javaSrc;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(app)
            .create();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Jack tool requires Java 7", JavaVersion.current().isJava7Compatible());

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    compileSdkVersion 'android-24'\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 24\n"
                        + "        jackOptions {\n"
                        + "            enabled true\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        javaSrc = project.file(app.getFile("HelloWorld.java").getPath());
        Files.write(
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "import android.widget.TextView;\n"
                        + "\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        TextView tv = new TextView(this);\n"
                        + "        tv.setText(new Hello().get());\n"
                        + "        setContentView(tv);\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "interface StringOperation {\n"
                        + "    String execute(String str1, String str2);\n"
                        + "}\n"
                        + "\n"
                        + "interface Function {"
                        + "}"
                        + "\n"
                        + "interface StringProvider {\n"
                        + "    // test static interface method\n"
                        + "    static String hello() { \n"
                        + "        // test lambda and intersection type\n"
                        + "        StringOperation concat = (Function & StringOperation) (String str1, String str2) -> { return str1 + str2; };\n"
                        + "        return concat.execute(\"Hello \", \"world!\");\n"
                        + "    }\n"
                        + "\n"
                        + "    // test default interface method\n"
                        + "    default public String get() {\n"
                        + "        return hello();\n"
                        + "    }\n"
                        + "}\n"
                        + ""
                        + "class Hello implements StringProvider {\n"
                        + "}\n",
                javaSrc,
                Charsets.UTF_8);
    }

    @Test
    public void java8FeaturesSanityTest() throws Exception {
        Assume.assumeTrue("Only implicitly upgrades with JDK 1.8",
                JavaVersion.current().isJava8Compatible());
        project.execute("assembleDebug");
    }

    @Test
    public void java8FeaturesSanityTest_explicitVersion() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    compileOptions {\n"
                        + "        sourceCompatibility '1.8'\n"
                        + "        targetCompatibility '1.8'\n"
                        + "    }\n"
                        + "}\n");

        project.execute("assembleDebug");
    }

    @Test
    public void unitTest() throws Exception {
        Assume.assumeTrue("Only applicable with JDK 1.8", JavaVersion.current().isJava8Compatible());
        project.execute("testDebug");
    }
}
