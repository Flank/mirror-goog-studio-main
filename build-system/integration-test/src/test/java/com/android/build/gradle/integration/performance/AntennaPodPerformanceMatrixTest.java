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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.BuildModel;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class AntennaPodPerformanceMatrixTest {

    private static final AndroidVersion INSTANT_RUN_TARGET_DEVICE_VERSION =
            new AndroidVersion(24, null);

    @Rule public final GradleTestProject mainProject;
    @NonNull private final ProjectScenario projectScenario;
    private int nonce = 0;
    private GradleTestProject project;

    public AntennaPodPerformanceMatrixTest(@NonNull ProjectScenario projectScenario) {
        this.projectScenario = projectScenario;
        mainProject =
                GradleTestProject.builder()
                        .fromExternalProject("AntennaPod")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.ANTENNA_POD, projectScenario))
                        .withRelativeProfileDirectory(
                                Paths.get("AntennaPod", "build", "android-profile"))
                        .withHeap("1536M")
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static ProjectScenario[] getParameters() {
        return new ProjectScenario[] {
            ProjectScenario.NORMAL,
            ProjectScenario.DEX_ARCHIVE_MONODEX,
            ProjectScenario.DEX_OUT_OF_PROCESS,
            ProjectScenario.DESUGAR,
        };
    }

    @Before
    public void initializeProject() throws Exception {
        PerformanceTestProjects.initializeAntennaPod(mainProject);
        project = mainProject.getSubproject("AntennaPod");

        File appBuildFile = project.file("app/build.gradle");
        switch (projectScenario) {
            case NORMAL:
                break;
            case DEX_OUT_OF_PROCESS:
                DexInProcessHelper.disableDexInProcess(appBuildFile);
                break;
            case DEX_ARCHIVE_MONODEX:
                break;
            case DESUGAR:
                TestFileUtils.searchAndReplace(
                        appBuildFile, "apply plugin: \"me.tatarka.retrolambda\"", "");
                TestFileUtils.searchAndReplace(
                        project.getSubproject("AntennaPod/core").getBuildFile(),
                        "apply plugin: \"me.tatarka.retrolambda\"",
                        "");
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown project scenario" + projectScenario);
            }
    }

    @Test
    public void runBenchmarks() throws Exception {
        ModelContainer<AndroidProject> modelContainer = model().getMulti();
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        executor().run("assembleDebug", "assembleDebugAndroidTest");
        executor().run("clean");

        for (BenchmarkMode benchmarkMode : PerformanceTestUtil.BENCHMARK_MODES) {
            InstantRun instantRunModel = null;
            List<String> tasks;
            boolean isEdit = false;

            switch (benchmarkMode) {
                case EVALUATION:
                    tasks = ImmutableList.of("tasks");
                    break;
                case SYNC:
                    model().recordBenchmark(BenchmarkMode.SYNC).getMulti();
                    continue;
                case BUILD__FROM_CLEAN:
                    FileUtils.cleanOutputDir(executor().getBuildCacheDir());
                    executor().run("clean");
                    tasks = ImmutableList.of(":app:assembleDebug");
                    break;
                case BUILD__FROM_CLEAN__POPULATED_BUILD_CACHE:
                    // TODO
                    continue;
                case BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE:
                    tasks = ImmutableList.of(":app:assembleDebug");
                    // Initial build for incremental tasks
                    executor().run(tasks);
                    isEdit = true;
                    break;
                case BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE:
                case BUILD_INC__MAIN_PROJECT__RES__EDIT:
                case BUILD_INC__MAIN_PROJECT__RES__ADD:
                case BUILD_INC__SUB_PROJECT__RES__EDIT:
                case BUILD_INC__SUB_PROJECT__RES__ADD:
                    //TODO
                    continue;
                case INSTANT_RUN_BUILD__FROM_CLEAN:
                    executor().run("clean");
                    tasks = ImmutableList.of(":app:assembleDebug");
                    break;
                case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE:
                    instantRunModel = InstantRunTestUtils.getInstantRunModel(models.get(":app"));
                    tasks = ImmutableList.of(":app:assembleDebug");
                    // Initial build for incremental instant run tasks
                    executor()
                            .withInstantRun(INSTANT_RUN_TARGET_DEVICE_VERSION)
                            .withEnableInfoLogging(false)
                            .run(tasks);
                    isEdit = true;
                    break;
                case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE:
                case INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__API_CHANGE:
                case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__EDIT:
                case INSTANT_RUN_BUILD__MAIN_PROJECT__RES__ADD:
                case INSTANT_RUN_BUILD__SUB_PROJECT__RES__EDIT:
                case INSTANT_RUN_BUILD__SUB_PROJECT__RES__ADD:
                    //TODO
                    continue;
                case BUILD_ANDROID_TESTS_FROM_CLEAN:
                    executor().run("clean");
                    tasks = ImmutableList.of(":app:assembleDebugAndroidTest");
                    break;
                case BUILD_UNIT_TESTS_FROM_CLEAN:
                    executor().run("clean");
                    tasks = ImmutableList.of(":app:assembleDebugUnitTest");
                    break;
                case GENERATE_SOURCES:
                    executor().run("clean");
                    executor()
                            .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                            .recordBenchmark(BenchmarkMode.GENERATE_SOURCES)
                            .run(ModelHelper.getDebugGenerateSourcesCommands(models));
                    continue;
                case NO_OP:
                    // Do an initial build for NO_OP.
                    tasks = ImmutableList.of(":app:assembleDebug");
                    executor().run(tasks);

                    GradleBuildResult result =
                            executor()
                                    .recordBenchmark(benchmarkMode)
                                    .withEnableInfoLogging(true)
                                    .run(tasks);
                    assertThat(result.getInputChangedTasks()).isEmpty();
                    continue;
                case LINT_RUN:
                    continue; // TODO
                default:
                    throw new UnsupportedOperationException(benchmarkMode.toString());
            }

            if (isEdit) {
                doEdit(
                        PerformanceTestUtil.getSubProjectType(benchmarkMode),
                        PerformanceTestUtil.getEditType(benchmarkMode),
                        nonce++);
            }

            if (instantRunModel != null) {
                executor()
                        .recordBenchmark(benchmarkMode)
                        .withInstantRun(INSTANT_RUN_TARGET_DEVICE_VERSION)
                        .run(tasks);

                InstantRunTestUtils.loadContext(instantRunModel).getVerifierStatus();
            } else {
                executor().recordBenchmark(benchmarkMode).run(tasks);
            }
        }
    }

    @NonNull
    private BuildModel model() throws IOException {
        BuildModel buildModel = project.model().ignoreSyncIssues().withoutOfflineFlag();
        PerformanceTestProjects.assertNoSyncErrors(buildModel.getMulti().getModelMap());
        return buildModel;
    }

    public RunGradleTasks executor() {
        return project.executor()
                .withEnableInfoLogging(false)
                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                .with(BooleanOption.ENABLE_AAPT2, false)
                .withUseDexArchive(projectScenario.useDexArchive());
    }

    private void doEdit(
            @NonNull PerformanceTestUtil.SubProjectType subProjectType,
            @NonNull PerformanceTestUtil.EditType editType,
            int nonce)
            throws Exception {

        if (subProjectType != PerformanceTestUtil.SubProjectType.MAIN_PROJECT) {
            throw new UnsupportedOperationException("TODO: Cannot edit non main project yet.");
        }
        switch (editType) {
            case JAVA__IMPLEMENTATION_CHANGE:
                TestFileUtils.searchAndReplace(
                        project.file(
                                "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"),
                        "public void onStart\\(\\) \\{",
                        "public void onStart() {\n"
                                + "        Log.d(TAG, \"onStart called "
                                + nonce
                                + "\");");
                break;
            case JAVA__API_CHANGE:
                String newMethodName = "newMethod" + nonce;
                File mainActivity =
                        project.file(
                                "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java");
                TestFileUtils.searchAndReplace(
                        mainActivity,
                        "public void onStart\\(\\) \\{",
                        "public void onStart() {\n" + "        " + newMethodName + "();");
                TestFileUtils.addMethod(
                        mainActivity,
                        "private void "
                                + newMethodName
                                + "() {\n"
                                + "        Log.d(TAG, \""
                                + newMethodName
                                + " called\");\n"
                                + "    }\n");
                break;
            case RES__EDIT:
            case RES__ADD:
                throw new UnsupportedOperationException(
                        "TODO: Support '" + editType.toString() + "'");
            default:
                throw new UnsupportedOperationException(
                        "Don't know how to do '" + editType.toString() + "'.");
        }
    }
}
