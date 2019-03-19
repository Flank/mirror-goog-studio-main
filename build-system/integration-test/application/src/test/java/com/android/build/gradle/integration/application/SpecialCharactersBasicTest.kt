/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProjectPaths
import com.android.builder.model.SyncIssue
import com.android.utils.FileUtils
import java.util.regex.Pattern
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A very simple test to compile a project with special characters in it
 */
@RunWith(Parameterized::class)
class SpecialCharactersBasicTest(projectName: String) {

    @get:Rule
    var project: GradleTestProject = copyProjectWithName("basic", projectName)


    private fun copyProjectWithName(originalProject: String, copyName: String): GradleTestProject {
        val originalProjectPath = TestProjectPaths.getTestProjectDir(originalProject)

        try {
            val projectCopyPath = temporaryFolder.newFolder(copyName)
            FileUtils.copyDirectory(originalProjectPath, projectCopyPath)

            return GradleTestProject.builder()
                .fromDir(projectCopyPath)
                .create()
        } catch(err: java.io.IOException) {
            throw(java.io.IOException("Could not create project ${copyName}. This could be caused by an illegal file name.", err))
        }
    }

    @Test
    fun testProjectsWithSpecialCharacters() {
        project.execute("clean", "assemble")

        // basic project overwrites buildConfigField which emits a sync warning
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects().onlyModel

        model.syncIssues
            .forEach { issue ->
                assertThat(issue.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
                assertThat(issue.message)
                    .containsMatch(Pattern.compile(".*value is being replaced.*"))
            }
    }

    companion object {
        // temporaryFolder is a shared root folder for the test projects.
        @ClassRule
        @JvmField
        val temporaryFolder = TemporaryFolder()

        @JvmStatic
        @Parameterized.Parameters
        fun projectNames(): Collection<String> {
            return listOf(
                "1b@s %i péà`e eã~e=.;{}\$#!&^()¡²³¤€¼½¾‘’¥×βα基本осಮೂ基本どきコラપા기본आधមូលั้นਬੁਨਿਆძიমৌƏՀիመሠ",
                "בסיסיالأساسيةיקערדיק"

                /* Add these for individual language tests
                "βασικός",
                "基本",
                "основной",
                "ಮೂಲಭೂತ",
                "基本的な",
                "પાયાની",
                "الأساسية",
                "기본",
                "आधारभूत",
                "יקערדיק",
                "មូលដ្ឋាន",
                "ขั้นพื้นฐาน",
                "ਬੁਨਿਆਦੀ",
                "בסיסי",
                "ძირითადი",
                "মৌলিক",
                "Əsas",
                "Հիմնական",
                "መሠረታዊ"*/
            )
        }
    }

}
