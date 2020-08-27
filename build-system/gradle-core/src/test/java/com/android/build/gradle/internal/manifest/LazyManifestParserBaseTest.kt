/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.manifest

import com.android.build.gradle.internal.services.createProjectServices
import com.android.builder.model.SyncIssue
import com.android.testutils.AbstractBuildGivenBuildExpectTest
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.function.BooleanSupplier

/**
 * Basic tests for [LazyManifestParser]
 */
internal abstract class LazyManifestParserBaseTest :
    AbstractBuildGivenBuildExpectTest<
            LazyManifestParserBaseTest.GivenBuilder,
            LazyManifestParserBaseTest.Result>() {

    // no tests here

    // ---------------------------------------------------------------------------------------------

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val projectServices = createProjectServices()
    private var issueChecker: ((List<SyncIssue>) -> Unit)? = null

    fun withIssueChecker(action: (List<SyncIssue>) -> Unit) {
        checkState(TestState.GIVEN)
        issueChecker = action
    }

    override fun instantiateGiven() = GivenBuilder()
    override fun instantiateResult() = Result()

    override fun defaultWhen(given: GivenBuilder): Result? {

        val manifestValue = given.manifest

        val manifestFile = given.manifestFile
            ?: if (manifestValue != null) {
                temporaryFolder.newFile("AndroidManifest.xml").also {
                    it.writeText(manifestValue)
                }
            } else {
                File("/path/to/no/manifest")
            }

        val manifestFileProperty = projectServices.objectFactory.fileProperty().fileValue(manifestFile)

        val manifestParser = LazyManifestParser(
            manifestFile = manifestFileProperty,
            manifestFileRequired = given.manifestFileIsRequired,
            projectServices = projectServices,
            manifestParsingAllowed = BooleanSupplier{ !given.earlyManifestParsingCheck }
        )

        return Result(
            manifestParser.manifestData.get(),
            projectServices.issueReporter.syncIssues
        )
    }

    override fun compareResult(
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") expectedMaybe: Result?,
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") actualMaybe: Result?,
        given: GivenBuilder
    ) {
        val expected = expectedMaybe?.data ?: throw RuntimeException("expected is null")
        val actual = actualMaybe?.data ?: throw RuntimeException("actual is null")

        // only check the cases where expected value is not null

        expected.packageName?.let {
            Truth.assertThat(actual.packageName).named("packageName").isEqualTo(it)
        }

        expected.split?.let {
            Truth.assertThat(actual.split).named("split").isEqualTo(it)
        }

        expected.minSdkVersion?.let {
            Truth.assertThat(actual.minSdkVersion).named("minSdkVersion").isEqualTo(it)
        }

        expected.targetSdkVersion?.let {
            Truth.assertThat(actual.targetSdkVersion).named("targetSdkVersion").isEqualTo(it)
        }

        expected.instrumentationRunner?.let {
            Truth.assertThat(actual.instrumentationRunner).named("instrumentationRunner").isEqualTo(it)
        }

        expected.testLabel?.let {
            Truth.assertThat(actual.testLabel).named("testLabel").isEqualTo(it)
        }

        expected.functionalTest?.let {
            Truth.assertThat(actual.functionalTest).named("functionalTest").isEqualTo(it)
        }

        expected.handleProfiling?.let {
            Truth.assertThat(actual.handleProfiling).named("handleProfiling").isEqualTo(it)
        }

        expected.extractNativeLibs?.let {
            Truth.assertThat(actual.extractNativeLibs).named("extractNativeLibs").isEqualTo(it)
        }

        expected.useEmbeddedDex?.let {
            Truth.assertThat(actual.useEmbeddedDex).named("useEmbeddedDex").isEqualTo(it)
        }

        // finally check for errors
        issueChecker?.invoke(actualMaybe.issues) ?: run {
            // conver the normal SyncIssue returned into fake ones in order to compare them.
            Truth.assertThat(actualMaybe.issues.map { it.toFake() }).isEqualTo(expectedMaybe.issues)
        }
    }

    class GivenBuilder {
        var manifest: String? = null
        var manifestFile: File? = null
        var manifestFileIsRequired = true
        var earlyManifestParsingCheck = false
    }

    internal data class FakeSyncIssue(
        override val severity: Int = -1,
        override val type: Int = -1,
        override val data: String? = null,
        override val message: String = "",
        override val multiLineMessage: List<String>? = null
    ): SyncIssue

    private fun SyncIssue.toFake(): SyncIssue = FakeSyncIssue(
        this.severity,
        this.type,
        this.data,
        this.message,
        this.multiLineMessage
    )

    internal class SyncIssueBuilder(
        override var severity: Int = -1,
        override var type: Int = -1,
        override var data: String? = null,
        override var message: String = "",
        override var multiLineMessage: List<String>? = null
    ): SyncIssue {
        fun toFake(): SyncIssue = FakeSyncIssue(
            this.severity,
            this.type,
            this.data,
            this.message,
            this.multiLineMessage
        )
    }

    internal class Result(
        val data: ManifestData = ManifestData(),
        val issues: MutableList<SyncIssue> = mutableListOf()
    ) {
        fun data(action: ManifestData.() -> Unit) {
            action(data)
        }

        fun issue(action: SyncIssueBuilder.() -> Unit) {
            val builder = SyncIssueBuilder().also {
                action(it)
            }
            issues.add(builder.toFake())
        }

        override fun toString(): String {
            return "Result(data=$data, issues=$issues)"
        }
    }
}
