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

package com.android.build.gradle.internal.plugins

import com.android.Version
import com.android.build.gradle.internal.attribution.BuildAttributionService
import com.android.build.gradle.internal.attribution.BuildAttributionService.Companion.init
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.lint.LintFromMaven.Companion.from
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.profile.NoOpAnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.NoOpAnalyticsService
import com.android.build.gradle.internal.registerDependencyCheck
import com.android.build.gradle.internal.res.Aapt2FromMaven.Companion.create
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.IssueReporter.Type
import com.google.common.base.CharMatcher
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.build.event.BuildEventsListenerRegistry
import java.util.Locale

@Suppress("UnstableApiUsage")
abstract class AndroidPluginBaseServices(
    private val listenerRegistry: BuildEventsListenerRegistry
) {

    private val optionService: ProjectOptionService by lazy {
        withProject("optionService") {
            ProjectOptionService.RegistrationAction(it).execute().get()
        }
    }

    protected val syncIssueReporter: SyncIssueReporterImpl by lazy {
        withProject("syncIssueReporter") {
            SyncIssueReporterImpl(
                SyncOptions.getModelQueryMode(optionService.projectOptions),
                SyncOptions.getErrorFormatMode(optionService.projectOptions),
                it.logger
            )
        }
    }

    @JvmField
    protected var project: Project? = null

    protected val projectServices: ProjectServices by lazy {
        withProject("projectServices") { project ->
            val projectOptions = optionService.projectOptions
            ProjectServices(
                syncIssueReporter,
                DeprecationReporterImpl(syncIssueReporter, projectOptions, project.path),
                project.objects,
                project.logger,
                project.providers,
                project.layout,
                projectOptions,
                project.gradle.sharedServices,
                from(project, projectOptions, syncIssueReporter),
                create(project, projectOptions),
                project.gradle.startParameter.maxWorkerCount,
                ProjectInfo(project)
            ) { o: Any -> project.file(o) }
        }
    }

    protected val configuratorService: AnalyticsConfiguratorService by lazy {
        withProject("configuratorService") { project ->
            val projectOptions: ProjectOptions = projectServices.projectOptions
            if (projectOptions.isAnalyticsEnabled) {
                AnalyticsConfiguratorService.RegistrationAction(project).execute().get()
            } else {
                project.gradle.sharedServices.registerIfAbsent(
                    getBuildServiceName(AnalyticsConfiguratorService::class.java),
                    NoOpAnalyticsConfiguratorService::class.java
                ) { }.get()
            }
        }
    }

    protected open fun basePluginApply(project: Project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true")

        this.project = project
        AndroidLocationsBuildService.RegistrationAction(project).execute()
        checkMinJvmVersion()
        val projectOptions: ProjectOptions = projectServices.projectOptions
        if (projectOptions.isAnalyticsEnabled) {
            AnalyticsService.RegistrationAction(project).execute()
        } else {
            project.gradle.sharedServices.registerIfAbsent(
                getBuildServiceName(AnalyticsService::class.java),
                NoOpAnalyticsService::class.java
            ) { }
        }
        registerDependencyCheck(project, projectOptions)
        checkPathForErrors()
        val attributionFileLocation =
            projectOptions.get(StringOption.IDE_ATTRIBUTION_FILE_LOCATION)
        if (attributionFileLocation != null) {
            BuildAttributionService.RegistrationAction(project).execute()
            init(
                project, attributionFileLocation, listenerRegistry
            )
        }
        configuratorService.createAnalyticsService(project, listenerRegistry)
        configuratorService.getProjectBuilder(project.path)?.let {
            it
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST).options =
                AnalyticsUtil.toProto(projectOptions)
        }
        configuratorService.recordBlock(
            ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
            project.path,
            null,
            this::configureProject
        )
        configuratorService.recordBlock(
            ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
            project.path,
            null,
            this::configureExtension
        )
        configuratorService.recordBlock(
            ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
            project.path,
            null,
            this::createTasks
        )
    }

    private fun checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").lowercase(Locale.US).contains("windows")) {
            return
        }

        // See if the user disabled the check:
        if (projectServices.projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)) {
            return
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ascii().matchesAllOf(project!!.rootDir.absolutePath)) {
            return
        }
        val message = ("Your project path contains non-ASCII characters. This will most likely "
                + "cause the build to fail on Windows. Please move your project to a different "
                + "directory. See http://b.android.com/95744 for details. "
                + "This warning can be disabled by adding the line '"
                + BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY.propertyName
                + "=true' to gradle.properties file in the project directory.")
        throw StopExecutionException(message)
    }

    protected open fun checkMinJvmVersion() {
        val current: JavaVersion = JavaVersion.current()
        val minRequired: JavaVersion = JavaVersion.VERSION_11
        if (!current.isCompatibleWith(minRequired)) {
            syncIssueReporter.reportError(
                Type.AGP_USED_JAVA_VERSION_TOO_LOW,
                "Android Gradle plugin requires Java $minRequired to run. " +
                        "You are currently using Java $current.\n Your current JDK is located " +
                        "in ${System.getProperty("java.home")}\n " +
                        "You can try some of the following options:\n" +
                        "  - changing the IDE settings.\n" +
                        "  - changing the JAVA_HOME environment variable.\n" +
                        "  - changing `org.gradle.java.home` in `gradle.properties`."
            )
        }
    }

    protected abstract fun configureProject()

    protected abstract fun configureExtension()

    protected abstract fun createTasks()

    protected abstract fun getAnalyticsPluginType(): GradleBuildProject.PluginType?

    /**
     * Runs a lambda function if [project] has been initialized and return the function's result or
     * generate an exception if [project] is null.
     *
     * This is useful to have not nullable val field that depends on [project] being initialized.
     */
    private fun <T> withProject(context: String, action: (project: Project) -> T): T =
        project?.let {
            action(it)
        } ?: throw IllegalStateException("Cannot obtain $context until Project is known")
}
