package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.testutils.apk.Apk;
import com.android.testutils.truth.MoreTruth;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Basic integration test for native plugin. */
@RunWith(Parameterized.class)
@Category(SmokeTests.class)
public class BasicNdkComponentTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().useCppSource().build())
                    .useExperimentalGradleVersion(true)
                    .withHeap("2048m")
                    .create();

    @Parameterized.Parameter(value = 0)
    public String toolchain;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(new Object[] {"gcc"}, new Object[] {"clang"});
    }

    @Before
    public void setUp() throws IOException {
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
                        + "\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            platformVersion 19\n"
                        + "            toolchain \""
                        + toolchain
                        + "\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void assemble() throws IOException, InterruptedException {
        project.execute("assemble");
    }

    @Test
    public void assembleRelease() throws IOException, InterruptedException {
        project.execute("assembleRelease");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(GradleTestProject.ApkType.of("release", false));
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/mips/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void assembleDebug() throws IOException, InterruptedException {
        project.execute("assembleDebug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/mips/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");

        // 64-bits binaries will not be produced if platform version 19 is used.
        assertThat(apk).doesNotContain("lib/x86_64/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/arm64-v8a/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/mips64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        // Clang do not use response file with NDK <= r12 due to b.android.com/204552.
        if (toolchain.equals("clang")
                || NdkHandler.findRevision(GradleTestProject.ANDROID_NDK_HOME).getMajor() >= 13) {
            MoreTruth.assertThat(
                            project.file(
                                    "build/tmp/compileHello-jniArmeabiDebugSharedLibraryHello-jniArmeabiDebugSharedLibraryMainCpp/options.txt"))
                    .exists();
            MoreTruth.assertThat(
                            project.file(
                                    "build/tmp/linkHello-jniArmeabiDebugSharedLibrary/options.txt"))
                    .exists();
        }
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
