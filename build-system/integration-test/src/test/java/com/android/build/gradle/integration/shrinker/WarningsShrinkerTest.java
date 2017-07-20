package com.android.build.gradle.integration.shrinker;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for -dontwarn handling */
public class WarningsShrinkerTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    private Path rules;
    private Path activity;
    private int changeCounter;

    @Before
    public void enableShrinking() throws Exception {
        rules = project.getTestDir().toPath().resolve("proguard-rules.pro");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  buildTypes.debug {\n"
                        + "    minifyEnabled true\n"
                        + "    useProguard false\n"
                        + "    proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'\n"
                        + "  }\n"
                        + "}\n");

        Files.write(rules, ImmutableList.of("# Empty rules for now\n"));
    }

    @Before
    public void addGuavaDep() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" + "dependencies {\n" + "  compile 'com.google.guava:guava:18.0'\n" + "}\n");
    }

    @Before
    public void prepareForChanges() throws Exception {
        activity =
                project.getTestDir()
                        .toPath()
                        .resolve("src/main/java/com/example/helloworld/HelloWorld.java");

        TestFileUtils.addMethod(
                activity.toFile(),
                "\n"
                        + "@Override\n"
                        + "protected void onStop() {\n"
                        + "  android.util.Log.i(\"MainActivity\", \"CHANGE0\");\n"
                        + "  super.onStop();\n"
                        + "}");
    }

    @Test
    public void warningsStopBuild() throws Exception {
        project.executeExpectingFailure("assembleDebug");

        String output = project.getBuildResult().getStdout();
        assertThat(output).contains("references unknown");
        assertThat(output).contains("Unsafe");
        assertThat(output).contains("Nullable");
        assertThat(output).contains("com/google/common/cache");

        changeCode();

        project.executeExpectingFailure("assembleDebug");

        output = project.getBuildResult().getStdout();
        assertThat(output).contains("references unknown");
        assertThat(output).contains("Unsafe");
        assertThat(output).contains("Nullable");
        assertThat(output).contains("com/google/common/cache");
    }

    @Test
    public void dontwarnAppliesOnlyToRelevantClasses() throws Exception {
        TestFileUtils.appendToFile(rules.toFile(), "-dontwarn sun.misc.Unsafe\n");

        project.executeExpectingFailure("assembleDebug");

        String output = project.getBuildResult().getStdout();
        assertThat(output).contains("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).contains("Nullable");
        assertThat(output).contains("com/google/common/cache");
    }

    @Test
    public void dontwarnWithoutArguments() throws Exception {
        TestFileUtils.appendToFile(rules.toFile(), "-dontwarn\n");
        project.execute("assembleDebug");

        String output = project.getBuildResult().getStdout();
        assertThat(output).doesNotContain("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).doesNotContain("Nullable");
        assertThat(output).doesNotContain("com/google/common/cache");

        changeCode();

        project.execute("assembleDebug");

        output = project.getBuildResult().getStdout();
        assertThat(output).doesNotContain("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).doesNotContain("Nullable");
        assertThat(output).doesNotContain("com/google/common/cache");
    }

    @Test
    public void dontwarnOnCaller() throws Exception {
        TestFileUtils.appendToFile(rules.toFile(), "-dontwarn com.google.common.**\n");
        project.execute("assembleDebug");

        String output = project.getBuildResult().getStdout();
        assertThat(output).doesNotContain("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).doesNotContain("Nullable");
        assertThat(output).doesNotContain("com/google/common/cache");

        changeCode();

        project.execute("assembleDebug");

        output = project.getBuildResult().getStdout();
        assertThat(output).doesNotContain("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).doesNotContain("Nullable");
        assertThat(output).doesNotContain("com/google/common/cache");
    }

    @Test
    public void dontwarnOnCallee() throws Exception {
        TestFileUtils.appendToFile(
                rules.toFile(), "-dontwarn sun.misc.Unsafe\n -dontwarn javax.annotation.**\n");

        project.execute("assembleDebug");

        String output = project.getBuildResult().getStdout();
        assertThat(output).doesNotContain("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).doesNotContain("Nullable");
        assertThat(output).doesNotContain("com/google/common/cache");

        changeCode();

        project.execute("assembleDebug");

        output = project.getBuildResult().getStdout();
        assertThat(output).doesNotContain("references unknown");
        assertThat(output).doesNotContain("Unsafe");
        assertThat(output).doesNotContain("Nullable");
        assertThat(output).doesNotContain("com/google/common/cache");
    }

    @Test
    public void parserErrorsAreProperlyReported() throws Exception {
        TestFileUtils.appendToFile(rules.toFile(), "-foo\n");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertThat(result.getFailureMessage()).contains("'-foo' expecting EOF");
    }

    private void changeCode() throws IOException {
        changeCounter++;
        TestFileUtils.searchAndReplace(activity, "CHANGE\\d+", "CHANGE" + changeCounter);
    }
}
