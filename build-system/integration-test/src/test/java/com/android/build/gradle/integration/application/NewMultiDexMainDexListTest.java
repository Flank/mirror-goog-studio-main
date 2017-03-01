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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test to assert that the multidex list produced by the wordpress app is correct.
 *
 * <p>It compares the existing (Merge jar -> Proguard -> dx call) with the New Shrinker based
 * implementation.
 */
@RunWith(FilterableParameterized.class)
public class NewMultiDexMainDexListTest {

    @Parameterized.Parameters(name = "{0}")
    public static Set<Project> projectList() {
        return EnumSet.allOf(Project.class);
    }

    @Rule public GradleTestProject outerProject;

    private GradleTestProject project;

    private final Project testProject;

    public NewMultiDexMainDexListTest(Project project) {
        this.testProject = project;
        outerProject =
                GradleTestProject.builder()
                        .fromExternalProject(project.projectName)
                        .withHeap("4096M")
                        .create();
    }

    @Before
    public void initProject() throws Exception {
        switch (testProject) {
            case ANTENNAPOD:
                PerformanceTestProjects.initializeAntennaPod(outerProject);
                project = outerProject.getSubproject("AntennaPod");
                TestFileUtils.appendToFile(
                        project.getSubproject("AntennaPod/app").getBuildFile(),
                        "\nandroid.defaultConfig.multiDexEnabled true");
                break;
            case WORDPRESS:
                project = outerProject;
                PerformanceTestProjects.initializeWordpress(project);
                break;
        }
    }

    @Test
    public void checkNewAndOldGiveSameResults() throws Exception {
        checkBuild();
        TestFileUtils.appendToFile(
                project.getSubproject(testProject.appProjectDirectory).getBuildFile(),
                "\nandroid.dexOptions.keepRuntimeAnnotatedClasses false");
        checkBuild();
    }

    private void checkBuild() throws Exception {

        project.executor().withUseDexArchive(false).run(testProject.assembleTask);

        Set<String> mainDexList = getMainDexList();

        project.executor().withUseDexArchive(true).run(testProject.assembleTask);

        Set<String> mainDexList2 = getMainDexList();

        assertThat(mainDexList2).containsExactlyElementsIn(mainDexList);
    }

    private Set<String> getMainDexList() throws Exception {
        Path listFile =
                project.getSubproject(testProject.appProjectDirectory)
                        .getIntermediatesDir()
                        .toPath()
                        .resolve("multi-dex/" + testProject.variantDirPath + "/maindexlist.txt");
        return Files.readAllLines(listFile)
                .stream()
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toSet());
    }

    private enum Project {
        WORDPRESS(
                "gradle-perf-android-medium",
                ":WordPress:assembleVanillaDebug",
                "WordPress",
                "vanilla/debug"),
        ANTENNAPOD("AntennaPod", ":app:assembleDebug", "AntennaPod/app", "debug"),
        ;

        final String projectName;
        final String assembleTask;
        final String appProjectDirectory;
        final String variantDirPath;

        Project(
                @NonNull String projectName,
                @NonNull String assembleTask,
                @NonNull String appProjectDirectory,
                @NonNull String variantDirPath) {
            this.projectName = projectName;
            this.assembleTask = assembleTask;
            this.appProjectDirectory = appProjectDirectory;
            this.variantDirPath = variantDirPath;
        }
    }
}
