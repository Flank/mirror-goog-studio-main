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

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.BundleOptions
import com.android.build.gradle.internal.dsl.NoOpDeprecationReporter
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.testutils.truth.FileSubject
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito

class ParseIntegrityConfigTaskTest {

    @get:Rule
    val testFolder = TemporaryFolder()

    lateinit var project: Project

    // Under test
    lateinit var task: ParseIntegrityConfigTask

    @Before
    fun setup() {
        project = ProjectBuilder.builder().withProjectDir(testFolder.newFolder()).build()
        task = project.tasks.create("test", ParseIntegrityConfigTask::class.java)
    }

    @Test
    fun testParseConfig() {
        val configXML = project.projectDir.resolve("IntegrityConfig.xml")
        FileUtils.writeToFile(configXML, "<integrity_config/>")
        val params = ParseIntegrityConfigTask.Params(
            project.projectDir,
            testFolder.newFile()
        )

        // Under test
        ParseIntegrityConfigTask.ParseIntegrityConfigRunnable(params).run()

        assertThat(params.appIntegrityConfigProto.exists())
    }

    @Test
    fun testConfigureTask() {
        val configDirectory = project.projectDir.resolve("test_config")
        val configFileName = "IntegrityConfig.xml"
        val configXML = configDirectory.resolve(configFileName)
        FileUtils.writeToFile(configXML, "<integrity_config/>")

        val bundleOptions = BundleOptions(project.objects, NoOpDeprecationReporter())
        bundleOptions.integrityConfigDir.set(configDirectory)
        val variantScope = createScopeFromBundleOptions(bundleOptions)

        val taskAction = ParseIntegrityConfigTask.CreationAction(variantScope)
        taskAction.preConfigure(task.name)

        // Under test
        taskAction.configure(task)

        assertThat(task.integrityConfigDir.isPresent)
        val configDir = task.integrityConfigDir.asFile.get()
        FileSubject.assertThat(configDir).exists()
        val configFile = configDir.resolve(configFileName)
        FileSubject.assertThat(configFile).exists()
        FileSubject.assertThat(configFile).contains("<integrity_config/>")
    }

    private fun createScopeFromBundleOptions(bundleOptions: BundleOptions): VariantScope {
        val variantType = Mockito.mock(VariantType::class.java)
        val extension = Mockito.mock(BaseAppModuleExtension::class.java)
        val globalScope = Mockito.mock(GlobalScope::class.java)
        val variantScope = Mockito.mock(VariantScope::class.java)
        val taskContainer = Mockito.mock(MutableTaskContainer::class.java)
        val preBuildTask = Mockito.mock(TaskProvider::class.java)
        val projectOptions = ProjectOptions(
            ImmutableMap.of<String, Any>(
                BooleanOption.ENABLE_GRADLE_WORKERS.propertyName,
                false
            )
        )

        Mockito.`when`(variantScope.globalScope).thenReturn(globalScope)
        Mockito.`when`(extension.bundle).thenReturn(bundleOptions)
        Mockito.`when`(globalScope.extension).thenReturn(extension)
        Mockito.`when`(globalScope.projectOptions).thenReturn(projectOptions)
        Mockito.`when`(variantScope.type).thenReturn(variantType)
        Mockito.`when`(variantScope.name).thenReturn("variant")
        Mockito.`when`(variantScope.taskContainer).thenReturn(taskContainer)
        Mockito.`when`(taskContainer.preBuildTask).thenReturn(preBuildTask)

        return variantScope
    }
}
