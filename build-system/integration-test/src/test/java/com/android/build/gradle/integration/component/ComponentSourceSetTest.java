package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for different configuration of source sets. */
public class ComponentSourceSetTest {

    public static AndroidTestApp app = new HelloWorldJniApp();

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(app)
                    .useExperimentalGradleVersion(true)
                    .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.application\"\n"
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
                        + "            create(\"flavor1\") {\n"
                        + "                dimension \"foo\"\n"
                        + "            }\n"
                        + "            create(\"flavor2\") {\n"
                        + "                dimension \"foo\"\n"
                        + "            }\n"
                        + "            create(\"flavor3\") {\n"
                        + "                dimension \"foo\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "        sources {\n"
                        + "            main {\n"
                        + "                manifest {\n"
                        + "                    source {\n"
                        + "                        srcDir 'src'\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                jni {\n"
                        + "                    source {\n"
                        + "                        exclude \"**/fail.c\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "            flavor3 {\n"
                        + "                jni {\n"
                        + "                    source {\n"
                        + "                        srcDir 'src/flavor1/jni'\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.file("src/main/jni").mkdirs();
        TestFileUtils.appendToFile(
                project.file("src/main/jni/fail.c"),
                "\nUn-compilable file to test exclude source works.\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void defaultBuildTypeSourceDirectory() throws IOException, InterruptedException {
        project.execute("assembleFlavor2Release");
        Apk apk = project.getApk(GradleTestProject.ApkType.RELEASE, "flavor2");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
    }

    @Test
    public void defaultProductFlavorSourceDirectory() throws IOException, InterruptedException {
        project.execute("assembleFlavor1Debug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "flavor1");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
    }

    @Test
    public void defaultVariantSourceDirectory() throws IOException, InterruptedException {
        project.execute("assembleFlavor2Debug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "flavor2");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
    }

    @Test
    public void nonDefaultSourceDirectory() throws IOException, InterruptedException {
        project.execute("assembleFlavor3Debug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "flavor3");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
    }
}
