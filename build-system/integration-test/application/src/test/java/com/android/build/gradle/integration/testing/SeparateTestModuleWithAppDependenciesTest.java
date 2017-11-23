package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class SeparateTestModuleWithAppDependenciesTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "\n"
                        + "    publishNonDefault true\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 9\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    compile 'com.google.android.gms:play-services-base:"
                        + GradleTestProject.PLAY_SERVICES_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:appcompat-v7:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        File srcDir = project.getSubproject("app").getMainSrcDir();
        srcDir = new File(srcDir, "foo");
        srcDir.mkdirs();
        TestFileUtils.appendToFile(
                new File(srcDir, "FooActivity.java"),
                "\n"
                        + "package foo;\n"
                        + "\n"
                        + "import android.os.Bundle;\n"
                        + "import android.support.v7.app.AppCompatActivity;\n"
                        + "import android.view.View;\n"
                        + "import android.widget.TextView;\n"
                        + "\n"
                        + "public class FooActivity extends AppCompatActivity {\n"
                        + "\n"
                        + "    @Override\n"
                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support.test:rules:"
                        + GradleTestProject.TEST_SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-annotations:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        srcDir = project.getSubproject("test").getMainSrcDir();
        srcDir = new File(srcDir, "foo");
        srcDir.mkdirs();
        TestFileUtils.appendToFile(
                new File(srcDir, "FooActivityTest.java"),
                "\n"
                        + "package foo;\n"
                        + "\n"
                        + "public class FooActivityTest {\n"
                        + "    @org.junit.Rule \n"
                        + "    android.support.test.rule.ActivityTestRule<foo.FooActivity> activityTestRule =\n"
                        + "            new android.support.test.rule.ActivityTestRule<>(foo.FooActivity.class);\n"
                        + "}\n");

        project.executeAndReturnMultiModel("clean", "test:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkModel() throws Exception {
        // check the content of the test model.
    }
}
