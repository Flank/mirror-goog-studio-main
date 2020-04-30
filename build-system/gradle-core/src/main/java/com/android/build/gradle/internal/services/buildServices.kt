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

package com.android.build.gradle.internal.services

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration

/** Registers and configures the build service with the specified type. */
abstract class ServiceRegistrationAction<ServiceT, ParamsT>(
    protected val project: Project,
    private val buildServiceClass: Class<ServiceT>
) where ServiceT : BuildService<ParamsT>, ParamsT : BuildServiceParameters {
    fun execute(): Provider<ServiceT> {
        return project.gradle.sharedServices.registerIfAbsent(
            buildServiceClass.name,
            buildServiceClass
        ) {
            it.parameters?.let { params -> configure(params) }
        }
    }

    abstract fun configure(parameters: ParamsT)
}

/** Returns the build service with the specified type. Prefer reified [getBuildService] to this method. */
fun <ServiceT : BuildService<out BuildServiceParameters>> getBuildService(
    project: Project,
    buildServiceClass: Class<ServiceT>
): Provider<ServiceT> {
    @Suppress("UNCHECKED_CAST")
    return (project.gradle.sharedServices.registrations.getByName(buildServiceClass.name) as BuildServiceRegistration<ServiceT, *>).getService()
}

/** Returns the build service of [ServiceT] type. */
inline fun <reified ServiceT : BuildService<out BuildServiceParameters>> getBuildService(project: Project): Provider<ServiceT> {
    @Suppress("UNCHECKED_CAST")
    return (project.gradle.sharedServices.registrations.getByName(ServiceT::class.java.name) as BuildServiceRegistration<ServiceT, *>).getService()
}
