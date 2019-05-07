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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.services.createBuildModelServiceRegistry
import com.android.build.gradle.internal.cxx.services.runFinishListeners
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import java.util.UUID

/**
 * This instance is per classloader so there could be multiple build models, in the same build if
 * different sub projects are loaded by different classloaders due to different class path
 * configurations in each subproject build.gradle file.
 */
private var buildModel : CxxBuildModel? = null

private object GetCxxBuildModelLock

/**
 * Get the current build model.
 */
fun getCxxBuildModel(gradle: Gradle): CxxBuildModel = synchronized(GetCxxBuildModelLock) {
    if (buildModel == null) {
        buildModel = object : CxxBuildModel {
            override val buildId = UUID.randomUUID()
            override val services = createBuildModelServiceRegistry()
        }
        fun Gradle.findRoot(): Gradle = gradle.parent?.findRoot() ?: this
        val rootGradle = gradle.findRoot()
        rootGradle.addBuildListener(object : BuildListener {
            override fun settingsEvaluated(ignored: Settings) {}
            override fun projectsLoaded(ignored: Gradle) {}
            override fun buildStarted(ignored: Gradle) {}
            override fun projectsEvaluated(ignored: Gradle) {}
            override fun buildFinished(ignored: BuildResult) {
                try {
                    buildModel?.runFinishListeners()
                } finally {
                    buildModel = null
                }
            }
        })
    }
    return buildModel!!
}
