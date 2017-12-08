/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.performance;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilder;
import com.android.build.gradle.integration.common.fixture.ProfileCapturer;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.utils.ExceptionRunnable;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class MediumGradleProjectPerformanceMatrixTest {

    @Rule public final GradleTestProject project;
    @NonNull private final ProjectScenario projectScenario;
    @NonNull private BenchmarkRecorder recorder;

    public MediumGradleProjectPerformanceMatrixTest(@NonNull ProjectScenario projectScenario)
            throws IOException {
        this.projectScenario = projectScenario;
        project =
                GradleTestProject.builder()
                        .fromExternalProject("gradle-perf-android-medium")
                        .withHeap("1536M")
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static ProjectScenario[] getParameters() {
        return new ProjectScenario[] {
            ProjectScenario.LEGACY_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_LEGACY_MULTIDEX,
            ProjectScenario.NATIVE_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX,
            ProjectScenario.D8_NATIVE_MULTIDEX,
            ProjectScenario.D8_LEGACY_MULTIDEX,
        };
    }

    @Before
    public void initializeProject() throws Exception {
        PerformanceTestProjects.initializeWordpress(project);

        // Replace files direct access to file collection lazy access, since variants resolved
        // dependencies cannot be accessed in configuration time
        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "files\\(variant\\.javaCompile\\.classpath\\.files, android\\.getBootClasspath\\(\\)\\)",
                "files{[variant.javaCompile.classpath.files, android.getBootClasspath()]}");

        if (projectScenario != ProjectScenario.DEX_OUT_OF_PROCESS) {
            TestFileUtils.searchAndReplace(
                    project.file("WordPress/build.gradle"), "javaMaxHeapSize = \"6g\"", "");
        }

        switch (projectScenario) {
            case NATIVE_MULTIDEX:
            case DEX_ARCHIVE_NATIVE_MULTIDEX:
            case D8_NATIVE_MULTIDEX:
                TestFileUtils.searchAndReplace(
                        project.file("WordPress/build.gradle"),
                        "minSdkVersion( )* \\d+",
                        "minSdkVersion 21");
                break;
            case LEGACY_MULTIDEX:
            case DEX_ARCHIVE_LEGACY_MULTIDEX:
            case D8_LEGACY_MULTIDEX:
                break;
            default:
                throw new IllegalArgumentException("Unknown project scenario" + projectScenario);
        }
    }

    @Before
    public void initializeRecorder() throws Exception {
        /*
         * There's an annoying temporal dependency you have to be aware of before you call
         * project.getTestDir() (which getProfileDirectory() calls indirectly). You can't call it
         * before createTestDirectory() has been called, which is only called in apply(), which is
         * only called as part of the @Rule. So this has to live inside of the test body, or a
         * @Before or @After block.
         */
        recorder = new BenchmarkRecorder(new ProfileCapturer(project.getProfileDirectory()));
    }

    @After
    public void uploadResults() {
        recorder.uploadAsync();
    }

    @AfterClass
    public static void waitForUpload() throws Exception {
        BenchmarkRecorder.awaitUploads(15, TimeUnit.MINUTES);
    }

    @Test
    public void runBenchmarks() throws Exception {
        // Warm up
        Map<String, AndroidProject> models = model().getMulti().getModelMap();
        executor().run("clean");
        executor().run("assembleVanillaDebug");

        for (BenchmarkMode benchmarkMode : getBenchmarks()) {
            switch (benchmarkMode) {
                case EVALUATION:
                    record(benchmarkMode, () -> executor().run("tasks"));
                    break;
                case SYNC:
                    record(
                            benchmarkMode,
                            () -> {
                                Map<String, AndroidProject> model =
                                        model().getMulti().getModelMap();
                                assertThat(model.keySet()).contains(":WordPress");
                            });
                    continue;
                case BUILD__FROM_CLEAN:
                    FileUtils.cleanOutputDir(executor().getBuildCacheDir());
                    clean();
                    record(benchmarkMode, () -> executor().run("assembleVanillaDebug"));
                    break;
                case BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                    executor().run("assembleVanillaDebug");
                    changeJavaImplementation();
                    record(benchmarkMode, () -> executor().run("assembleVanillaDebug"));
                    break;
                case BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE:
                    executor().run("assembleVanillaDebug");
                    changeJavaApi("newMethod");
                    record(benchmarkMode, () -> executor().run("assembleVanillaDebug"));
                    break;
                case BUILD_ANDROID_TESTS_FROM_CLEAN:
                    clean();
                    record(benchmarkMode, () -> executor().run("assembleVanillaDebugAndroidTest"));
                    break;
                case GENERATE_SOURCES:
                    clean();
                    List<String> generateSourcesCommands =
                            ModelHelper.getGenerateSourcesCommands(
                                    models,
                                    project ->
                                            project.equals(":WordPress")
                                                    ? "vanillaDebug"
                                                    : "debug");
                    record(
                            benchmarkMode,
                            () ->
                                    executor()
                                            .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                                            .run(generateSourcesCommands));
                    break;
                case NO_OP:
                    executor().run("assembleVanillaDebug");
                    record(benchmarkMode, () -> executor().run("assembleVanillaDebug"));
                    break;
                default:
                    throw new UnsupportedOperationException(benchmarkMode.toString());
            }
        }
    }

    private void changeJavaImplementation() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("WordPress")
                        .file("src/main/java/org/wordpress/android/ui/WebViewActivity.java"),
                "protected void onPause\\(\\) \\{",
                "protected void onPause() {\n"
                        + "        android.util.Log.d(\"TAG\", \"onPause called "
                        + "\");");
    }

    private void changeJavaApi(@NonNull String newMethodName) throws IOException {
        File mainActivity =
                project.getSubproject("WordPress")
                        .file("src/main/java/org/wordpress/android/ui/WebViewActivity.java");
        TestFileUtils.searchAndReplace(
                mainActivity,
                "protected void onPause\\(\\) \\{",
                "protected void onPause() {\n" + "        " + newMethodName + "();");
        TestFileUtils.addMethod(
                mainActivity,
                "protected void "
                        + newMethodName
                        + "() {\n"
                        + "        android.util.Log.d(\"TAG\", \""
                        + newMethodName
                        + " called\");\n"
                        + "    }\n");
    }

    @NonNull
    private Set<BenchmarkMode> getBenchmarks() {
        return ImmutableSet.of(
                BenchmarkMode.EVALUATION,
                BenchmarkMode.SYNC,
                BenchmarkMode.GENERATE_SOURCES,
                BenchmarkMode.BUILD__FROM_CLEAN,
                BenchmarkMode.NO_OP,
                BenchmarkMode.BUILD_ANDROID_TESTS_FROM_CLEAN,
                BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE,
                BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE);
    }

    private void clean() throws IOException, InterruptedException {
        executor().run("clean");
    }

    @NonNull
    private ModelBuilder model() {
        return project.model().withoutOfflineFlag().ignoreSyncIssues(SyncIssue.SEVERITY_WARNING);
    }

    @NonNull
    private GradleTaskExecutor executor() {
        return projectScenario.configureExecutor(project.executor());
    }

    private void record(BenchmarkMode benchmarkMode, ExceptionRunnable r) throws Exception {
        recorder.record(projectScenario, Logging.Benchmark.PERF_ANDROID_MEDIUM, benchmarkMode, r);
    }
}
