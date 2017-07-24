package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for DSL AAPT options. */
public class AaptOptionsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws IOException {
        FileUtils.createFile(project.file("src/main/res/raw/ignored"), "ignored");
        FileUtils.createFile(project.file("src/main/res/raw/kept"), "kept");
    }

    @Test
    public void testAaptOptionsFlagsWithAapt() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  aaptOptions {\n"
                        + "    additionalParameters \"--ignore-assets\", \"!ignored*\"\n"
                        + "  }\n"
                        + "}\n");
        project.executor().withEnabledAapt2(false).run("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsFileWithContent("res/raw/kept", "kept");
        assertThat(apk).doesNotContain("res/raw/ignored");

        FileUtils.createFile(project.file("src/main/res/raw/ignored2"), "ignored2");
        FileUtils.createFile(project.file("src/main/res/raw/kept2"), "kept2");

        project.executor().withEnabledAapt2(false).run("assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsFileWithContent("res/raw/kept2", "kept2");
        assertThat(apk).doesNotContain("res/raw/ignored2");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "additionalParameters \"--ignore-assets\", \"!ignored\\*\"",
                "");

        project.executor().withEnabledAapt2(false).run("assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsFileWithContent("res/raw/kept", "kept");
        assertThat(apk).containsFileWithContent("res/raw/ignored", "ignored");
        assertThat(apk).containsFileWithContent("res/raw/kept2", "kept2");
        assertThat(apk).containsFileWithContent("res/raw/ignored2", "ignored2");
    }

    @Test
    public void testAaptOptionsFlagsWithAapt2() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  aaptOptions {\n"
                        + "    additionalParameters \"--extra-packages\", \"com.boop.beep\"\n"
                        + "  }\n"
                        + "}\n");

        project.executor().withEnabledAapt2(true).run("clean", "assembleDebug");

        Joiner joiner = Joiner.on(File.separator);
        File extraR =
                new File(
                        joiner.join(
                                project.getOutputDir().getParentFile(),
                                "generated",
                                "source",
                                "r",
                                "debug",
                                "com",
                                "boop",
                                "beep",
                                "R.java"));

        // Check that the extra R.java file was generated in the specified package.
        assertThat(extraR).exists();
        assertThat(extraR).contains("package com.boop.beep");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "additionalParameters \"--extra-packages\", \"com.boop.beep\"",
                "");

        project.executor().withEnabledAapt2(true).run("assembleDebug");

        // Check that the extra R.java file is not generated if the extra options aren't present.
        assertThat(extraR).doesNotExist();
    }
}
