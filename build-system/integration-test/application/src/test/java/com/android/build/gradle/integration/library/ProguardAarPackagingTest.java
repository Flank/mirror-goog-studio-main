package com.android.build.gradle.integration.library;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Integration test to check that libraries included directly as jar files are correctly handled
 * when using R8.
 */
public class ProguardAarPackagingTest {

    public static GradleProject testApp = HelloWorldApp.noBuildFile();
    public static GradleProject libraryInJar = new EmptyAndroidTestApp();

    static {
        TestSourceFile oldHelloWorld = testApp.getFileByName("HelloWorld.java");
        testApp.replaceFile(
                new TestSourceFile(
                        oldHelloWorld.getPath(),
                        "package com.example.helloworld;\n"
                                + "\n"
                                + "import com.example.libinjar.LibInJar;\n"
                                + "\n"
                                + "import android.app.Activity;\n"
                                + "import android.os.Bundle;\n"
                                + "\n"
                                + "public class HelloWorld extends Activity {\n"
                                + "    /** Called when the activity is first created. */\n"
                                + "    @Override\n"
                                + "    public void onCreate(Bundle savedInstanceState) {\n"
                                + "        super.onCreate(savedInstanceState);\n"
                                + "        setContentView(R.layout.main);\n"
                                + "        LibInJar.method();\n"
                                + "    }\n"
                                + "}\n"));

        testApp.addFile(new TestSourceFile("config.pro", "-keeppackagenames **"));

        // Create simple library jar.
        libraryInJar.addFile(
                new TestSourceFile(
                        "src/main/java/com/example/libinjar",
                        "LibInJar.java",
                        "package com.example.libinjar;\n"
                                + "\n"
                                + "public class LibInJar {\n"
                                + "    public static void method() {\n"
                                + "        throw new UnsupportedOperationException(\"Not implemented\");\n"
                                + "    }\n"
                                + "}\n"));
    }

    @ClassRule
    public static GradleTestProject androidProject =
            GradleTestProject.builder().withName("mainProject").fromTestApp(testApp).create();

    @ClassRule
    public static GradleTestProject libraryInJarProject =
            GradleTestProject.builder().withName("libInJar").fromTestApp(libraryInJar).create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        // Create android test application
        TestFileUtils.appendToFile(
                androidProject.getBuildFile(),
                "apply plugin: 'com.android.library'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api fileTree(dir: 'libs', include: '*.jar')\n"
                        + "}\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        release {\n"
                        + "            minifyEnabled true\n"
                        + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'config.pro'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(
                libraryInJarProject.getBuildFile(),
                "\n"
                        + "apply plugin: 'java'\n"
                        + "java.sourceCompatibility = JavaVersion.VERSION_1_8\n"
                        + "java.targetCompatibility = JavaVersion.VERSION_1_8\n");
        libraryInJarProject.execute("assemble");

        // Copy the generated jar into the android project.
        FileUtils.mkdirs(androidProject.file("libs"));
        String libInJarName =
                Joiner.on(File.separatorChar)
                        .join(
                                "build",
                                "libs",
                                libraryInJarProject.getName() + SdkConstants.DOT_JAR);
        FileUtils.copyFile(
                libraryInJarProject.file(libInJarName), androidProject.file("libs/libinjar.jar"));
    }

    @AfterClass
    public static void cleanUp() {
        androidProject = null;
        libraryInJarProject = null;
    }

    @Test
    public void checkDebugAarPackaging() throws Exception {
        androidProject.executor().run("assembleDebug");

        androidProject.testAar(
                "debug",
                it -> {
                    // check that the classes from the local jars are still in a local jar
                    it.containsSecondaryClass("Lcom/example/libinjar/LibInJar;");

                    // check that it's not in the main class file.
                    it.doesNotContainMainClass("Lcom/example/libinjar/LibInJar;");
                });
    }

    @Test
    public void checkReleaseAarPackaging() throws Exception {
        androidProject.executor().run("assembleRelease");

        androidProject.testAar(
                "release",
                it -> {
                    // check that the classes from the local jars are in the main class file
                    it.containsMainClass("Lcom/example/libinjar/a;");

                    // check that it's not in any local jar
                    it.doesNotContainSecondaryClass("Lcom/example/libinjar/LibInJar;");
                });
    }
}
