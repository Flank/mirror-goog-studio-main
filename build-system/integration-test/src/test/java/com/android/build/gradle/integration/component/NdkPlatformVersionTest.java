package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NativeModelHelper;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.repository.Revision;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration test for platform version.
 */
public class NdkPlatformVersionTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .useExperimentalGradleVersion(true)
            .create();


    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void testDefaultPlatformVersion() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "model {\n"
                        + "    android {\n"
                        + "        ndk {\n"
                        + "            platformVersion 19\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        NativeAndroidProject model =
                project.executeAndReturnModel(NativeAndroidProject.class, "assembleDebug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk("debug");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/mips/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");

        // 64-bits binaries will not be produced if platform version 19 is used.
        assertThat(apk).doesNotContain("lib/x86_64/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/arm64-v8a/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/mips64/libhello-jni.so");

        assertThat(model.getArtifacts()).hasSize(8); // 4 ABI * 2 variants.
        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> flags = NativeModelHelper.getFlatCFlags(model, artifact);
            Optional<String> sysrootFlag = flags.stream().filter(f -> f.contains("sysroot")).findFirst();
            assertThat(sysrootFlag.isPresent()).isTrue();

            if (getNdkMajorVersion() > 14) {
                // unified headers are on by default
                assertThat(sysrootFlag.get())
                        .contains(
                                FileUtils.join(
                                        GradleTestProject.ANDROID_NDK_HOME.getPath(), "sysroot"));
            } else {
                assertThat(sysrootFlag.get()).contains("android-19");
            }
        }
    }


    @Test
    public void testAbiSpecificPlatformVersion() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "model {\n"
                        + "    android {\n"
                        + "        abis {\n"
                        + "            create(\"x86\") {\n"
                        + "                platformVersion \"android-19\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        NativeAndroidProject model = project.executeAndReturnModel(NativeAndroidProject.class, "assembleDebug");

        assertThat(model.getArtifacts()).hasSize(Abi.values().length * 2); // 4 ABI * 2 variants.
        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> flags = NativeModelHelper.getFlatCFlags(model, artifact);
            Optional<String> sysrootFlag = flags.stream().filter(f -> f.contains("sysroot")).findFirst();
            assertThat(sysrootFlag.isPresent()).isTrue();

            String expected;
            if (getNdkMajorVersion() > 14) {
                // unified headers are on by default
                expected = FileUtils.join(GradleTestProject.ANDROID_NDK_HOME.getPath(), "sysroot");
            } else if (artifact.getName().endsWith("x86")) {
                expected = "android-19";
            } else {
                AndroidVersion selectedVersion =
                        NdkHelper.getPlatformSupported(
                                GradleTestProject.ANDROID_NDK_HOME,
                                GradleTestProject.getCompileSdkHash());
                expected = AndroidTargetHash.getPlatformHashString(selectedVersion);
            }
            //noinspection OptionalGetWithoutIsPresent
            assertThat(sysrootFlag.get()).contains(expected);
        }
    }

    private static int getNdkMajorVersion() {
        Revision ndkVersion = NdkHandler.findRevision(GradleTestProject.ANDROID_NDK_HOME);
        return Objects.requireNonNull(ndkVersion).getMajor();
    }
}
