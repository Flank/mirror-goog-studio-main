package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test separate test module that tests an application with some complicated dependencies : - the
 * app imports a library importing a jar file itself.
 */
public class SeparateTestWithoutMinificationWithDependenciesTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModuleWithDependencies")
                    .withDependencyChecker(false)
                    .create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "        apply plugin: 'com.android.test'\n"
                        + "\n"
                        + "        android {\n"
                        + "            compileSdkVersion rootProject.latestCompileSdk\n"
                        + "            buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "\n"
                        + "            targetProjectPath ':app'\n"
                        + "            targetVariant 'debug'\n"
                        + "        }\n");
        project.execute("clean", "assemble");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkAppContainsAllDependentClasses() throws IOException, ProcessException {
        Apk apk = project.getSubproject("app").getApk("debug");
        TruthHelper.assertThatApk(apk)
                .containsClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
    }

    @Test
    public void checkTestAppDoesNotContainAnyApplicationDependentClasses()
            throws IOException, ProcessException {
        Apk apk = project.getSubproject("test").getApk("debug");
        TruthHelper.assertThatApk(apk)
                .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
    }
}
