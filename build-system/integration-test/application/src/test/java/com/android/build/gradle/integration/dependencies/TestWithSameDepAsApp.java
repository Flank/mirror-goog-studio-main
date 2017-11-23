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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests the handling of test dependency.
 */
@RunWith(FilterableParameterized.class)
public class TestWithSameDepAsApp {

    @Rule
    public GradleTestProject project;

    public String plugin;
    public String appDependency;
    public String testDependency;
    public String className;
    public String appUsage;
    public String testUsage;

    @Parameterized.Parameters(name = "{0}: {1}, {2}")
    public static Collection<Object[]> data() {
        List<String> plugins = Lists.newArrayList("com.android.application", "com.android.library");
        List<Object[]> parameters = Lists.newArrayList();

        for (String plugin : plugins) {
            // Check two JARs.
            parameters.add(Lists.newArrayList(
                    plugin,
                    "org.hamcrest:hamcrest-core:1.3",
                    "org.hamcrest:hamcrest-core:1.3",
                    "Lorg/hamcrest/Matcher;",
                    "org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is(\"foo\");",
                    "org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is(\"foo\");").toArray());

            // Check two JARs, indirect conflict.
            parameters.add(Lists.newArrayList(
                    plugin,
                    "org.hamcrest:hamcrest-core:1.3",
                    "junit:junit:4.12",
                    "Lorg/hamcrest/Matcher;",
                    "org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is(\"foo\");",
                    "org.hamcrest.Matcher<String> m = org.hamcrest.CoreMatchers.is(\"foo\");").toArray());

            // Check two AARs.
            parameters.add(
                    Lists.newArrayList(
                                    plugin,
                                    "com.android.support:support-v4:"
                                            + GradleTestProject.SUPPORT_LIB_VERSION,
                                    "com.android.support:support-v4:"
                                            + GradleTestProject.SUPPORT_LIB_VERSION,
                                    "Landroid/support/v4/widget/Space;",
                                    "new android.support.v4.widget.Space(this);",
                                    "new android.support.v4.widget.Space(getActivity());")
                            .toArray());

            // Check two AARs, indirect conflict.
            parameters.add(
                    Lists.newArrayList(
                                    plugin,
                                    "com.android.support:support-v4:"
                                            + GradleTestProject.SUPPORT_LIB_VERSION,
                                    "com.android.support:recyclerview-v7:"
                                            + GradleTestProject.SUPPORT_LIB_VERSION,
                                    "Landroid/support/v4/widget/Space;",
                                    "new android.support.v4.widget.Space(this);",
                                    "new android.support.v4.widget.Space(getActivity());")
                            .toArray());
        }

        return parameters;
    }

    public TestWithSameDepAsApp(
            String plugin,
            String appDependency,
            String testDependency,
            String className,
            String appUsage,
            String testUsage)
            throws Exception {
        this.plugin = plugin;
        this.appDependency = appDependency;
        this.testDependency = testDependency;
        this.className = className;
        this.appUsage = appUsage;
        this.testUsage = testUsage;

        this.project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin(plugin))
            .create();
    }

    @Before
    public void setUp() throws Exception {
        appendToFile(project.getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile \"" + this.appDependency + "\"\n" +
                "    androidTestCompile \"" + this.testDependency + "\"\n" +
                "}\n" +
                "\n" +
                "android.defaultConfig.minSdkVersion 16\n");

        TestFileUtils.addMethod(
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                        "\n" +
                        "public void useDependency() {\n" +
                        "    " + this.appUsage + "\n" +
                        "}\n" +
                        ""
        );

        TestFileUtils.addMethod(
                project.file("src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                "\n"
                        + "public void testDependency() throws Exception {\n"
                        + "    "
                        + this.testUsage
                        + "\n"
                        + "}\n"
                        + "");
    }

    @Test
    public void testWithSamedepVersionThanTestedDoesNotEmbedDependency() throws Exception {
        project.execute("assembleDebug", "assembleDebugAndroidTest");

        if (plugin.contains("application")) {
            TruthHelper.assertThat(project.getApk("debug")).containsClass(this.className);
            TruthHelper.assertThat(project.getTestApk()).doesNotContainClass(this.className);
        } else {
            // External dependencies are not packaged in AARs.
            TruthHelper.assertThat(project.getAar("debug")).doesNotContainClass(this.className);
            // But should be in the test APK.
            TruthHelper.assertThat(project.getTestApk()).containsClass(this.className);
        }
    }

    @Test
    @Category(DeviceTests.class)
    public void runTestsOnDevices() throws Exception {
        project.executeConnectedCheck();
    }
}
