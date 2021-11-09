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

@file:JvmName("FakeServices")
package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.res.Aapt2FromMaven
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.options.ProjectOptions
import com.android.testutils.TestUtils
import com.google.common.collect.ImmutableMap
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceRegistry
import java.io.File

@JvmOverloads
fun createProjectServices(
    project: Project = ProjectFactory.project,
    issueReporter: SyncIssueReporter = FakeSyncIssueReporter(),
    deprecationReporter: DeprecationReporter = FakeDeprecationReporter(),
    objectFactory: ObjectFactory = project.objects,
    logger: Logger = FakeLogger(),
    providerFactory: ProviderFactory = project.providers,
    projectOptions: ProjectOptions = ProjectOptions(ImmutableMap.of(), FakeProviderFactory(providerFactory, ImmutableMap.of())),
    fileResolver: (Any) -> File = { File(it.toString()) }
): ProjectServices =
    ProjectServices(
        issueReporter,
        deprecationReporter,
        objectFactory,
        logger,
        providerFactory,
        project.layout,
        projectOptions,
        project.gradle.sharedServices,
        lintFromMaven = LintFromMaven(objectFactory.fileCollection(), "invalid lint"),
        aapt2FromMaven = Aapt2FromMaven(objectFactory.fileCollection().from(TestUtils.getAapt2().parent), "test-aapt2"),
        maxWorkerCount = 1,
        ProjectInfo(project),
        fileResolver = fileResolver
    )

@JvmOverloads
fun createDslServices(
    projectServices: ProjectServices = createProjectServices(),
    sdkComponents: Provider<SdkComponentsBuildService> = FakeGradleProvider(null)
): DslServices {
    return DslServicesImpl(projectServices, sdkComponents)
}

@JvmOverloads
fun createVariantApiServices(
    projectServices: ProjectServices = createProjectServices()
): VariantApiServices = VariantApiServicesImpl(projectServices)

@JvmOverloads
fun createVariantPropertiesApiServices(
    projectServices: ProjectServices = createProjectServices()
): VariantPropertiesApiServices = VariantPropertiesApiServicesImpl(
    projectServices = projectServices,
    forUnitTesting = true,
)

@JvmOverloads
fun createTaskCreationServices(
    projectServices: ProjectServices = createProjectServices()
): TaskCreationServices = TaskCreationServicesImpl(projectServices)
