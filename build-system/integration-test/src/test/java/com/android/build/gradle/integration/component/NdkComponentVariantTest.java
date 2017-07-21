package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.testing.api.DeviceException;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Integration test of the native plugin with multiple variants. */
public class NdkComponentVariantTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Rule public Adb adb = new Adb();

    @BeforeClass
    public static void setUp() throws IOException {

        TestFileUtils.appendToFile(
                project.getBuildFile(),
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
                        + "        }\n"
                        + "        productFlavors {\n"
                        + "            create(\"x86\") {\n"
                        + "                ndk.abiFilters.add(\"x86\")\n"
                        + "                dimension \"abi\"\n"
                        + "            }\n"
                        + "            create(\"arm\") {\n"
                        + "                ndk.abiFilters.add(\"armeabi-v7a\")\n"
                        + "                ndk.abiFilters.add(\"armeabi\")\n"
                        + "                dimension \"abi\"\n"
                        + "            }\n"
                        + "            create(\"mips\") {\n"
                        + "                ndk.abiFilters.add(\"mips\")\n"
                        + "                dimension \"abi\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "        abis {\n"
                        + "            create(\"x86\") {\n"
                        + "                CFlags.add(\"-DX86\")\n"
                        + "            }\n"
                        + "            create(\"armeabi-v7a\") {\n"
                        + "                CFlags.add(\"-DARMEABI_V7A\")\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkOldNdkTasksAreNotCreated() throws IOException {
        List<String> tasks = project.model().getTaskList();
        assertThat(tasks)
                .containsNoneOf(
                        "compileArmDebugNdk",
                        "compileX86DebugNdk",
                        "compileMipsDebugNdk",
                        "compileArmReleaseNdk",
                        "compileX86ReleaseNdk",
                        "compileMipsReleaseNdk");
    }

    @Test
    public void assembleX86Debug() throws IOException, InterruptedException {
        project.execute("assembleX86Debug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(null, GradleTestProject.ApkType.DEBUG, "x86");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/mips/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void assembleArmDebug() throws IOException, InterruptedException {
        project.execute("assembleArmDebug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(null, GradleTestProject.ApkType.DEBUG, "arm");
        assertThat(apk).doesNotContain("lib/x86/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/mips/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void assembleMipsDebug() throws IOException, InterruptedException {
        project.execute("assembleMipsDebug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(null, GradleTestProject.ApkType.DEBUG, "mips");
        assertThat(apk).doesNotContain("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/mips/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkReleaseBuild() throws IOException, InterruptedException {
        project.execute("assembleArmRelease");

        Apk apk = project.getApk(null, GradleTestProject.ApkType.RELEASE, "arm");
        assertThat(apk).contains("lib/armeabi/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() throws IOException, InterruptedException, DeviceException {
        adb.exclusiveAccess();
        if (GradleTestProject.DEVICE_PROVIDER_NAME.equals(BuilderConstants.CONNECTED)) {
            Collection<String> abis = DeviceHelper.getDeviceAbis();
            if (abis.contains("x86")) {
                project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "x86DebugAndroidTest");
            } else {
                project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "ArmDebugAndroidTest");
            }

        } else {
            project.execute(GradleTestProject.DEVICE_PROVIDER_NAME + "X86DebugAndroidTest");
        }

    }
}
