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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxBuildModel

/**
 * Create and register a [CxxProcessService] for holding the complete data model for the
 * whole build
 */
internal fun createCompleteModelService(services: CxxServiceRegistryBuilder) {
    services.registerFactory(COMPLETE_MODEL_SERVICE_KEY) {
        CxxCompleteModelService()
    }
}

/**
 * Register a [CxxAbiModel] as part of the complete build model.
 * All [CxxAbiModel]s will be registered here.
 */
fun CxxBuildModel.registerCompleteModelAbi(abi : CxxAbiModel) {
    services[COMPLETE_MODEL_SERVICE_KEY].abis += abi
}

/**
 * Retrieve all [CxxAbiModel]s for this build.
 */
fun CxxBuildModel.completeModelAbis() : List<CxxAbiModel> {
    return services[COMPLETE_MODEL_SERVICE_KEY].abis
}

/**
 * Private service key for overall data model for the build.
 */
private val COMPLETE_MODEL_SERVICE_KEY = object : CxxServiceKey<CxxCompleteModelService> {
    override val type = CxxCompleteModelService::class.java
}

/**
 * Private interface to access the process service via [CxxServiceRegistry].
 */
private data class CxxCompleteModelService(
    val abis : MutableList<CxxAbiModel> = mutableListOf()
)