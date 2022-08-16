/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.detector.api.LintFix
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class GooglePlaySdkIndexTest {
    private lateinit var proto: Index
    private lateinit var index: GooglePlaySdkIndex

    @Before
    fun prepareIndex() {
        proto = Index.newBuilder()
            .addSdks(
                Sdk.newBuilder()
                    .setIndexUrl("http://index.example.url/")
                    .addLibraries(
                        Library.newBuilder()
                            .setLibraryId(
                                LibraryIdentifier.newBuilder()
                                    .setMavenId(
                                        LibraryIdentifier.MavenIdentifier.newBuilder()
                                            .setGroupId("log4j")
                                            .setArtifactId("log4j")
                                            .build()
                                    )
                            )
                            // Ok, latest, no issues
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("1.2.18")
                                    .setIsLatestVersion(true)
                            )
                            // Ok, latest, no issues
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("1.2.17")
                                    .setIsLatestVersion(false)
                            )
                            // Critical
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("1.2.16")
                                    .setIsLatestVersion(false)
                                    .setVersionLabels(
                                        LibraryVersionLabels.newBuilder()
                                            .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                                    )
                            )
                            // Outdated
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("1.2.15")
                                    .setIsLatestVersion(false)
                                    .setVersionLabels(
                                        LibraryVersionLabels.newBuilder()
                                            .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                                    )
                            )
                            // Policy
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("1.2.14")
                                    .setIsLatestVersion(false)
                                    .setVersionLabels(
                                        LibraryVersionLabels.newBuilder()
                                            .setNonCompliantIssueInfo(LibraryVersionLabels.NonCompliantPolicyInfo.newBuilder())
                                    )
                            )
                    )
            )
            .addSdks(
                Sdk.newBuilder()
                    .setIndexUrl("http://another.example.url/")
                    .addLibraries(
                        Library.newBuilder()
                            .setLibraryId(
                                LibraryIdentifier.newBuilder()
                                    .setMavenId(
                                        LibraryIdentifier.MavenIdentifier.newBuilder()
                                            .setGroupId("com.example.ads.third.party")
                                            .setArtifactId("example")
                                            .build()
                                    )
                            )
                            // Ok, latest
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("8.0.0")
                                    .setIsLatestVersion(true)
                            )
                            // Ok
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("7.2.2")
                                    .setIsLatestVersion(false)
                            )
                            // Ok
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("7.2.1")
                                    .setIsLatestVersion(false)
                            )
                            // Outdated & non compliant & Critical
                            .addVersions(
                                LibraryVersion.newBuilder()
                                    .setVersionString("7.2.0")
                                    .setIsLatestVersion(false)
                                    .setVersionLabels(
                                        LibraryVersionLabels.newBuilder()
                                            .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                                            .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                                            .setNonCompliantIssueInfo(LibraryVersionLabels.NonCompliantPolicyInfo.newBuilder())
                                    )
                            )
                    )
            )
            .build()
        index = object : GooglePlaySdkIndex() {
            override fun readUrlData(url: String, timeout: Int): ByteArray? {
                fail("Trying to read proto from the network!")
                return null
            }

            override fun readDefaultData(relative: String): InputStream? {
                fail("Trying to read default proto!")
                return null
            }

            override fun error(throwable: Throwable, message: String?) {
            }
        }
        index.initialize(ByteArrayInputStream(proto.toByteArray()))
        assertThat(index.getLastReadSource()).isEqualTo(NetworkCache.DataSourceType.TEST_DATA)
    }

    @Test
    fun `outdated shown if showMessages is enabled`() {
        index.showMessages = true
        assertThat(hasOutdatedIssues()).isTrue()
    }

    @Test
    fun `outdated not shown if showMessages is disabled`() {
        index.showMessages = false
        assertThat(hasOutdatedIssues()).isFalse()
    }

    @Test
    fun `critical shown if showMessages and showCritical are enabled`() {
        index.showMessages = true
        index.showCriticalIssues = true
        assertThat(hasCriticalIssues()).isTrue()
    }

    @Test
    fun `critical not shown if showMessages is enabled but showCritical not`() {
        index.showMessages = true
        index.showCriticalIssues = false
        assertThat(hasCriticalIssues()).isFalse()
    }

    @Test
    fun `critical not shown if showMessages and showCritical are not enabled`() {
        index.showMessages = false
        index.showCriticalIssues = false
        assertThat(hasCriticalIssues()).isFalse()
    }

    @Test
    fun `policy issues shown if showMessages and showPolicyIssues are enabled`() {
        index.showMessages = true
        index.showPolicyIssues = true
        assertThat(hasPolicyIssues()).isTrue()
    }

    @Test
    fun `policy issues not shown if showMessages is enabled but showPolicyIssues not`() {
        index.showMessages = true
        index.showPolicyIssues = false
        assertThat(hasPolicyIssues()).isFalse()
    }

    @Test
    fun `policy issues not shown if showMessages and showPolicyIssues are not enabled`() {
        index.showMessages = false
        index.showPolicyIssues = false
        assertThat(hasPolicyIssues()).isFalse()
    }

    @Test
    fun `all links are present when showLinks is enabled`() {
        index.showLinks = true
        for (sdk in proto.sdksList) {
            val expectedUrl = sdk.indexUrl
            for (library in sdk.librariesList) {
                val group = library.libraryId.mavenId.groupId
                val artifact = library.libraryId.mavenId.artifactId
                val lintLink = index.generateSdkLinkLintFix(group, artifact, versionString = "noVersion", buildFile = null)
                assertThat(lintLink).isNotNull()
                assertThat(lintLink).isInstanceOf(LintFix.ShowUrl::class.java)
                assertThat((lintLink as LintFix.ShowUrl).url).isEqualTo(expectedUrl)
            }
        }
    }

    @Test
    fun `no links are present when showLinks is not enabled`() {
        index.showLinks = false
        for (sdk in proto.sdksList) {
            for (library in sdk.librariesList) {
                val group = library.libraryId.mavenId.groupId
                val artifact = library.libraryId.mavenId.artifactId
                val lintLink = index.generateSdkLinkLintFix(group, artifact, versionString = "noVersion", buildFile = null)
                assertThat(lintLink).isNull()
            }
        }
    }

    private fun hasOutdatedIssues(): Boolean {
        for (sdk in proto.sdksList) {
            for (library in sdk.librariesList) {
                val group = library.libraryId.mavenId.groupId
                val artifact = library.libraryId.mavenId.artifactId
                for (version in library.versionsList) {
                    if (index.isLibraryOutdated(group, artifact, version.versionString, null)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasPolicyIssues(): Boolean {
        for (sdk in proto.sdksList) {
            for (library in sdk.librariesList) {
                val group = library.libraryId.mavenId.groupId
                val artifact = library.libraryId.mavenId.artifactId
                for (version in library.versionsList) {
                    if (index.isLibraryNonCompliant(group, artifact, version.versionString, null)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasCriticalIssues(): Boolean {
        for (sdk in proto.sdksList) {
            for (library in sdk.librariesList) {
                val group = library.libraryId.mavenId.groupId
                val artifact = library.libraryId.mavenId.artifactId
                for (version in library.versionsList) {
                    if (index.hasLibraryCriticalIssues(group, artifact, version.versionString, null)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
