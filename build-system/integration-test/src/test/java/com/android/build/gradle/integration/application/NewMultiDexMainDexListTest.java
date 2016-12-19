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

import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to assert that the multidex list produced by the wordpress app is correct.
 *
 * <p>It compares the existing (Merge jar -> Proguard -> dx call) with the New Shrinker based
 * implementation.
 */
public class NewMultiDexMainDexListTest {

    @Rule
    public GradleTestProject outerProject =
            GradleTestProject.builder()
                    .fromExternalProject("AntennaPod")
                    .withHeap("4096M")
                    .create();

    private GradleTestProject project;

    @Before
    public void initProject() throws IOException {
        PerformanceTestProjects.initializeAntennaPod(outerProject);
        project = outerProject.getSubproject("AntennaPod");
        TestFileUtils.appendToFile(
                project.getSubproject("AntennaPod/app").getBuildFile(),
                "\nandroid.defaultConfig.multiDexEnabled true");
    }

    @Test
    public void checkNewAndOldGiveSameResults() throws Exception {
        if (GradleTestProject.USE_JACK) {
            throw new AssumptionViolatedException(
                    "Jack has its own implementation of main dex list");
        }
        checkBuild();
        TestFileUtils.appendToFile(
                project.getSubproject("AntennaPod/app").getBuildFile(),
                "\nandroid.dexOptions.keepRuntimeAnnotatedClasses false");
        checkBuild();
    }

    private void checkBuild() throws Exception {

        project.executor()
                .withProperty(AndroidGradleOptions.PROPERTY_USE_MAIN_DEX_LIST_2, "false")
                .run(":app:assembleDebug");

        Set<String> mainDexList = getMainDexList();

        project.executor()
                .withProperty(AndroidGradleOptions.PROPERTY_USE_MAIN_DEX_LIST_2, "true")
                .run(":app:assembleDebug");

        Set<String> mainDexList2 = getMainDexList();

        assertThat(mainDexList2).containsExactlyElementsIn(mainDexList);
    }


    private Set<String> getMainDexList() throws IOException {
        Path listFile =
                project.getSubproject("AntennaPod/app")
                        .getIntermediatesDir()
                        .toPath()
                        .resolve("multi-dex/debug/maindexlist.txt");
        return Files.readAllLines(listFile)
                .stream()
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toSet());
    }
}
