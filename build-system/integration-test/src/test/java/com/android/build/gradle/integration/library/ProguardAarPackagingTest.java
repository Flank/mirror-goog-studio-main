package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Aar;
import com.google.common.base.Joiner;
import groovy.transform.CompileStatic;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Integration test to check that libraries included directly as jar files are correctly handled
 * when using proguard.
 */
@CompileStatic
public class ProguardAarPackagingTest {

    public static AndroidTestApp testApp = HelloWorldApp.noBuildFile();
    public static AndroidTestApp libraryInJar = new EmptyAndroidTestApp();

    static {
        TestSourceFile oldHelloWorld = testApp.getFile("HelloWorld.java");
        testApp.removeFile(oldHelloWorld);
        testApp.addFile(
                new TestSourceFile(
                        oldHelloWorld.getParent(),
                        oldHelloWorld.getName(),
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

        testApp.addFile(new TestSourceFile("", "config.pro", "-keeppackagenames"));

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
                        + "    compile fileTree(dir: 'libs', include: '*.jar')\n"
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

        TestFileUtils.appendToFile(libraryInJarProject.getBuildFile(), "apply plugin: 'java'");
        libraryInJarProject.execute("assemble");

        // Copy the generated jar into the android project.
        androidProject.file("libs").mkdirs();
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
    public void checkDebugAarPackaging()
            throws IOException, InterruptedException, ProcessException {
        androidProject.execute("assembleDebug");

        Aar debug = androidProject.getAar("debug");

        // check that the classes from the local jars are still in a local jar
        assertThat(debug).containsSecondaryClass("Lcom/example/libinjar/LibInJar;");

        // check that it's not in the main class file.
        assertThat(debug).doesNotContainMainClass("Lcom/example/libinjar/LibInJar;");
    }

    @Test
    public void checkReleaseAarPackaging()
            throws IOException, InterruptedException, ProcessException {
        androidProject.execute("assembleRelease");

        Aar release = androidProject.getAar("release");

        // check that the classes from the local jars are in the main class file
        assertThat(release).containsMainClass("Lcom/example/libinjar/a;");

        // check that it's not in any local jar
        assertThat(release).doesNotContainSecondaryClass("Lcom/example/libinjar/LibInJar;");
    }
}
