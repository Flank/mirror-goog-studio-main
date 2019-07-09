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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.google.gson.Gson
import java.io.File

/**
 * Given a json string construct a [BuildSettingsModel].
 */
fun createBuildSettingsFromJson(json: String): BuildSettingsModel {
    return try {
        val settings = Gson().fromJson(json, BuildSettingsModel::class.java)

        // Null checks are required here because Gson deserialization may return null
        if (settings != null) {
            BuildSettingsModel(environmentVariables = settings.environmentVariables
                ?.filterNotNull()
                ?.filter { !it.name.isNullOrBlank() }
                ?: emptyList()
            )
        } else {
            errorln("Json is empty")
            BuildSettingsModel()
        }

    } catch (e: Throwable) {
        errorln(e.message ?: e.cause?.message ?: e.javaClass.name)
        BuildSettingsModel()
    }
}

/**
 * Given a file with json construct [BuildSettingsModel].
 */
fun createBuildSettingsFromFile(jsonFile: File): BuildSettingsModel {
    return if (jsonFile.exists()) {
        PassThroughPrefixingLoggingEnvironment(file = jsonFile).use {
            return createBuildSettingsFromJson(jsonFile.readText())
        }
    } else {
        BuildSettingsModel()
    }
}

/**
 * Converts [BuildSettingsModel] into a name:value Map.
 * Omits environment variables with no name provided.
 */
fun BuildSettingsModel.getEnvironmentVariableMap(): Map<String, String> {
    return environmentVariables.associateBy({ it.name }, { it.value ?: "" })
}
