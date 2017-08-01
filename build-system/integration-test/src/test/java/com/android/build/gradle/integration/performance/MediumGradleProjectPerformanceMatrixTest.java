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
import com.android.build.gradle.integration.common.fixture.BuildModel;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class MediumGradleProjectPerformanceMatrixTest {

    @Rule public final GradleTestProject project;
    @NonNull private final ProjectScenario projectScenario;

    public MediumGradleProjectPerformanceMatrixTest(@NonNull ProjectScenario projectScenario) {
        this.projectScenario = projectScenario;
        project =
                GradleTestProject.builder()
                        .fromExternalProject("gradle-perf-android-medium")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.PERF_ANDROID_MEDIUM, projectScenario))
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
                throw new IllegalArgumentException(
                        "Unknown project scenario" + projectScenario);
        }
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
                    executor().recordBenchmark(benchmarkMode).run("tasks");
                    break;
                case SYNC:
                    Map<String, AndroidProject> model =
                            model().recordBenchmark(BenchmarkMode.SYNC).getMulti().getModelMap();
                    assertThat(model.keySet()).contains(":WordPress");
                    continue;
                case BUILD__FROM_CLEAN:
                    FileUtils.cleanOutputDir(executor().getBuildCacheDir());
                    clean();
                    executor().recordBenchmark(benchmarkMode).run("assembleVanillaDebug");
                    break;
                case BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                    executor().run("assembleVanillaDebug");
                    changeJavaImplementation();
                    executor().recordBenchmark(benchmarkMode).run("assembleVanillaDebug");
                    break;
                case BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE:
                    executor().run("assembleVanillaDebug");
                    changeJavaApi("newMethod");
                    executor().recordBenchmark(benchmarkMode).run("assembleVanillaDebug");
                    break;
                case BUILD_ANDROID_TESTS_FROM_CLEAN:
                    clean();
                    executor()
                            .recordBenchmark(benchmarkMode)
                            .run("assembleVanillaDebugAndroidTest");
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
                    executor()
                            .recordBenchmark(benchmarkMode)
                            .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                            .run(generateSourcesCommands);
                    break;
                case NO_OP:
                    executor().run("assembleVanillaDebug");
                    executor().recordBenchmark(benchmarkMode).run("assembleVanillaDebug");
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
    private BuildModel model() {
        return project.model().withoutOfflineFlag();
    }

    @NonNull
    private RunGradleTasks executor() {
        return project.executor()
                .withEnableInfoLogging(false)
                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                .with(BooleanOption.ENABLE_AAPT2, false)
                .with(BooleanOption.ENABLE_D8, projectScenario.useD8())
                .withUseDexArchive(projectScenario.useDexArchive());
    }
}
