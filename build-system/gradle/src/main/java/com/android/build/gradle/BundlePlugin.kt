/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.dsl.BundleExtensionImpl
import com.android.build.gradle.internal.tasks.BundleTask
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Plugin
import org.gradle.api.Project

class BundlePlugin: Plugin<Project> {

    override fun apply(project: Project) {
        BasePlugin.checkGradleVersion(
                project, LoggerWrapper(project.logger), ProjectOptions(project))

        // create the extension
        val extension = project.extensions.create("android", BundleExtensionImpl::class.java, project)

        // create the single task
        val task = project.tasks.create("bundle", BundleTask::class.java)
        task.extension = extension
        task.outputFile = project.layout.buildDirectory.file("bundle.zip")
    }
}