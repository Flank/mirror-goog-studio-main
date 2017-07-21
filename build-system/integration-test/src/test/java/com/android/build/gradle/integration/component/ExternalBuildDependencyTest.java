package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for native dependencies */
public class ExternalBuildDependencyTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(
                            new MultiModuleTestProject(
                                    ImmutableMap.of(
                                            "app",
                                            new HelloWorldJniApp(),
                                            "lib",
                                            new EmptyAndroidTestApp())))
                    .useExperimentalGradleVersion(true)
                    .create();

    @Before
    public void setUp() throws IOException {
        GradleTestProject app = project.getSubproject("app");
        app.file("hello-jni.c").delete();
        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.application\"\n"
                        + "\n"
                        + "model {\n"
                        + "  android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + '\n'
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    sources {\n"
                        + "      main {\n"
                        + "        jniLibs {\n"
                        + "          dependencies {\n"
                        + "            project \":lib\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");

        GradleTestProject lib = project.getSubproject("lib");
        Path helloJni = lib.getTestDir().toPath().resolve("src/main/jni/hello-jni.c");
        Files.createDirectories(helloJni.getParent());
        String helloJniContent =
                "\n"
                        + "#include <string.h>\n"
                        + "#include <jni.h>\n"
                        + "\n"
                        + "jstring\n"
                        + "Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)\n"
                        + "{\n"
                        + "    return (*env)->NewStringUTF(env, \"hello world!\");\n"
                        + "}\n";
        Files.write(helloJni, helloJniContent.getBytes());

        Path androidMk = lib.getTestDir().toPath().resolve("Android.mk");
        String androidMkContent =
                "\n"
                        + "LOCAL_PATH := $(call my-dir)\n"
                        + "include $(CLEAR_VARS)\n"
                        + "LOCAL_MODULE := hello-jni\n"
                        + "LOCAL_SRC_FILES := src/main/jni/hello-jni.c\n"
                        + "include $(BUILD_SHARED_LIBRARY)\n";
        Files.write(androidMk, androidMkContent.getBytes());
    }

    @Test
    public void checkStandaloneLibProperlyCreatesLibrary()
            throws IOException, InterruptedException {
        // File a clang compiler.  Doesn't matter which one.
        boolean isWindows = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS;
        final File compiler =
                new File(
                        GradleTestProject.ANDROID_NDK_HOME,
                        "ndk-build" + (isWindows ? ".cmd" : ""));
        GradleTestProject lib = project.getSubproject("lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.external\"\n"
                        + "\n"
                        + "model {\n"
                        + "    nativeBuildConfig {\n"
                        + "        libraries {\n"
                        + "            create(\"foo\") {\n"
                        + "                buildCommand \"\\\""
                        + FileUtils.toSystemIndependentPath(compiler.getPath())
                        + "\\\" \" +\n"
                        + "                    \"APP_BUILD_SCRIPT=Android.mk \" +\n"
                        + "                    \"NDK_PROJECT_PATH=null \" +\n"
                        + "                    \"NDK_OUT=build/intermediate \" +\n"
                        + "                    \"NDK_LIBS_OUT=build/output \" +\n"
                        + "                    \"APP_ABI=x86\"\n"
                        + "                toolchain \"gcc\"\n"
                        + "                abi \"x86\"\n"
                        + "                output file(\"build/output/x86/libhello-jni.so\")\n"
                        + "                files {\n"
                        + "                    create() {\n"
                        + "                        src \"src/main/jni/hello-jni.c\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "\n"
                        + "            }\n"
                        + "        }\n"
                        + "        toolchains {\n"
                        + "            create(\"gcc\") {\n"
                        + "                // Needs to be CCompilerExecutable instead of the more correct cCompilerExecutable,\n"
                        + "                // because of a stupid bug with Gradle.\n"
                        + "                CCompilerExecutable = \""
                        + FileUtils.toSystemIndependentPath(compiler.getPath())
                        + "\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.execute("clean", ":app:assembleDebug");

        Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("lib/x86/libhello-jni.so");
    }
}
