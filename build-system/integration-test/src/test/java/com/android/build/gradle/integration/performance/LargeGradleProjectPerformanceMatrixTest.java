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
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class LargeGradleProjectPerformanceMatrixTest {

    @Rule public final GradleTestProject project;
    @NonNull private final ProjectScenario projectScenario;

    public LargeGradleProjectPerformanceMatrixTest(@NonNull ProjectScenario projectScenario) {
        this.projectScenario = projectScenario;
        project =
                GradleTestProject.builder()
                        .fromExternalProject("android-studio-gradle-test")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.PERF_ANDROID_LARGE, projectScenario))
                        .withHeap("20G")
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static ProjectScenario[] getParameters() {
        return new ProjectScenario[] {
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX,
            //ProjectScenario.DEX_ARCHIVE_LEGACY_MULTIDEX,
        };
    }

    @Before
    public void initializeProject() throws Exception {

        //noinspection ConstantConditions
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                PerformanceTestProjects.generateLocalRepositoriesSnippet());

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                "classpath 'com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + "'");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "(classpath 'com.uber:okbuck:[^']+')", "// $0");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "(classpath 'com.jakewharton:butterknife-gradle-plugin:8.4.0')",
                "// $1");

        String content = new String(Files.readAllBytes(project.getBuildFile().toPath()));
        int pos = content.indexOf("apply plugin: 'com.uber");
        Files.write(project.getBuildFile().toPath(), content.substring(0, pos - 1).getBytes());

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "buildToolsVersion: '\\d+.\\d+.\\d+',",
                "buildToolsVersion: '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "',");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "supportVersion *: '\\d+.\\d+.\\d+',",
                "supportVersion: '" + GradleTestProject.SUPPORT_LIB_VERSION + "',");

        TestFileUtils.searchAndReplace(
                project.file("build.gradle"),
                "(force 'com.android.support:[^:]*):[^']*'",
                "$1:" + GradleTestProject.SUPPORT_LIB_VERSION + "'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"), "('io.reactivex:rxjava):[^']*'", "$1:1.2.3'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.jakewharton:butterknife[^:]*):[^']*'",
                "$1:8.4.0'");
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.squareup.okio:okio):[^']*'",
                "$1:1.9.0'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.jakewharton.rxbinding:rxbinding[^:]*):[^']+'",
                "$1:1.0.0'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.google.auto.value:auto-value):[^']*'",
                "$1:1.3'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "('com.google.code.gson:gson):[^']+'",
                "$1:2.8.0'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "def support = \\[",
                "def support = [\n"
                        + "leanback : \"com.android.support:leanback-v17:\\${versions.supportVersion}\",\n"
                        + "mediarouter : [\"com.android.support:mediarouter-v7:25.0.1\","
                        // There is no mediarouter > 25.0.1, but upgrade its dependencies.
                        + "\"com.android.support:appcompat-v7:\\${versions.supportVersion}\","
                        + "\"com.android.support:palette-v7:\\${versions.supportVersion}\"],\n");

        TestFileUtils.appendToFile(
                project.file("dependencies.gradle"),
                "\n\n// Fixes for support lib versions.\n"
                        + "ext.deps.other.appcompat = [\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        ext.deps.other.appcompat,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.cast = [\n"
                        + "        ext.deps.other.cast,\n"
                        + "        ext.deps.support.mediarouter,\n"
                        + "        ext.deps.support.appCompat\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.design = [\n"
                        + "        ext.deps.support.design,\n"
                        + "        ext.deps.other.design,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.facebook = [\n"
                        + "        ext.deps.other.facebook,\n"
                        + "        ext.deps.support.cardView,\n"
                        + "        \"com.android.support:customtabs:${versions.supportVersion}\",\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.fresco = [\n"
                        + "        ext.deps.other.fresco,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.googleMap = [\n"
                        + "        ext.deps.other.googleMap,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.leanback = [\n"
                        + "        ext.deps.other.leanback,\n"
                        + "        ext.deps.support.leanback,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.playServices.maps = [\n"
                        + "        ext.deps.playServices.maps,\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.rave = [\n"
                        + "        ext.deps.other.gson,\n"
                        + "        ext.deps.other.rave,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.recyclerview = [\n"
                        + "        ext.deps.support.recyclerView,\n"
                        + "        ext.deps.other.recyclerview,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.utils = [\n"
                        + "        ext.deps.other.utils,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "\n // End support lib version fixes. \n");

        // Fix project compilation.
        TestFileUtils.searchAndReplace(
                project.file("outissue/cyclus/build.gradle"),
                "dependencies \\{\n",
                "dependencies {\n"
                        + "compile deps.support.leanback\n"
                        + "compile deps.support.appCompat\n"
                        + "compile deps.external.rxjava\n");

        TestFileUtils.searchAndReplace(
                project.file("outissue/embrace/build.gradle"),
                "dependencies \\{\n",
                "dependencies { compile deps.external.rxjava\n");

        TestFileUtils.searchAndReplace(
                project.file("outissue/nutate/build.gradle"),
                "dependencies \\{\n",
                "dependencies { compile deps.support.mediarouter\n");

        // Remove butterknife plugin.
        for (String path :
                ImmutableList.of(
                        "outissue/carnally",
                        "outissue/airified",
                        "Padraig/follicle",
                        "outissue/Glumaceae",
                        "fratry/sodden",
                        "subvola/zelator",
                        "subvola/doored",
                        "subvola/transpire",
                        "subvola/atbash",
                        "subvola/gorgoneum/Chordata",
                        "subvola/gorgoneum/metanilic/agaric",
                        "subvola/gorgoneum/teerer/polytonal",
                        "subvola/gorgoneum/teerer/Cuphea",
                        "harvestry/Timbira")) {
            TestFileUtils.searchAndReplace(
                    project.file(path + "/build.gradle"),
                    "apply plugin: 'com.jakewharton.butterknife'",
                    "/* $0 */");
        }

        // because we are testing the source generation which will trigger the test manifest
        // merging, minSdkVersion has to be at least 17
        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"), "(minSdkVersion *): \\d+,", "$1: 17,");

        switch (projectScenario.getFlags().getMultiDex()) {
            case LEGACY:
                break;
            case NATIVE:
                TestFileUtils.searchAndReplace(
                        project.file("dependencies.gradle"), "(minSdkVersion *): \\d+,", "$1: 21,");
                break;
            default:
                throw new IllegalArgumentException("Unknown project scenario" + projectScenario);
        }
    }

    @Test
    public void runBenchmarks() throws Exception {
        ModelContainer<AndroidProject> modelContainer = model().ignoreSyncIssues().getMulti();
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        for (AndroidProject project : models.values()) {
            List<SyncIssue> severeIssues =
                    project.getSyncIssues()
                            .stream()
                            .filter(issue -> issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                            .collect(Collectors.toList());
            Truth.assertThat(severeIssues).named("Issues for " + project.getName()).isEmpty();
        }

        // TODO: warm up (This is really slow already)

        executor().recordBenchmark(BenchmarkMode.EVALUATION).run("tasks");

        // TODO: Introduce more benchmarks once this one seems stable.
        if (true) {
            return;
        }
        executor().recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN).run("assembleDebug");

        executor().recordBenchmark(BenchmarkMode.NO_OP).run("assembleDebug");

        executor().run("clean");

        model().ignoreSyncIssues().recordBenchmark(BenchmarkMode.SYNC).getMulti();

        executor()
                .recordBenchmark(BenchmarkMode.GENERATE_SOURCES)
                .run(ModelHelper.getDebugGenerateSourcesCommands(models));

    }

    @NonNull
    private BuildModel model() {
        return project.model().withoutOfflineFlag();
    }

    private RunGradleTasks executor() {
        return project.executor()
                .withEnableInfoLogging(false)
                .withUseDexArchive(projectScenario.useDexArchive())
                .disablePreDexBuildCache()
                .disableAaptV2()
                .withoutOfflineFlag();
    }
}
