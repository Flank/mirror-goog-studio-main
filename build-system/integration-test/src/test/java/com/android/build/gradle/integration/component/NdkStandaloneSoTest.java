package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for standalone NDK plugin and native dependencies.
 *
 * <p>Test project consists of an app project that depends on an NDK project that depends on another
 * NDK project.
 */
public class NdkStandaloneSoTest {

    private static MultiModuleTestProject base =
            new MultiModuleTestProject(
                    ImmutableMap.of(
                            "app", new HelloWorldJniApp(),
                            "lib1", new EmptyAndroidTestApp(),
                            "lib2", new EmptyAndroidTestApp()));

    static {
        initialize();
    }

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(base)
                    .useExperimentalGradleVersion(true)
                    .create();

    private static void initialize() {
        AndroidTestApp app = (HelloWorldJniApp) base.getSubproject("app");
        app.removeFile(app.getFile("hello-jni.c"));
        app.addFile(
                new TestSourceFile(
                        "",
                        "build.gradle",
                        "\n"
                                + "apply plugin: \"com.android.model.application\"\n"
                                + "\n"
                                + "model {\n"
                                + "    repositories {\n"
                                + "        libs(PrebuiltLibraries) {\n"
                                + "            prebuilt {\n"
                                + "                binaries.withType(SharedLibraryBinary) {\n"
                                + "                    sharedLibraryFile = file(\"prebuilt.so\")\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "    android {\n"
                                + "        compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "        buildToolsVersion \""
                                + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                                + "\"\n"
                                + "        sources {\n"
                                + "            main {\n"
                                + "                jniLibs {\n"
                                + "                    dependencies {\n"
                                + "                        project \":lib1\" buildType \"debug\"\n"
                                + "                        library \"prebuilt\"\n"
                                + "                    }\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"));
        // An empty .so just to check if it can be packaged
        app.addFile(new TestSourceFile("", "prebuilt.so", ""));

        AndroidTestApp lib1 = (AndroidTestApp) base.getSubproject("lib1");
        lib1.addFile(
                new TestSourceFile(
                        "src/main/jni",
                        "hello-jni.c",
                        "\n"
                                + "#include <string.h>\n"
                                + "#include <jni.h>\n"
                                + "\n"
                                + "jstring\n"
                                + "Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)\n"
                                + "{\n"
                                + "    return (*env)->NewStringUTF(env, getString());\n"
                                + "}\n"));

        lib1.addFile(
                new TestSourceFile(
                        "",
                        "build.gradle",
                        "\n"
                                + "apply plugin: \"com.android.model.native\"\n"
                                + "\n"
                                + "model {\n"
                                + "    android {\n"
                                + "        compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "        ndk {\n"
                                + "            moduleName \"hello-jni\"\n"
                                + "        }\n"
                                + "        sources {\n"
                                + "            main {\n"
                                + "                jni {\n"
                                + "                    dependencies {\n"
                                + "                        project \":lib2\"\n"
                                + "                    }\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"));

        AndroidTestApp lib2 = (AndroidTestApp) base.getSubproject("lib2");
        lib2.addFile(
                new TestSourceFile(
                        "src/main/headers/",
                        "hello.h",
                        "\n"
                                + "#ifndef HELLO_H\n"
                                + "#define HELLO_H\n"
                                + "\n"
                                + "char* getString();\n"
                                + "\n"
                                + "#endif\n"));
        lib2.addFile(
                new TestSourceFile(
                        "src/main/jni/",
                        "hello.c",
                        "\n" + "char* getString() {\n" + "    return \"hello world!\";\n" + "}\n"));
        lib2.addFile(
                new TestSourceFile(
                        "",
                        "build.gradle",
                        "\n"
                                + "apply plugin: \"com.android.model.native\"\n"
                                + "\n"
                                + "model {\n"
                                + "    android {\n"
                                + "        compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "        ndk {\n"
                                + "            moduleName \"hello-jni\"\n"
                                + "        }\n"
                                + "        sources {\n"
                                + "            main {\n"
                                + "                jni {\n"
                                + "                    exportedHeaders {\n"
                                + "                        srcDir \"src/main/headers\"\n"
                                + "                    }\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"));
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        base = null;
    }

    @Test
    public void checkStandaloneLibProperlyCreatesLibrary()
            throws IOException, InterruptedException {
        project.execute("clean", ":lib1:assembleDebug");

        GradleTestProject lib = project.getSubproject("lib1");
        assertThat(lib.file("build/outputs/native/debug/lib/x86/libhello-jni.so")).exists();
    }

    @Test
    public void checkAppContainsCompiledSo() throws IOException, InterruptedException {
        project.execute("clean", ":app:assembleRelease");

        GradleTestProject lib1 = project.getSubproject("lib1");
        assertThat(lib1.file("build/intermediates/binaries/debug/obj/x86/libhello-jni.so"))
                .exists();

        // Check that release lib is not compiled.
        assertThat(lib1.file("build/intermediates/binaries/release/obj/x86/libhello-jni.so"))
                .doesNotExist();

        Apk apk = project.getSubproject("app").getApk("release", "unsigned");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/x86/prebuilt.so");
    }
}
