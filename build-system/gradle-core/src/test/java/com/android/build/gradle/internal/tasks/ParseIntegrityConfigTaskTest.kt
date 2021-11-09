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

package com.android.build.gradle.internal.tasks

import com.android.build.api.dsl.Bundle
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.BundleOptions
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.bundle.AppIntegrityConfigOuterClass.AppIntegrityConfig
import com.android.bundle.AppIntegrityConfigOuterClass.EmulatorCheck
import com.android.bundle.AppIntegrityConfigOuterClass.InstallerCheck
import com.android.bundle.AppIntegrityConfigOuterClass.LicenseCheck
import com.android.bundle.AppIntegrityConfigOuterClass.Policy
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.FileInputStream

class ParseIntegrityConfigTaskTest {

    @get:Rule
    val testFolder = TemporaryFolder()

    lateinit var project: Project

    // Under test
    lateinit var task: ParseIntegrityConfigTask

    private val gradleProperties = ImmutableMap.of<String, Any>()

    private lateinit var taskCreationServices: TaskCreationServices
    private lateinit var dslServices : DslServices

    @Before
    fun setup() {
        project = ProjectBuilder.builder().withProjectDir(testFolder.newFolder()).build()
        task = project.tasks.create("test", ParseIntegrityConfigTask::class.java)
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java), AnalyticsService::class.java) {}

        val projectServices = createProjectServices(
            project = project,
            projectOptions = ProjectOptions(
                ImmutableMap.of(),
                FakeProviderFactory(FakeProviderFactory.factory, gradleProperties)
            )
        )
        taskCreationServices = createTaskCreationServices(projectServices)
        dslServices = createDslServices(projectServices)
    }

    @Test
    fun testParseConfig() {
        val configXML = project.projectDir.resolve("IntegrityConfig.xml")
        FileUtils.writeToFile(configXML, "<IntegrityConfig/>")

        val appIntegrityConfigProto = testFolder.root.resolve("expected_output")
        // Under test
        object : ParseIntegrityConfigTask.ParseIntegrityConfigRunnable() {
            override fun getParameters(): ParseIntegrityConfigTask.Params {
                return object : ParseIntegrityConfigTask.Params() {
                    override val integrityConfigDir =
                        project.objects.directoryProperty().fileValue(project.projectDir)
                    override val appIntegrityConfigProto =
                        project.objects.fileProperty().fileValue(appIntegrityConfigProto)
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()

        assertThat(appIntegrityConfigProto).exists()
        val config = AppIntegrityConfig.parseFrom(
            FileInputStream(appIntegrityConfigProto)
        )
        val defaultConfig = AppIntegrityConfig.newBuilder()
            .setEnabled(true)
            .setLicenseCheck(
                LicenseCheck.newBuilder().setEnabled(false).setPolicy(
                    Policy.newBuilder().setAction(
                        Policy.Action.WARN)
                )
            )
            .setInstallerCheck(
                InstallerCheck.newBuilder().setEnabled(true).setPolicy(
                    Policy.newBuilder().setAction(
                        Policy.Action.WARN)
                )
            )
            .setEmulatorCheck(EmulatorCheck.newBuilder().setEnabled(true))
            .build()
        assertThat(config).isEqualTo(defaultConfig)
    }

    @Test
    fun testParseConfig_disabled() {
        val configXML = project.projectDir.resolve("IntegrityConfig.xml")
        FileUtils.writeToFile(configXML, """<IntegrityConfig enabled="false"/>""")

        val appIntegrityConfigProto = testFolder.root.resolve("expected_output")
        // Under test
        object : ParseIntegrityConfigTask.ParseIntegrityConfigRunnable() {
            override fun getParameters(): ParseIntegrityConfigTask.Params {
                return object : ParseIntegrityConfigTask.Params() {
                    override val integrityConfigDir =
                        project.objects.directoryProperty().fileValue(project.projectDir)
                    override val appIntegrityConfigProto =
                        project.objects.fileProperty().fileValue(appIntegrityConfigProto)
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(appIntegrityConfigProto).exists()
        val config = AppIntegrityConfig.parseFrom(
            FileInputStream(appIntegrityConfigProto)
        )

        assertThat(config.enabled).isFalse()
    }

    private interface BundleWrapper {
        val bundle: Bundle
    }

    @Test
    fun testConfigureTask() {
        val configDirectory = project.projectDir.resolve("test_config")
        val configFileName = "IntegrityConfig.xml"
        val configXML = configDirectory.resolve(configFileName)
        FileUtils.writeToFile(configXML, "<IntegrityConfig/>")

        val bundleOptions = androidPluginDslDecorator.decorate(BundleWrapper::class.java)
            .getDeclaredConstructor(DslServices::class.java)
            .newInstance(dslServices).bundle as BundleOptions
        bundleOptions.integrityConfigDir.set(configDirectory)
        val componentProperties = createScopeFromBundleOptions(bundleOptions)

        val taskAction = ParseIntegrityConfigTask.CreationAction(componentProperties)
        taskAction.preConfigure(task.name)

        // Under test
        taskAction.configure(task)

        assertThat(task.integrityConfigDir.isPresent)
        val configDir = task.integrityConfigDir.asFile.get()
        assertThat(configDir).exists()
        val configFile = configDir.resolve(configFileName)
        assertThat(configFile).exists()
        assertThat(configFile).contains("<IntegrityConfig/>")
    }

    private fun createScopeFromBundleOptions(bundleOptions: BundleOptions): VariantCreationConfig {
        val componentProperties = Mockito.mock(VariantCreationConfig::class.java)
        val variantType = Mockito.mock(VariantType::class.java)
        val extension = Mockito.mock(BaseAppModuleExtension::class.java)
        val globalScope = Mockito.mock(GlobalScope::class.java)
        val variantScope = Mockito.mock(VariantScope::class.java)
        val taskContainer = Mockito.mock(MutableTaskContainer::class.java)
        val preBuildTask = Mockito.mock(TaskProvider::class.java)

        Mockito.`when`(componentProperties.services).thenReturn(taskCreationServices)
        Mockito.`when`(componentProperties.variantType).thenReturn(variantType)
        Mockito.`when`(componentProperties.name).thenReturn("variant")
        Mockito.`when`(componentProperties.taskContainer).thenReturn(taskContainer)
        Mockito.`when`(componentProperties.globalScope).thenReturn(globalScope)
        Mockito.`when`(componentProperties.variantScope).thenReturn(variantScope)
        Mockito.`when`(extension.bundle).thenReturn(bundleOptions)
        Mockito.`when`(globalScope.extension).thenReturn(extension)
        Mockito.`when`(globalScope.dslServices).thenReturn(dslServices)
        Mockito.`when`(taskContainer.preBuildTask).thenReturn(preBuildTask)

        return componentProperties
    }
}
