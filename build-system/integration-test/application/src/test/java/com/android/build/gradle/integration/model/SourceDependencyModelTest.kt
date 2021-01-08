/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Make sure we are able to sync projects with source dependencies. */
class SourceDependencyModelTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestProject("sourceDependency")
            .create()

    @Before
    fun setUp() {
        // Move .git dirs to their location in order ot have Gradle source deps working correctly.
        project.projectDir.resolve("simple-git-repo/gitdir")
                .copyRecursively(project.projectDir.resolve("simple-git-repo/.git"))
        project.projectDir.resolve("simple-git-repo-2/gitdir")
                .copyRecursively(project.projectDir.resolve("simple-git-repo-2/.git"))
    }

    @Test
    fun checkModelBuildSuccessfully() {
        project.model()
                .withoutOfflineFlag()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchAndroidProjects()
    }
}
