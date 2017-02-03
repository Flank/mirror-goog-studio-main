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


import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.BuildScriptGenerator;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for annotation processor.
 */
@RunWith(FilterableParameterized.class)
public class AnnotationProcessorTest {
    @Parameterized.Parameters(name = "forJack={0}, forComponentPlugin={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // forJack       forComponentPlugin
                {true,           false},
                {false,          false},
                {true,           true},
                {false,          true},
        });
    }


    private final boolean forJack;
    private final boolean forComponentPlugin;

    @Rule
    public GradleTestProject project;

    @Rule
    public Adb adb = new Adb();

    public AnnotationProcessorTest(boolean forJack, boolean forComponentPlugin) {
        this.forJack = forJack;
        this.forComponentPlugin = forComponentPlugin;

        project = GradleTestProject.builder()
                .fromTestApp(new MultiModuleTestProject(
                        ImmutableMap.of(
                                ":app", sApp,
                                ":lib", AnnotationProcessorLib.createLibrary(),
                                ":lib-compiler", AnnotationProcessorLib.createCompiler()
                                )))
                .useExperimentalGradleVersion(forComponentPlugin)
                .create();
    }
    private static AndroidTestApp sApp = HelloWorldApp.noBuildFile();
    static {
        sApp.removeFile(sApp.getFile("HelloWorld.java"));
        sApp.addFile(new TestSourceFile(
        "src/main/java/com/example/helloworld", "HelloWorld.java",
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.widget.TextView;\n"
                        + "import android.os.Bundle;\n"
                        + "import com.example.annotation.ProvideString;\n"
                        + "\n"
                        + "@ProvideString\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        TextView tv = new TextView(this);\n"
                        + "        tv.setText(getString());\n"
                        + "        setContentView(tv);\n"
                        + "    }\n"
                        + "\n"
                        + "    public static String getString() {\n"
                        + "        return new com.example.annotation.HelloWorldStringValue().value;\n"
                        + "    }\n"
                        + "\n"
                        + "    public static String getProcessor() {\n"
                        + "        return new com.example.annotation.HelloWorldStringValue().processor;\n"
                        + "    }\n"
                        + "}\n"));


        sApp.removeFile(sApp.getFile("HelloWorldTest.java"));
        sApp.addFile(new TestSourceFile(
                "src/androidTest/java/com/example/hellojni", "HelloWorldTest.java",
                "package com.example.helloworld;\n" +
                        "\n" +
                        "import android.test.ActivityInstrumentationTestCase;\n" +
                        "\n" +
                        "public class HelloWorldTest extends ActivityInstrumentationTestCase<HelloWorld> {\n" +
                        "\n" +
                        "    public HelloWorldTest() {\n" +
                        "        super(\"com.example.helloworld\", HelloWorld.class);\n" +
                        "    }\n" +
                        "\n" +
                        "    public void testStringValue() {\n" +
                        "        assertTrue(\"Hello\".equals(HelloWorld.getString()));\n" +
                        "    }\n" +
                        "    public void testProcessor() {\n" +
                        "        assertTrue(\"Processor\".equals(HelloWorld.getProcessor()));\n" +
                        "    }\n" +
                        "}\n"));
    }

    @Before
    public void setUp() throws Exception {
        String buildScript = new BuildScriptGenerator(
                "\n"
                        + "apply from: \"../../commonHeader.gradle\"\n"
                        + "buildscript { apply from: \"../../commonBuildScript.gradle\" }\n"
                        + "apply from: \"../../commonLocalRepo.gradle\"\n"
                        + "\n"
                        + "apply plugin: '${application_plugin}'\n"
                        + "\n"
                        + "${model_start}"
                        + "android {\n"
                        + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                        + "    defaultConfig {\n"
                        + "        jackOptions {\n"
                        + "            enabled " + forJack + "\n"
                        + "        }\n"
                        + "        javaCompileOptions {\n"
                        + "            annotationProcessorOptions {\n"
                        + "                ${argument}\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "${model_end}\n")
                .addPattern(
                        "argument",
                        "argument \"value\", \"Hello\"",
                        "arguments { create(\"value\") { value \"Hello\" }\n }")
                .build(forComponentPlugin);
        Files.write(buildScript, project.getSubproject(":app").file("build.gradle"), Charsets.UTF_8);
    }

    @Test
    public void normalBuild() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "dependencies {\n"
                        + "    compile project(':lib')\n"
                        + "    annotationProcessor project(':lib-compiler')\n"
                        + "}\n");

        project.execute("assembleDebug");
        File aptOutputFolder = project.getSubproject(":app").file("build/generated/source/apt/debug");
        assertThat(new File(aptOutputFolder, "HelloWorldStringValue.java")).exists();

        AndroidProject model = project.model().getMulti().getModelMap().get(":app");
        assertThat(ModelHelper.getDebugArtifact(model).getGeneratedSourceFolders())
                .contains(aptOutputFolder);
    }

    /**
     * Test compile classpath is being added to processor path.
     */
    @Test
    public void compileClasspathIncludedInProcessor() throws Exception {
        File emptyJar = project.getSubproject("app").file("empty.jar");
        assertThat(emptyJar.createNewFile()).isTrue();

        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                new BuildScriptGenerator(
                        "${model_start}\n"
                                + "    android {\n"
                                + "        defaultConfig {\n"
                                + "            javaCompileOptions {\n"
                                + "                annotationProcessorOptions {\n"
                                + "                    includeCompileClasspath = true\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "${model_end}\n"
                                + "dependencies {\n"
                                + "    compile project(':lib-compiler')\n"
                                + "    annotationProcessor files('empty.jar')\n"
                                + "}\n").build(forComponentPlugin));

        project.execute("assembleDebug");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "dependencies {\n"
                        + "    compile project(':lib')\n"
                        + "    annotationProcessor project(':lib-compiler')\n"
                        + "}\n");
        project.executeConnectedCheck();
    }

    @Test
    public void checkBuildscriptDependencyNotUsedForJackAP() throws Exception {
        // check for jack and non-component plugin
        Assume.assumeTrue(forJack && !forComponentPlugin);

        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "dependencies {\n"
                        + "    compile project(':lib')\n"
                        + "    annotationProcessor project(':lib-compiler')\n"
                        + "}\n");

        GradleTestProject proc = project.getSubproject("lib-compiler");
        TestFileUtils.appendToFile(
                proc.getBuildFile(),
                "apply from: '../../commonLocalRepo.gradle'"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.google.dagger:dagger:2.6'\n"
                        + "}\n");

        // update the annotation processor the reference enum that exists in 2.6 but not in 1.2.2
        TestFileUtils.searchAndReplace(
                FileUtils.join(
                        proc.getMainSrcDir(), "com", "example", "annotation", "Processor.java"),
                "\n}\\s*$",
                "String s = dagger.Provides.Type.MAP.toString();" + "\n\n}");


        // add older dagger to the buildscript classpath
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath 'com.squareup.dagger:dagger:1.2.2'\n"
                        + "    }\n"
                        + "}\n");

        // make sure we resolve used class to the 2.6 version
        project.execute("assembleDebug");
    }
}
