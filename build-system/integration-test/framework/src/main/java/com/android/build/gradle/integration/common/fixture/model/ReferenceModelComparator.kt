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

package com.android.build.gradle.integration.common.fixture.model

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.testprojects.TestProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.TestProjectBuilderImpl
import org.junit.Rule

/**
 * Compare models to golden files that contains only the delta to a reference project state.
 *
 * This is meant to be used as a base class for tests running sync.
 */
abstract class ReferenceModelComparator(
    /**
     * A lambda that configures the base project state to be compared against
     */
    referenceConfig: TestProjectBuilder.() -> Unit,
    /**
     * A lambda that configures the project with the modification.
     * This is applied on top of [referenceConfig]
     */
    deltaConfig: TestProjectBuilder.() -> Unit,
    /**
     * Sync options to be used when syncing both the base state and the modified project state.
     */
    private val syncOptions: ModelBuilderV2.() -> ModelBuilderV2 = { this }
) : BaseModelComparator {

    private val referenceBuilder = createBaseProject(referenceConfig)
    private val deltaBuilder = createBaseProject(referenceConfig).also(deltaConfig)

    @get:Rule
    val referenceProject = GradleTestProject.builder()
        .withName("referenceProject")
        .fromTestApp(referenceBuilder)
        .create()

    @get:Rule
    val deltaProject =
            GradleTestProject.builder().fromTestApp(deltaBuilder).create()

    protected fun modelV2() : ModelBuilderV2 {
        return syncOptions(deltaProject.modelV2())
    }

    fun compareAndroidProjectWith(goldenFileSuffix: String) {
        val referenceResult = syncOptions(referenceProject.modelV2()).fetchModels()
        val result = syncOptions(deltaProject.modelV2()).fetchModels()

        Comparator(this, result, referenceResult).compare(
            model = result.container.singleAndroidProject,
            referenceModel = referenceResult.container.singleAndroidProject,
            goldenFile = goldenFileSuffix
        )
   }

    fun compareVariantDependenciesWith(
        variantName: String,
        goldenFileSuffix: String
    ) {
        val referenceResult = syncOptions(referenceProject.modelV2()).fetchVariantDependencies(variantName)
        val result = syncOptions(deltaProject.modelV2()).fetchVariantDependencies(variantName)

        Comparator(this, result, referenceResult).compare(
            model = result.container.singleVariantDependencies,
            goldenFile = goldenFileSuffix
        )
    }

    companion object {
        private fun createBaseProject(action: TestProjectBuilder.() -> Unit): TestProjectBuilderImpl {
            val builder = TestProjectBuilderImpl()
            action(builder)

            return builder
        }
    }
}