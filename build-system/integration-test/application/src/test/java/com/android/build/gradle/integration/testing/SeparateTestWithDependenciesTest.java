package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test separate test module that tests an application with some complicated dependencies : - the
 * app imports a library importing a jar file itself. - use minification.
 */
public class SeparateTestWithDependenciesTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModuleWithDependencies")
                    .create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        project.execute("clean", "assemble");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkAppContainsAllDependentClasses()
            throws IOException, ProcessException, InterruptedException {
        project.execute("clean", "assemble");
        Apk apk = project.getSubproject("app").getApk("debug");
        TruthHelper.assertThatApk(apk)
                .containsClass("Lcom/android/tests/jarDep/JarDependencyUtil;");

        apk = project.getSubproject("app").getApk("minified");
        TruthHelper.assertThatApk(apk)
                .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
    }

    @Test
    public void checkTestAppDoesNotContainAnyMinifiedApplicationDependentClasses()
            throws IOException, ProcessException, InterruptedException {
        project.execute("clean", ":test:assemble");
        Apk apk = project.getSubproject("test").getApk("debug");
        TruthHelper.assertThatApk(apk)
                .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");

        apk = project.getSubproject("test").getApk("minified");
        TruthHelper.assertThatApk(apk)
                .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
    }
}
