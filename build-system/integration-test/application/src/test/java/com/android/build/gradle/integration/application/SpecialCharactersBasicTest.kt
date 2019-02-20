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

import com.android.build.gradle.integration.common.category.SmokeTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProjectPaths
import com.android.builder.model.SyncIssue
import com.android.utils.FileUtils
import org.junit.AfterClass
import java.util.regex.Pattern
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder

/**
 * A very simple test to compile a project with special characters in it
 */
@Category(SmokeTests::class)
class SpecialCharactersBasicTest {

    @get:Rule
    val project: GradleTestProject = copyProjectWithName("basic", "1b@sic w%ith spécià`l charãct~ers=.;{}$#!&^()¡²³¤€¼½¾‘’¥×")


    private fun copyProjectWithName(originalProject: String, copyName: String): GradleTestProject {
        val originalProjectPath = TestProjectPaths.getTestProjectDir(originalProject)

        val projectCopyPath = temporaryFolder.newFolder(copyName)

        FileUtils.copyDirectory(originalProjectPath, projectCopyPath)

        return GradleTestProject.builder()
            .fromDir(projectCopyPath)
            .create()
    }

    @Test
    fun testProjectWithSpecialCharacters() {

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
        @ClassRule
        @JvmField
        val temporaryFolder = TemporaryFolder()
    }

}
