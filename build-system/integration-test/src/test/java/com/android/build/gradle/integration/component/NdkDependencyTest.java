package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeSettings;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Test for dependencies on NDK projects. */
public class NdkDependencyTest {

    private static final String[] ABIS =
            new String[] {"armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64"};
    private static AndroidTestApp prebuilt = new EmptyAndroidTestApp();
    private static MultiModuleTestProject base =
            new MultiModuleTestProject(
                    ImmutableMap.of(
                            "app",
                            new HelloWorldJniApp(),
                            "lib1",
                            new EmptyAndroidTestApp(),
                            "lib2",
                            new EmptyAndroidTestApp()));

    static {
        try {
            initialize();
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("Initialization failed", e);
        }
    }

    @ClassRule
    public static GradleTestProject prebuiltProject =
            GradleTestProject.builder()
                    .withName("prebuilt")
                    .fromTestApp(prebuilt)
                    .useExperimentalGradleVersion(true)
                    .create();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(base)
                    .useExperimentalGradleVersion(true)
                    .create();

    public static void initialize() throws IOException, InterruptedException {
        AndroidTestApp app = (HelloWorldJniApp) base.getSubproject("app");

        app.removeFile(app.getFile("hello-jni.c"));
        app.addFile(
                new TestSourceFile(
                        "src/main/jni",
                        "hello-jni.cpp",
                        "\n"
                                + "#include <string.h>\n"
                                + "#include <jni.h>\n"
                                + "#include \"lib1.h\"\n"
                                + "\n"
                                + "extern \"C\"\n"
                                + "jstring\n"
                                + "Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)\n"
                                + "{\n"
                                + "    return env->NewStringUTF(getLib1String());\n"
                                + "}\n"));

        app.addFile(
                new TestSourceFile(
                        "",
                        "build.gradle",
                        "\n"
                                + "apply plugin: \"com.android.model.application\"\n"
                                + "\n"
                                + "model {\n"
                                + "    repositories {\n"
                                + "        prebuilt(PrebuiltLibraries) {\n"
                                + "            prebuilt {\n"
                                + "                binaries.withType(SharedLibraryBinary) {\n"
                                + "                    sharedLibraryFile = project.file(\"../../../prebuilt/build/outputs/native/debug/lib/${targetPlatform.getName()}/libprebuilt.so\")\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "    android {\n"
                                + "        compileSdkVersion = "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + '\n'
                                + "        buildToolsVersion = \""
                                + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                                + "\"\n"
                                + "    }\n"
                                + "    android.ndk {\n"
                                + "        moduleName = \"hello-jni\"\n"
                                + "    }\n"
                                + "    android.sources {\n"
                                + "        main {\n"
                                + "            jni {\n"
                                + "                dependencies {\n"
                                + "                    project \":lib1\"\n"
                                + "                    library \"prebuilt\"\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"));

        AndroidTestApp lib1 = (AndroidTestApp) base.getSubproject("lib1");
        lib1.addFile(
                new TestSourceFile(
                        "src/main/headers/",
                        "lib1.h",
                        "\n"
                                + "#ifndef INCLUDED_LIB1_H\n"
                                + "#define INCLUDED_LIB1_H\n"
                                + "\n"
                                + "char* getLib1String();\n"
                                + "\n"
                                + "#endif\n"));
        lib1.addFile(
                new TestSourceFile(
                        "src/main/jni/",
                        "lib1.cpp",
                        "\n"
                                + "#include \"lib1.h\"\n"
                                + "#include \"lib2.h\"\n"
                                + "\n"
                                + "char* getLib1String() {\n"
                                + "    return getLib2String();\n"
                                + "}\n"));
        lib1.addFile(
                new TestSourceFile(
                        "",
                        "build.gradle",
                        "\n"
                                + "apply plugin: \"com.android.model.native\"\n"
                                + "\n"
                                + "\n"
                                + "model {\n"
                                + "    android {\n"
                                + "        compileSdkVersion = "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "    }\n"
                                + "    android.ndk {\n"
                                + "        moduleName = \"getstring1\"\n"
                                + "    }\n"
                                + "    android.sources {\n"
                                + "        main {\n"
                                + "            jni {\n"
                                + "                dependencies {\n"
                                + "                    project \":lib2\"\n"
                                + "                }\n"
                                + "                exportedHeaders {\n"
                                + "                    srcDir \"src/main/headers\"\n"
                                + "                }\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"));

        AndroidTestApp lib2 = (AndroidTestApp) base.getSubproject("lib2");
        lib2.addFile(
                new TestSourceFile(
                        "src/main/headers/",
                        "lib2.h",
                        "\n"
                                + "#ifndef INCLUDED_LIB2_H\n"
                                + "#define INCLUDED_LIB2_H\n"
                                + "\n"
                                + "char* getLib2String();\n"
                                + "\n"
                                + "#endif\n"));
        // Add c++ file that uses function from the STL.
        lib2.addFile(
                new TestSourceFile(
                        "src/main/jni/",
                        "lib2.cpp",
                        "\n"
                                + "#include \"lib2.h\"\n"
                                + "#include <algorithm>\n"
                                + "#include <cstring>\n"
                                + "#include <cctype>\n"
                                + "\n"
                                + "char* getLib2String() {\n"
                                + "    char* greeting = new char[32];\n"
                                + "    std::strcpy(greeting, \"HELLO WORLD!\");\n"
                                + "    std::transform(greeting, greeting + strlen(greeting), greeting, std::tolower);\n"
                                + "    return greeting;  // memory leak if greeting is not deallocated, but doesn't matter.\n"
                                + "}\n"));
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
                                + "            moduleName \"getstring2\"\n"
                                + "            toolchain \"clang\"\n"
                                + "            stl \"stlport_shared\"\n"
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

        // Subproject for creating prebuilt libraries.
        prebuilt.addFile(
                new TestSourceFile(
                        "src/main/jni/",
                        "prebuilt.c",
                        "\n"
                                + "char* getPrebuiltString() {\n"
                                + "    return \"prebuilt\";\n"
                                + "}\n"));
        prebuilt.addFile(
                new TestSourceFile(
                        "",
                        "build.gradle",
                        "\n"
                                + "apply plugin: \"com.android.model.native\"\n"
                                + "\n"
                                + "model {\n"
                                + "    android {\n"
                                + "        compileSdkVersion = "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "    }\n"
                                + "    android.ndk {\n"
                                + "        moduleName = \"prebuilt\"\n"
                                + "    }\n"
                                + "}\n"));
    }

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        // Create prebuilt libraries.
        prebuiltProject.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        base = null;
        prebuilt = null;
        prebuiltProject = null;
    }

