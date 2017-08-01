package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Tests C/C++/ld flags in an NDK project. */
public class NdkFlagsTest {

    private static AndroidTestApp cApp = new HelloWorldJniApp();

    @ClassRule
    public static GradleTestProject cProject =
            GradleTestProject.builder()
                    .withName("c_project")
                    .fromTestApp(cApp)
                    .useExperimentalGradleVersion(true)
                    .create();

    private static AndroidTestApp cppApp = HelloWorldJniApp.builder().useCppSource().build();

    @ClassRule
    public static GradleTestProject cppProject =
            GradleTestProject.builder()
                    .withName("cpp_project")
                    .fromTestApp(cppApp)
                    .useExperimentalGradleVersion(true)
                    .create();

    private static AndroidTestApp ldApp = new HelloWorldJniApp();

    @ClassRule
    public static GradleTestProject ldProject =
            GradleTestProject.builder()
                    .withName("ld_project")
                    .fromTestApp(ldApp)
                    .useExperimentalGradleVersion(true)
                    .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(
                cProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            CFlags.addAll(['-DHELLO_WORLD=\"hello world\"', '-DEXCLAMATION_MARK=\"!\"'])\n"
                        + "            CFlags.add(' -DFLAG_WITH_LEADING_SPACE')\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                cppProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            cppFlags.addAll(['-DHELLO_WORLD=\"hello world\"', '-DEXCLAMATION_MARK=\"!\"'])\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                ldProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            ldFlags.addAll(\"-llog\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        cProject = null;
    }

    @Test
    public void assembleCProject() throws IOException, InterruptedException {
        cProject.execute("assembleDebug");
        assertThat(cProject.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/x86/libhello-jni.so");
    }

    @Test
    public void assembleCppProject() throws IOException, InterruptedException {
        cppProject.execute("assembleDebug");
        assertThat(cppProject.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/x86/libhello-jni.so");
    }

    @Test
    public void assembleLdProject() throws IOException, InterruptedException {
        ldProject.execute("assembleDebug");
        assertThat(ldProject.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/x86/libhello-jni.so");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheckCProject() throws IOException, InterruptedException {
        cProject.executeConnectedCheck();
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheckCppProject() throws IOException, InterruptedException {
        cppProject.executeConnectedCheck();
    }
}
