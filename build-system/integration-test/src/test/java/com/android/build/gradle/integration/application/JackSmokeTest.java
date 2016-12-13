package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Simple Jack test for a few test project. */
public class JackSmokeTest {

    @ClassRule
    public static GradleTestProject sBasic =
            GradleTestProject.builder().fromTestProject("basic").withJack(true).create();

    @ClassRule
    public static GradleTestProject sMinify =
            GradleTestProject.builder().fromTestProject("minify").withJack(true).create();

    @ClassRule
    public static GradleTestProject sMultiDex =
            GradleTestProject.builder().fromTestProject("multiDex").withJack(true).create();

    @AfterClass
    public static void cleanUp() {
        sBasic = null;
        sMinify = null;
        sMultiDex = null;
    }

    @Rule public Adb adb = new Adb();

    @Test
    public void assembleBasicDebug() throws Exception {
        GradleBuildResult result =
                sBasic.executor().run("clean", "assembleDebug", "assembleDebugAndroidTest");
        assertThat(result.getTask(":transformJackWithJackDexerForDebug")).wasExecuted();

        assertThat(sBasic.getApk("debug")).contains("classes.dex");
    }

    @Test
    public void assembleMinifyDebug() {
        GradleBuildResult result =
                sMinify.executor().run("clean", "assembleDebug", "assembleDebugAndroidTest");
        assertThat(result.getTask(":transformJackWithJackDexerForDebug")).wasExecuted();
    }

    @Test
    public void assembleMultiDexDebug() {
        GradleBuildResult result =
                sMultiDex
                        .executor()
                        .run(
                                "clean",
                                "assembleIcsDebugAndroidTest",
                                "assembleDebug",
                                "assembleLollipopDebugAndroidTest");
        assertThat(result.getTask(":transformJackAndJavaSourcesWithJackCompileForIcsDebug"))
                .wasExecuted();
        assertThat(result.getTask(":transformJackAndJavaSourcesWithJackCompileForLollipopDebug"))
                .wasExecuted();
        assertThat(result.getTask(":transformJackWithJackDexerForLollipopDebug")).wasNotExecuted();
        assertThat(result.getTask(":transformJackWithJackDexerForLollipopDebugAndroidTest"))
                .wasNotExecuted();
    }

    @Test
    @Category(DeviceTests.class)
    public void basicConnectedCheck() {
        sBasic.executeConnectedCheck();
    }

    @Test
    @Category(DeviceTests.class)
    public void multiDexConnectedCheck() throws IOException {
        sMultiDex.execute(
                "assembleDebug", "assembleIcsDebugAndroidTest", "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        sMultiDex.execute("connectedCheck");
    }

    @Test
    public void minifyUnitTestsWithJavac() {
        sMinify.execute("testMinified");
    }

    @Test
    public void minifyUnitTestsWithJack() {
        sMinify.execute("clean", "testMinified");

        // Make sure javac was run.
        assertThat(sMinify.file("build/intermediates/classes/minified")).exists();

        // Make sure jack was not run.
        assertThat(sMinify.file("build/intermediates/transforms/preDexJackRuntimeLibraries"))
                .doesNotExist();
    }
}