    @Test
    public void checkAppContainsCompiledSo() throws IOException, InterruptedException {
        // Ensure that the prebuilt libraries do not need to be present until it is used in the
        // compile task.
        prebuiltProject.executor().run("clean");
        project.executor().run("clean");

        prebuiltProject.executor().run("assembleDebug");

        project.execute(":app:assembleDebug");
        GetAndroidModelAction.ModelContainer<AndroidProject> modelContainer =
                project.model().getMulti();
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        GradleTestProject app = project.getSubproject("app");
        GradleTestProject lib1 = project.getSubproject("lib1");
        GradleTestProject lib2 = project.getSubproject("lib2");

        assertThat(models).containsKey(":app");

        AndroidProject model = models.get(":app");

        final Apk apk = project.getSubproject("app").getApk("debug");
        for (String abi : ABIS) {
            NativeLibrary libModel = findNativeLibraryByAbi(model, "debug", abi);
            assertThat(libModel).isNotNull();
            assertThat(libModel.getDebuggableLibraryFolders())
                    .containsAllOf(
                            app.file("build/intermediates/binaries/debug/obj/" + abi),
                            lib1.file("build/intermediates/binaries/debug/obj/" + abi),
                            lib2.file("build/intermediates/binaries/debug/obj/" + abi));

            List<String> expectedLibs =
                    new ArrayList<String>(
                            Arrays.asList(
                                    "libhello-jni.so",
                                    "libstlport_shared.so",
                                    "libgetstring1.so",
                                    "libgetstring2.so",
                                    "libprebuilt.so"));
            for (String expectedLib : expectedLibs) {
                String path = (String) "lib/" + abi + "/" + expectedLib;
                assertThat(apk).contains(path);
                File lib = ZipHelper.extractFile(apk, path);
                assertThatNativeLib(lib).isStripped();
            }

        }

        Map<String, NativeAndroidProject> nativeModels =
                project.model().getMulti(NativeAndroidProject.class);
        for (NativeArtifact artifact : nativeModels.get(":app").getArtifacts()) {
            assertThat(
                            artifact.getRuntimeFiles()
                                    .stream()
                                    .map(File::getName)
                                    .collect(Collectors.toList()))
                    .containsExactly(
                            "libstlport_shared.so",
                            "libgetstring1.so",
                            "libgetstring2.so",
                            "libprebuilt.so");
            for (File runtimeFile : artifact.getRuntimeFiles()) {
                assertThat(runtimeFile.getPath()).contains(artifact.getAbi());
            }

        }

    }

