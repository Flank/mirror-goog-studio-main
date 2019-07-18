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

package com.android.ide.common.gradle.model


import com.android.ide.common.gradle.model.stubs.ProjectSyncIssuesStub
import com.android.ide.common.gradle.model.stubs.SyncIssueStub
import com.android.testutils.Serialization
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.Serializable

/** Tests for {@link IdeProjectSyncIssues}. */
class IdeProjectSyncIssuesTest {
    private val modelCache = ModelCache()

    @Test
    fun serializable() {
        assertThat(IdeProjectSyncIssues::class.java).isAssignableTo(Serializable::class.java)
    }

    @Test
    fun serialization() {
        val expectedSyncIssue = IdeSyncIssue(SyncIssueStub())

        val projectSyncIssues = IdeProjectSyncIssues(ProjectSyncIssuesStub(), modelCache)
        assertThat(projectSyncIssues.syncIssues).containsExactly(expectedSyncIssue)
        val bytes = Serialization.serialize(projectSyncIssues)
        val o = Serialization.deserialize(bytes)
        assertThat(o).isEqualTo(projectSyncIssues)
        assertThat((o as IdeProjectSyncIssues).syncIssues).containsExactly(expectedSyncIssue)
    }

    @Test
    fun constructor() {
        val original = ProjectSyncIssuesStub()
        val copy = IdeProjectSyncIssues(ProjectSyncIssuesStub(), modelCache)
        val expectedIssues = original.syncIssues.map { issue -> IdeSyncIssue(issue) }

        assertThat(copy.syncIssues).isEqualTo(expectedIssues)
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy)
    }
}