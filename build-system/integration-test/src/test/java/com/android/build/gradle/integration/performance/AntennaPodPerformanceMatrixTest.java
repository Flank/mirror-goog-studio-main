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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.BuildModel;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.build.gradle.integration.common.utils.JackHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class AntennaPodPerformanceMatrixTest {

    private int nonce = 0;
    @Rule public final GradleTestProject mainProject;
    @NonNull private final Set<ProjectScenario> projectScenarios;
    private GradleTestProject project;

    public AntennaPodPerformanceMatrixTest(@NonNull Set<ProjectScenario> projectScenarios) {
        this.projectScenarios = projectScenarios;
        mainProject =
                GradleTestProject.builder()
                        .fromExternalProject("AntennaPod")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.ANTENNA_POD, projectScenarios))
                        .withRelativeProfileDirectory(
                                Paths.get("AntennaPod", "build", "android-profile"))
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] {
                    {EnumSet.of(ProjectScenario.NORMAL)},
                    {EnumSet.of(ProjectScenario.NORMAL, ProjectScenario.JACK_ON)},
                    {EnumSet.of(ProjectScenario.DEX_OUT_OF_PROCESS)},
                    {EnumSet.of(ProjectScenario.JACK_OUT_OF_PROCESS)}
                });
    }

    @Before
    public void initializeProject() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath \"com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + '"');

        upgradeBuildToolsVersion(project.getBuildFile());
        upgradeBuildToolsVersion(mainProject.file("afollestad/commons/build.gradle"));
        upgradeBuildToolsVersion(mainProject.file("afollestad/core/build.gradle"));
        upgradeBuildToolsVersion(mainProject.file("AudioPlayer/library/build.gradle"));

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                "maven { url '"
                        + FileUtils.toSystemIndependentPath(System.getenv("CUSTOM_REPO"))
                        + "'} \n"
                        + "        jcenter()");

        File appBuildFile = project.file("app/build.gradle");
        for (ProjectScenario projectScenario : projectScenarios) {
            switch (projectScenario) {
                case NORMAL:
                    break;
                case DEX_OUT_OF_PROCESS:
                    DexInProcessHelper.disableDexInProcess(appBuildFile);
                    break;
                case JACK_ON:
                    disableRetrolambda(appBuildFile);
                    JackHelper.enableJack(appBuildFile);
                    break;
                case JACK_OUT_OF_PROCESS:
                    disableRetrolambda(appBuildFile);
                    // automatically enables Jack as well
                    JackHelper.disableJackInProcess(appBuildFile);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unknown project scenario" + projectScenario);
            }
        }
    }

    private static void upgradeBuildToolsVersion(@NonNull File buildGradleFile) throws IOException {
        TestFileUtils.searchAndReplace(
                buildGradleFile,
                "buildToolsVersion( =)? \"\\d+.\\d+.\\d+\"",
                "buildToolsVersion$1 \"25.0.0\"");
    }

    private static void disableRetrolambda(@NonNull File buildGradleFile) throws IOException {
        TestFileUtils.searchAndReplace(
                buildGradleFile,
                "apply plugin: \"me.tatarka.retrolambda\"",
                "// retrolambda disabled");
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

            if (isJackOn() && isInstantRunOn(benchmarkMode)) {
                // Jack does not support Instant Run, because it does not generate intermediate
                // class files when compiling an app
                continue;
            }

            switch (benchmarkMode) {
                case EVALUATION:
                    tasks = ImmutableList.of("tasks");
                    break;
                case SYNC:
                    model().recordBenchmark(BenchmarkMode.SYNC).getMulti();
                    continue;
                case BUILD__FROM_CLEAN:
                    executor().run("clean");
                    tasks = ImmutableList.of(":app:assembleDebug");
                    break;
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
                    project.executor()
                            .withInstantRun(24, ColdswapMode.MULTIDEX)
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
                    project.executor()
                            .withArgument("-Pandroid.injected.generateSourcesOnly=true")
                            .recordBenchmark(BenchmarkMode.GENERATE_SOURCES)
                            .run(ModelHelper.getDebugGenerateSourcesCommands(models));
                    continue;
                case NO_OP:
                    // Do an initial build for NO_OP.
                    tasks = ImmutableList.of(":app:assembleDebug");
                    executor().run(tasks);
                    break;
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
                        .withInstantRun(24, ColdswapMode.MULTIDEX)
                        .run(tasks);

                InstantRunTestUtils.loadContext(instantRunModel).getVerifierStatus();
            } else {
                executor().recordBenchmark(benchmarkMode).run(tasks);
            }
        }
    }

    @NonNull
    private BuildModel model() {
        return project.model().withoutOfflineFlag();
    }

    public RunGradleTasks executor() {
        return project.executor().withEnableInfoLogging(false).withoutOfflineFlag();
    }

    private boolean isJackOn() {
        return projectScenarios.contains(ProjectScenario.JACK_ON)
                || projectScenarios.contains(ProjectScenario.JACK_OUT_OF_PROCESS);
    }

    private boolean isInstantRunOn(BenchmarkMode benchmarkMode) {
        final List<Logging.BenchmarkMode> instantRunModes = ImmutableList.of(
                BenchmarkMode.INSTANT_RUN_BUILD__FROM_CLEAN,
                BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE,
                BenchmarkMode.INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                BenchmarkMode.INSTANT_RUN_BUILD__SUB_PROJECT__JAVA__API_CHANGE,
                BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__RES__EDIT,
                BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__RES__ADD,
                BenchmarkMode.INSTANT_RUN_BUILD__SUB_PROJECT__RES__EDIT,
                BenchmarkMode.INSTANT_RUN_BUILD__SUB_PROJECT__RES__ADD,
                BenchmarkMode.INSTANT_RUN_BUILD_INC_JAVA_DEPRECATED
        );
        return instantRunModes.contains(benchmarkMode);
    }

    private void doEdit(
            @NonNull PerformanceTestUtil.SubProjectType subProjectType,
            @NonNull PerformanceTestUtil.EditType editType,
            int nonce)
            throws IOException {

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
