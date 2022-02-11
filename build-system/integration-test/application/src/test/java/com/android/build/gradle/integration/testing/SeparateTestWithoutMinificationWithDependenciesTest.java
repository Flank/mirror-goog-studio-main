package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test separate test module that tests an application with some complicated dependencies : - the
 * app imports a library importing a jar file itself.
 */
public class SeparateTestWithoutMinificationWithDependenciesTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModuleWithDependencies")
                    .withDependencyChecker(false)
                    .create();

    @Before
    public void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "        android {\n"
                        + "            targetVariant 'debug'\n"
                        + "        }\n");
        project.execute("clean");
        project.executor().run("assemble");
    }

    @Test
    public void checkApkContent() throws IOException, ProcessException {
        Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk)
                .containsClass("Lcom/android/tests/jarDep/JarDependencyUtil;");

        Apk apkTest = project.getSubproject("test").getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apkTest)
                .doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;");
    }
}
