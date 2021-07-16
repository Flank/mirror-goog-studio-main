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

import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootClasspathBuilderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val projectLayout: ProjectLayout = ProjectBuilder.builder().build().layout

    private val issueReporter = FakeSyncIssueReporter()

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
        assertThat(android28Classpath.get().single().asFile.name).isEqualTo("android-28.jar")
        assertThat(androidQClasspath.get().single().asFile.name).isEqualTo("android-Q.jar")
    }

    private fun getClasspath(
        androidVersion: AndroidVersion,
        androidJar: File
    ): Provider<List<RegularFile>> {
        return BootClasspathBuilder.computeClasspath(
            projectLayout = projectLayout,
            providerFactory = FakeProviderFactory.factory,
            objects = FakeObjectFactory.factory,
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
