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

package com.android.build.gradle.internal.scope

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.File

class BootClasspathBuilderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val mockitoJUnit: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    val project: Project? = null

    private val issueReporter = FakeSyncIssueReporter()

    @Before
    fun setupProject() {
        Mockito.`when`(project!!.files(any(Any::class.java)))
            .thenAnswer { FakeConfigurableFileCollection(it.arguments[0]) }
    }

    @After
    fun checkIssues() {
        assertThat(issueReporter.messages.isEmpty())
    }

    /** Regression test for b/139780810 */
    @Test
    fun checkPreviewHandling() {

        val android28 = temporaryFolder.newFile("android-28.jar")
        val android28Classpath = getClasspath(AndroidVersion(28), android28)

        val androidQ = temporaryFolder.newFile("android-Q.jar")
        val androidQClasspath = getClasspath(AndroidVersion(28, "Q"), androidQ)

        // Check that preview and final versions are not mixed.
        assertThat(android28Classpath.files.single().name).isEqualTo("android-28.jar")
        assertThat(androidQClasspath.files.single().name).isEqualTo("android-Q.jar")
    }

    private fun getClasspath(androidVersion: AndroidVersion, androidJar: File): FileCollection {
        return BootClasspathBuilder.computeClasspath(
            project = project!!,
            issueReporter = issueReporter,
            targetBootClasspath = FakeGradleProvider(listOf(androidJar)),
            targetAndroidVersion = FakeGradleProvider(androidVersion),
            additionalLibraries = FakeGradleProvider(listOf()),
            optionalLibraries = FakeGradleProvider(listOf()),
            annotationsJar = FakeGradleProvider(null),
            addAllOptionalLibraries = false,
            libraryRequests = listOf()
        )
    }
}