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

package com.android.build.gradle.internal.ide.v2

import com.android.build.api.component.impl.TestComponentPropertiesImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.variant.VariantInputModelBuilder
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.build.gradle.options.SyncOptions
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.google.common.io.Files
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.lang.RuntimeException

class ModelBuilderTest {

    @get:Rule
    val thrown: ExpectedException = ExpectedException.none()

    @Test
    fun `test canBuild`() {
        fun testModel(name: String) {
            Truth.assertThat(createModelBuilder().canBuild(name))
                .named("can build '$name")
                .isTrue()
        }

        testModel(AndroidProject::class.java.name)
        testModel(GlobalLibraryMap::class.java.name)
        testModel(VariantDependencies::class.java.name)
        testModel(ProjectSyncIssues::class.java.name)
    }

    @Test
    fun `test wrong model`() {
        Truth.assertThat(createModelBuilder().canBuild("com.FooModel"))
            .named("can build com.FooModel")
            .isFalse()

        thrown.expect(RuntimeException::class.java)
        thrown.expectMessage("Does not support model 'com.FooModel'")
        createModelBuilder().buildAll("com.FooModel", createProject())
    }

    @Test
    fun `test wrong query with VariantDependencies`() {
        thrown.expect(RuntimeException::class.java)
        thrown.expectMessage("Please use parameterized Tooling API to obtain VariantDependencies model.")
        createModelBuilder().buildAll(VariantDependencies::class.java.name, createProject())
    }

    @Test
    fun `test wrong query with AndroidProject`() {
        thrown.expect(RuntimeException::class.java)
        val name = AndroidProject::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createModelBuilder().buildAll(name, FakeModelBuilderParameter(), createProject())
    }

    @Test
    fun `test wrong query with GlobalLibraryMap`() {
        thrown.expect(RuntimeException::class.java)
        val name = GlobalLibraryMap::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createModelBuilder().buildAll(name, FakeModelBuilderParameter(), createProject())
    }

    @Test
    fun `test wrong query with ProjectSyncIssues`() {
        thrown.expect(RuntimeException::class.java)
        val name = ProjectSyncIssues::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createModelBuilder().buildAll(name, FakeModelBuilderParameter(), createProject())
    }

    //---------------

    private val syncIssueReporter = SyncIssueReporterImpl(SyncOptions.EvaluationMode.IDE, FakeLogger())
    private val variantList: MutableList<VariantPropertiesImpl> = mutableListOf()
    private val testComponentList: MutableList<TestComponentPropertiesImpl> = mutableListOf()

    private fun createModelBuilder(): ModelBuilder<BaseExtension> {
        return ModelBuilder<BaseExtension>(createVariantModel())
    }

    private fun createVariantModel() : VariantModel = VariantModelImpl(
        VariantInputModelBuilder(createDslServices()).toModel(),
        { "debug" },
        { variantList },
        { testComponentList },
        syncIssueReporter
    )

    private fun createProject() : Project =
        ProjectBuilder.builder().withProjectDir(Files.createTempDir()).build()

    data class FakeModelBuilderParameter(
        override val variantName: String = "foo",
        override val abiName: String? = null
    ) : ModelBuilderParameter

}