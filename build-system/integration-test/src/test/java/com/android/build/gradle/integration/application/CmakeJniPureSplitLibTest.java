package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for pure splits under CMake */
public class CmakeJniPureSplitLibTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("ndkJniPureSplitLib")
                    .addFile(HelloWorldJniApp.cmakeLists("lib"))
                    .create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        new File(project.getTestDir(), "src/main/jni")
                .renameTo(new File(project.getTestDir(), "src/main/cxx"));
        AssumeUtil.assumeBuildToolsAtLeast(21);
        GradleTestProject lib = project.getSubproject("lib");
        // No explicit project, but project "lights up" because CMakeLists.txt is present
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkVersionCode() throws IOException {
        GradleTestProject app = project.getSubproject("app");
        assertThat(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "free"))
                .hasVersionCode(123);
        assertThat(app.getApk("mips", GradleTestProject.ApkType.DEBUG, "free")).hasVersionCode(123);
        assertThat(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "free")).hasVersionCode(123);
        assertThat(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "paid"))
                .hasVersionCode(123);
        assertThat(app.getApk("mips", GradleTestProject.ApkType.DEBUG, "paid")).hasVersionCode(123);
        assertThat(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "paid")).hasVersionCode(123);
    }

    @Test
    public void checkSo() throws IOException {
        GradleTestProject app = project.getSubproject("app");
        assertThat(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "free"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        assertThat(app.getApk("mips", GradleTestProject.ApkType.DEBUG, "paid"))
                .contains("lib/mips/libhello-jni.so");
    }
}
