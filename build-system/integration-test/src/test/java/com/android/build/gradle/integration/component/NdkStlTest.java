package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.repository.Revision;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.gradle.tooling.BuildException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration test for STL containers.
 *
 * This unit test is parameterized and will be executed for various values of STL.
 */
@RunWith(Parameterized.class)
public class NdkStlTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().useCppSource().build())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {

        return ImmutableList.of(
                new Object[] {"system"},
                new Object[] {"stlport_static"},
                new Object[] {"stlport_shared"},
                new Object[] {"gnustl_static"},
                new Object[] {"gnustl_shared"},
                new Object[] {"gabi++_static"},
                new Object[] {"gabi++_shared"},
                new Object[] {"c++_static"},
                new Object[] {"c++_shared"},
                new Object[] {"invalid"});
    }

    private String stl;

    public NdkStlTest(String stl) {
        this.stl = stl;
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
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            stl \""
                        + stl
                        + "\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void buildAppWithStl() throws IOException, InterruptedException {
        // ndk r11 does noes support gabi++
        Revision ndkRevision = NdkHandler.findRevision(GradleTestProject.ANDROID_NDK_HOME);
        boolean notGabiSupported =
                stl.startsWith("gabi++") && ndkRevision != null && ndkRevision.getMajor() >= 11;

        if (stl.equals("invalid") || notGabiSupported) {
            // Fail if it's invalid.
            try {
                project.execute("assembleDebug");
                Assert.fail();
            } catch (BuildException ignored) {
            }
        } else {
            project.execute("assembleDebug");

            Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
            assertThat(apk).contains("lib/x86/libhello-jni.so");
            assertThat(apk).contains("lib/mips/libhello-jni.so");
            assertThat(apk).contains("lib/armeabi/libhello-jni.so");
            assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");

            if (stl.endsWith("shared")) {
                assertThat(apk).contains("lib/x86/lib" + stl + ".so");
                assertThat(apk).contains("lib/mips/lib" + stl + ".so");
                assertThat(apk).contains("lib/armeabi/lib" + stl + ".so");
                assertThat(apk).contains("lib/armeabi-v7a/lib" + stl + ".so");
            }

        }

    }

    @Test
    public void checkWithCodeThatUsesTheStl() throws IOException, InterruptedException {
        assume().that(stl).isNotEqualTo("invalid");
        assume().that(stl).isNotEqualTo("system");
        assume().that(stl).isNotEqualTo("gabi++_shared");
        assume().that(stl).isNotEqualTo("gabi++_static");

        File src = FileUtils.find(project.getTestDir(), "hello-jni.cpp").get();
        src.delete();
        TestFileUtils.appendToFile(
                src,
                "\n"
                        + "#include <jni.h>\n"
                        + "#include <string>\n"
                        + "\n"
                        + "extern \"C\"\n"
                        + "jstring\n"
                        + "Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz) {\n"
                        + "    std::string greeting = \"hello world!\";\n"
                        + "    return env->NewStringUTF(greeting.c_str());\n"
                        + "}\n"
                        + "\n");
        project.execute("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/mips/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi/libhello-jni.so");
        assertThat(apk).contains("lib/armeabi-v7a/libhello-jni.so");

        if (stl.endsWith("shared")) {
            assertThat(apk).contains("lib/x86/lib" + stl + ".so");
            assertThat(apk).contains("lib/mips/lib" + stl + ".so");
            assertThat(apk).contains("lib/armeabi/lib" + stl + ".so");
            assertThat(apk).contains("lib/armeabi-v7a/lib" + stl + ".so");
        }


    }
}
