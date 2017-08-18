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
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX, ProjectScenario.D8_NATIVE_MULTIDEX,
        };
    }

    @Before
    public void initializeProject() throws Exception {
        PerformanceTestProjects.initializeUberSkeleton(project);
        project.executor().run("addSources");
    }

    @Test
    public void runBenchmarks() throws Exception {
        Map<String, AndroidProject> models =
                project.model().ignoreSyncIssues().getMulti().getModelMap();

        PerformanceTestProjects.assertNoSyncErrors(models);

        // warm-up
        executor().run("clean");
        executor().run(":phthalic:assembleDebug");
        executor().run("clean");

        // recording data
        executor().recordBenchmark(BenchmarkMode.EVALUATION).run("tasks");
        executor().recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN).run(":phthalic:assembleDebug");
        executor().recordBenchmark(BenchmarkMode.NO_OP).run(":phthalic:assembleDebug");

        String source = "outissue/carnally/src/main/java/com/studio/carnally/LoginActivity.java";
        applyJavaChange(project.getTestDir().toPath().resolve(source), false);
        executor()
                .recordBenchmark(BenchmarkMode.BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE)
                .run(":phthalic:assembleDebug");

        applyJavaChange(project.getTestDir().toPath().resolve(source), true);
        executor()
                .recordBenchmark(BenchmarkMode.BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE)
                .run(":phthalic:assembleDebug");

        String stringsXml = "outissue/carnally/src/main/res/values/strings.xml";
        changeResValue(project.getTestDir().toPath().resolve(stringsXml));
        executor()
                .recordBenchmark(BenchmarkMode.BUILD_INC__SUB_PROJECT__RES__EDIT)
                .run(":phthalic:assembleDebug");

        addResValue(project.getTestDir().toPath().resolve(stringsXml));
        executor()
                .recordBenchmark(BenchmarkMode.BUILD_INC__SUB_PROJECT__RES__ADD)
                .run(":phthalic:assembleDebug");

        executor().run("clean");
        project.model().ignoreSyncIssues().recordBenchmark(BenchmarkMode.SYNC).getMulti();

        executor()
                .recordBenchmark(BenchmarkMode.GENERATE_SOURCES)
                .run(ModelHelper.getDebugGenerateSourcesCommands(models));

    }

    private RunGradleTasks executor() {
        return project.executor()
                .withEnableInfoLogging(false)
                .withUseDexArchive(projectScenario.useDexArchive())
                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                .with(BooleanOption.ENABLE_AAPT2, false)
                .with(BooleanOption.ENABLE_D8, projectScenario.useD8())
                .withoutOfflineFlag();
    }

    private void applyJavaChange(@NonNull Path sourceFile, boolean isAbiChange) throws IOException {
        String modifier = isAbiChange ? "public" : "private";
        String method =
                String.format(
                        "%s void generated_method_for_perf_test_%d() {\n"
                                + "    System.out.println(\"test code\");\n"
                                + "}",
                        modifier, System.nanoTime());
        TestFileUtils.addMethod(sourceFile.toFile(), method);
    }

    private void changeResValue(@NonNull Path stringsXml) throws IOException {
        TestFileUtils.searchAndReplace(stringsXml, "</string>", " added by test</string>");
    }

    private void addResValue(@NonNull Path stringsXml) throws IOException {
        TestFileUtils.searchAndReplace(
                stringsXml,
                "</resources>",
                "<string name=\"generated_by_test_for_perf\">my string</string></resources>");
    }
}