    @Test
    public void checkStaticLinkage() throws IOException, InterruptedException {
        GradleTestProject lib1 = project.getSubproject("lib1");
        lib1.getBuildFile().delete();
        TestFileUtils.appendToFile(
                lib1.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.native\"\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        ndk {\n"
                        + "            moduleName = \"getstring1\"\n"
                        + "        }\n"
                        + "        sources {\n"
                        + "            main {\n"
                        + "                jni {\n"
                        + "                    dependencies {\n"
                        + "                        project \":lib2\" linkage \"static\"\n"
                        + "                    }\n"
                        + "                    exportedHeaders {\n"
                        + "                        srcDir \"src/main/headers\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.executor().run("clean", ":app:assembleDebug");
        Map<String, NativeAndroidProject> models =
                project.model().getMulti(NativeAndroidProject.class);
        NativeAndroidProject model = models.get(":app");
        Apk apk = project.getSubproject("app").getApk("debug");
        for (String abi : ABIS) {
            assertThat(apk).contains("lib/" + abi + "/libhello-jni.so");
            assertThat(apk).contains("lib/" + abi + "/libstlport_shared.so");
            assertThat(apk).doesNotContain("lib/" + abi + "/libget-string.so");

            // Check that the static library is compiled, but not the shared library.
            GradleTestProject lib2 = project.getSubproject("lib2");
            assertThat(
                            lib2.file(
                                    "build/intermediates/binaries/debug/obj/"
                                            + abi
                                            + "/libgetstring2.a"))
                    .exists();
            assertThat(
                            lib2.file(
                                    "build/intermediates/binaries/debug/obj/"
                                            + abi
                                            + "/libgetstring2.so"))
                    .doesNotExist();
        }

        for (NativeArtifact artifact : model.getArtifacts()) {
            assertThat(
                            artifact.getRuntimeFiles()
                                    .stream()
                                    .map(File::getName)
                                    .collect(Collectors.toList()))
                    .containsExactly("libstlport_shared.so", "libgetstring1.so", "libprebuilt.so");
            for (File runtimeFile : artifact.getRuntimeFiles()) {
                assertThat(runtimeFile.getPath()).contains(artifact.getAbi());
            }

        }

        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings.getCompilerFlags())
                    .contains(
                            "-I"
                                    + lib1.file("src/main/headers")
                                            .getAbsolutePath()
                                            .replace("\\", "\\\\"));
        }

    }

    @Test
    public void checkUpdateInLibTriggersRebuild() throws IOException, InterruptedException {
        project.execute("clean", ":app:assembleDebug");
        GradleTestProject app = project.getSubproject("app");
        GradleTestProject lib1 = project.getSubproject("lib1");
        GradleTestProject lib2 = project.getSubproject("lib2");

        Apk apk = project.getSubproject("app").getApk("debug");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/x86/libstlport_shared.so");

        TestFileUtils.appendToFile(lib2.file("src/main/jni/lib2.cpp"), "void foo() {}");

        File appSo = app.file("build/intermediates/binaries/debug/obj/x86/libhello-jni.so");
        File lib1So = lib1.file("build/intermediates/binaries/debug/obj/x86/libgetstring1.so");
        File lib2So = lib2.file("build/intermediates/binaries/debug/obj/x86/libgetstring2.so");

        long appModifiedTime = appSo.lastModified();
        long lib1ModifiedTime = lib1So.lastModified();
        long lib2ModifiedTime = lib2So.lastModified();

        project.execute(":app:assembleDebug");

        assertThat(lib2So).isNewerThan(lib2ModifiedTime);
        assertThat(lib1So).isNewerThan(lib1ModifiedTime);
        assertThat(appSo).isNewerThan(appModifiedTime);
    }

    @Test
    public void checkDependencyOrder() throws IOException, InterruptedException {
        GradleTestProject app = project.getSubproject("app");
        int numPrebuilts = 10;
        // Create a bunch of prebuilt libraries
        for (Abi abi : NdkHandler.getAbiList()) {
            File dir = app.file("prebuilt/" + abi.getName());
            FileUtils.mkdirs(dir);
            for (int i = 0; i < numPrebuilts; i++) {
                FileUtils.copyFile(
                        prebuiltProject.file(
                                "build/outputs/native/debug/lib/"
                                        + abi.getName()
                                        + "/libprebuilt.so"),
                        new File(dir, "libprebuilt" + i + ".so"));
            }
        }

        app.getBuildFile().delete();
        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.application\"\n"
                        + "\n"
                        + "model {\n"
                        + "    repositories {\n"
                        + "        prebuilt(PrebuiltLibraries) {\n");
        for (int i = 0; i < numPrebuilts; i++) {
            TestFileUtils.appendToFile(
                    app.getBuildFile(),
                    "\n"
                            + "            prebuilt"
                            + String.valueOf(i)
                            + " {\n"
                            + "                binaries.withType(SharedLibraryBinary) {\n"
                            + "                    sharedLibraryFile = project.file(\"prebuilt/${targetPlatform.getName()}/libprebuilt"
                            + String.valueOf(i)
                            + ".so\")\n"
                            + "                }\n"
                            + "            }\n");
        }

        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "\n"
                        + "        }\n"
                        + "    }\n"
                        + "    android {\n"
                        + "        compileSdkVersion = "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion = \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "    }\n"
                        + "    android.ndk {\n"
                        + "        moduleName = \"hello-jni\"\n"
                        + "    }\n"
                        + "    android.sources {\n"
                        + "        main {\n"
                        + "            jni {\n"
                        + "                dependencies {\n"
                        + "                    project \":lib1\"\n");
        for (int i = 0; i < numPrebuilts; i++) {
            TestFileUtils.appendToFile(
                    app.getBuildFile(),
                    "\n" + "                    library 'prebuilt" + String.valueOf(i) + "'\n");
        }

        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        project.executor().run(":app:assembleDebug");

        List<String> linkOptions = Files.readLines(
                app.file("build/tmp/linkHello-jniArm64-v8aDebugSharedLibrary/options.txt"),
                Charsets.UTF_8);

        List<String> libs =
                linkOptions
                        .stream()
                        .filter(s -> s.matches(".*libprebuilt\\d\\.so$"))
                        .map(s -> s.substring(s.lastIndexOf("libprebuilt")))
                        .collect(Collectors.toList());

        List<String> expected = new ArrayList<>(10);
        for (int i = 0; i < expected.size(); i++) {
            expected.add("libprebuilt" + i + ".so");
        }

        // Check prebuilt libraries are in the right order in the link command.
        assertThat(libs).containsAllIn(expected).inOrder();
    }

    private static NativeLibrary findNativeLibraryByAbi(
            AndroidProject model, String variantName, final String abi) {
        AndroidArtifact artifact =
                ModelHelper.getVariant(model.getVariants(), variantName).getMainArtifact();
        Collection<NativeLibrary> nativeLibraries =
                Preconditions.checkNotNull(artifact.getNativeLibraries());
        return nativeLibraries
                .stream()
                .filter(n -> n.getAbi().equals(abi))
                .findFirst()
                .orElse(null);
    }
}
