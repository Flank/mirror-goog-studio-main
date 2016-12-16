package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Simple Jack test for a few test project.
 */
public class JackSmokeTest {

    @Rule
    public Adb adb = new Adb();

    @ClassRule
    public static GradleTestProject sBasic = GradleTestProject.builder().withName("basic")
            .fromTestProject("basic").create();

    @ClassRule
    public static GradleTestProject sMinify = GradleTestProject.builder().withName("minify")
            .fromTestProject("minify").create();

    @ClassRule
    public static GradleTestProject sMultiDex = GradleTestProject.builder().withName("multiDex")
            .fromTestProject("multiDex").create();

    private static final List<String> JACK_OPTIONS = ImmutableList
            .of("-Pcom.android.build.gradle.integratonTest.useJack=true",
                    "-PCUSTOM_BUILDTOOLS=" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION);

    @AfterClass
    public static void cleanUp() {
        sBasic = null;
        sMinify = null;
        sMultiDex = null;
    }

    @Test
    public void assembleBasicDebug() throws Exception {
        GradleBuildResult result = sBasic
                .executor().withArguments(JACK_OPTIONS)
                .run("clean", "assembleDebug", "assembleDebugAndroidTest");
        assertThat(result.getTask(":transformJackWithJackDexerForDebug")).wasExecuted();

        assertThat(sBasic.getApk("debug")).contains("classes.dex");
    }

    @Test
    public void assembleMinifyDebug() {
        GradleBuildResult result = sMinify.executor().withArguments(JACK_OPTIONS)
                .run("clean", "assembleDebug", "assembleDebugAndroidTest");
        assertThat(result.getTask(":transformJackWithJackDexerForDebug")).wasExecuted();
    }

    @Test
    public void assembleMultiDexDebug() {
        GradleBuildResult result = sMultiDex.executor().withArguments(JACK_OPTIONS)
                .run("clean", "assembleIcsDebugAndroidTest", "assembleDebug",
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
        sBasic.executeConnectedCheck(JACK_OPTIONS);
    }

    @Test
    @Category(DeviceTests.class)
    public void multiDexConnectedCheck() throws IOException {
        sMultiDex.execute(JACK_OPTIONS, "assembleDebug",
                "assembleIcsDebugAndroidTest",
                "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        sMultiDex.execute(JACK_OPTIONS, "connectedCheck");
    }

    @Test
    public void minifyUnitTestsWithJavac() {
        sMinify.execute("testMinified");
    }

    @Test
    public void minifyUnitTestsWithJack() {
        sMinify.execute(JACK_OPTIONS, "clean", "testMinified");

        // Make sure javac was run.
        assertThat(sMinify.file("build/intermediates/classes/minified")).exists();

        // Make sure jack was not run.
        assertThat(sMinify.file("build/intermediates/transforms/preDexJackRuntimeLibraries")).doesNotExist();
    }
}
