/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.structureplugin

import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

class ASPoetInfo {
    val poetVersion: String = "0.1"
    lateinit var gradleVersion: String
    var agpVersion: String = ""
    var modules: MutableList<ModuleInfo> = mutableListOf()

    fun saveAsJsonTo(to: File) {
        FileUtils.mkdirs(to.parentFile)
        Files.asCharSink(to, Charsets.UTF_8).write(toJson())
    }

    fun toJson() : String {
        val gson = GsonBuilder()
            .registerTypeAdapter(ASPoetInfo::class.java, ASPoetInfoJsonAdapter())
            .setPrettyPrinting()
            .create()
        return gson.toJson(this)
    }
}

class ModuleInfo {
    lateinit var path: String
    var type = ModuleType.PURE
    var javaPackageCount: Int = 0
    var javaClassCount: Int = 0
    var javaMethodsPerClass: Int = 0
    var useKotlin: Boolean = false
    var kotlinPackageCount: Int = 0
    var kotlinClassCount: Int = 0
    var kotlinMethodsPerClass: Int = 0
    var dependencies: MutableList<PoetDependenciesInfo> = mutableListOf()
    // Android Specific:
    var activityCount: Int = 0
    var hasLaunchActivity: Boolean = false
    var androidBuildConfig = AndroidBuildConfig()
    var resources: PoetResourceInfo = PoetResourceInfo()

    val moduleName get() = this.path.split(':').last()

    fun saveAsJsonTo(to: File) {
        FileUtils.mkdirs(to.parentFile)
        Files.asCharSink(to, Charsets.UTF_8).write(toJson())
    }

    fun toJson() : String {
        val gson = GsonBuilder()
            .registerTypeAdapter(ModuleInfo::class.java, JavaModuleInfoJsonAdapter())
            .setPrettyPrinting()
            .create()
        return gson.toJson(this)
    }

    companion object {
        fun readAsJsonFrom(from: File): ModuleInfo {
            return fromJson(from.readText(Charsets.UTF_8))
        }

        fun fromJson(from: String): ModuleInfo {
            val gson = GsonBuilder()
                .registerTypeAdapter(ModuleInfo::class.java, JavaModuleInfoJsonAdapter())
                .create()

            val recordType = object : TypeToken<ModuleInfo>() {}.type
            return gson.fromJson(from, recordType)
        }
    }
}

enum class ModuleType {
    PURE, ANDROID
}

enum class DependencyType(val jsonValue: String) {
    MODULE("moduleName"), EXTERNAL_LIBRARY("library");
}

data class PoetDependenciesInfo(
    val type: DependencyType,
    val scope: String,
    val dependency: String
)

data class PoetResourceInfo(
    var stringCount: Int = 0,
    var imageCount: Int = 0,
    var layoutCount: Int = 0
)

data class AndroidBuildConfig(
    var minSdkVersion: Int = 0,
    var targetSdkVersion: Int = 0,
    var compileSdkVersion: Int = 0
)
