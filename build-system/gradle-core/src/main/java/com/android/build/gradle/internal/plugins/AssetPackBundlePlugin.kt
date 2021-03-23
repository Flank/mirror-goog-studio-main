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

package com.android.build.gradle.internal.plugins

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.AssetPackBundleExtension
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.NoOpAnalyticsService
import com.android.build.gradle.internal.res.Aapt2FromMaven.Companion.create
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.google.common.io.Files
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class AssetPackBundlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val projectOptions = ProjectOptionService.RegistrationAction(project)
            .execute()
            .get()
            .projectOptions

        val syncIssueHandler = SyncIssueReporterImpl(
            SyncOptions.getModelQueryMode(projectOptions),
            SyncOptions.getErrorFormatMode(projectOptions),
            project.logger
        )

        val deprecationReporter =
            DeprecationReporterImpl(syncIssueHandler, projectOptions, project.path)

        val projectServices = ProjectServices(
            syncIssueHandler,
            deprecationReporter,
            project.objects,
            project.logger,
            project.providers,
            project.layout,
            projectOptions,
            project.gradle.sharedServices,
            create(project, projectOptions),
            project.gradle.startParameter.maxWorkerCount,
            ProjectInfo(project),
            project::file
        )
        registerServices(project, projectOptions)

        val dslServices = DslServicesImpl(
            projectServices,
            sdkComponents = projectServices.providerFactory.provider { null }
        )
        val extension =
            dslServices.newDecoratedInstance(AssetPackBundleExtension::class.java, dslServices)
        project.extensions.add(AssetPackBundleExtension::class.java, "bundle", extension)

        project.afterEvaluate {
            createAssetOnlyTasks(project, projectServices, extension)
        }
    }

    private fun registerServices(project: Project, projectOptions: ProjectOptions) {
        if (projectOptions.isAnalyticsEnabled) {
            AnalyticsService.RegistrationAction(project).execute()
        } else {
            project.gradle.sharedServices.registerIfAbsent(
                getBuildServiceName(AnalyticsService::class.java),
                NoOpAnalyticsService::class.java,
            ) {}
        }
    }

    private fun createAssetOnlyTasks(
        project: Project,
        projectServices: ProjectServices,
        extension: AssetPackBundleExtension
    ) {
        val tasks = TaskFactoryImpl(project.tasks)
        val artifacts = ArtifactsImpl(project, "global")

        registerDummyTask(projectServices, extension, tasks, artifacts)

        tasks.register(
            "bundle",
            null,
            object : TaskConfigAction<Task> {
                override fun configure(task: Task) {
                    task.description = "Assembles asset pack bundle for asset only updates"
                    task.dependsOn(artifacts.get(ArtifactType.BUNDLE))
                }
            }
        )
    }

    private fun registerDummyTask(
        projectServices: ProjectServices,
        extension: AssetPackBundleExtension,
        tasks: TaskFactory,
        artifacts: ArtifactsImpl
    ) {
        val buildDirectory =
            ArtifactType.BUNDLE.getOutputPath(artifacts.buildDirectory, "").absolutePath
        val outputFileName = "${projectServices.projectInfo.getProjectBaseName()}.aab"

        val outputProperty = projectServices.objectFactory.fileProperty()
        tasks.register(
            "dummyTask"
        ) {
            it.outputs.file(outputProperty)
            it.doLast {
                Files.write(extension.versionTag.toByteArray(), outputProperty.asFile.get())
            }
        }.also {
            artifacts.setInitialProvider(it, { outputProperty })
                .atLocation(buildDirectory)
                .withName(outputFileName)
                .on(ArtifactType.BUNDLE)
        }
    }
}
