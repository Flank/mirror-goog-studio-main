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

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class ASPoetInfoJsonAdapter : TypeAdapter<ASPoetInfo>() {
    override fun write(output: JsonWriter, data: ASPoetInfo) {
        output.beginObject()
        output.name("inputVersion").value(data.poetVersion)

        writeProjectConfig(output, data)

        output.endObject()
    }

    private fun writeProjectConfig(output: JsonWriter, data: ASPoetInfo) {
        output.name("projectConfig").beginObject()
        output.name("projectName").value("generated")
        output.name("root").value("./")

        writeBuildSystemConfig(output, data)
        writeModuleConfigs(output, data)

        output.endObject()
    }

    private fun writeBuildSystemConfig(output: JsonWriter, data: ASPoetInfo) {
        output.name("buildSystemConfig").beginObject()
            .name("buildSystemVersion").value(data.gradleVersion)
            .name("agpVersion").value(data.agpVersion)
            .endObject()
    }

    private fun writeModuleConfigs(output: JsonWriter, data: ASPoetInfo) {
        output.name("moduleConfigs").beginArray()
        data.modules.forEach { JavaModuleInfoJsonAdapter().write(output, it) }
        output.endArray()
    }

    override fun read(input: JsonReader): ASPoetInfo {
        throw UnsupportedOperationException("Full Config reading not supported.")
    }
}

class JavaModuleInfoJsonAdapter : TypeAdapter<ModuleInfo>() {
    override fun write(output: JsonWriter, data: ModuleInfo) {
        output.beginObject()

        output.name("moduleName").value(data.moduleName)
        output.name("modulePath").value(data.path)
        output.name("moduleType").value(data.type.toString().toLowerCase())

        writeJavaCounts(output, data)
        writeKotlinCounts(output, data)
        writeAndroidFields(output, data)
        writeDependencies(output, data)

        output.endObject()
    }

    private fun writeJavaCounts(output: JsonWriter, data: ModuleInfo) {
        output.name("javaPackageCount").value(data.javaPackageCount)
        output.name("javaClassCount").value(data.javaClassCount)
        output.name("javaMethodsPerClass").value(data.javaMethodsPerClass)
    }

    private fun writeKotlinCounts(output: JsonWriter, data: ModuleInfo) {
        if (!data.useKotlin) return
        output.name("useKotlin").value(data.useKotlin)
        output.name("kotlinPackageCount").value(data.kotlinPackageCount)
        output.name("kotlinClassCount").value(data.kotlinClassCount)
        output.name("kotlinMethodsPerClass").value(data.kotlinMethodsPerClass)
    }

    private fun writeDependencies(output: JsonWriter, data: ModuleInfo) {
        output.name("dependencies").beginArray()
        data.dependencies.forEach {
            output.beginObject()
                .name(it.type.jsonValue).value(it.dependency)
                .name("method").value(it.scope)
                .endObject()
        }
        output.endArray()
    }

    private fun writeAndroidFields(output: JsonWriter, data: com.android.build.gradle.internal.tasks.structureplugin.ModuleInfo) {
        if (data.type != com.android.build.gradle.internal.tasks.structureplugin.ModuleType.ANDROID) return
        output.name("androidBuildConfig").beginObject()
            .name("minSdkVersion").value(data.androidBuildConfig.minSdkVersion)
            .name("targetSdkVersion").value(data.androidBuildConfig.targetSdkVersion)
            .name("compileSdkVersion").value(data.androidBuildConfig.compileSdkVersion)
            .endObject()
        output.name("activityCount").value(data.activityCount)
        output.name("hasLaunchActivity").value(data.hasLaunchActivity)
        output.name("resourcesConfig").beginObject()
            .name("stringCount").value(data.resources.stringCount)
            .name("imageCount").value(data.resources.imageCount)
            .name("layoutCount").value(data.resources.layoutCount)
            .endObject()

    }

    override fun read(input: JsonReader): ModuleInfo {
        val data = ModuleInfo()

        input.readObjectProperties {
            when (it) {
                "modulePath" -> data.path = nextString()
                "moduleType" -> data.type = ModuleType.valueOf(nextString().toUpperCase())
                "javaPackageCount" -> data.javaPackageCount = nextInt()
                "javaClassCount" -> data.javaClassCount = nextInt()
                "javaMethodsPerClass" -> data.javaMethodsPerClass = nextInt()
                "useKotlin" -> data.useKotlin = nextBoolean()
                "kotlinPackageCount" -> data.kotlinPackageCount = nextInt()
                "kotlinClassCount" -> data.kotlinClassCount = nextInt()
                "kotlinMethodsPerClass" -> data.kotlinMethodsPerClass = nextInt()
                "dependencies" -> data.dependencies = readDependencies(this)
                "activityCount" -> data.activityCount = nextInt()
                "hasLaunchActivity" -> data.hasLaunchActivity = nextBoolean()
                "androidBuildConfig" -> data.androidBuildConfig = readAndroidBuildConfig(this)
                "resources" -> data.resources = readResources(this)
                else -> skipValue()
            }
        }

        return data
    }

    private fun readDependencies(input: JsonReader): MutableList<PoetDependenciesInfo> {
        val dependencies = mutableListOf<PoetDependenciesInfo>()

        input.readArray {
            var type: DependencyType? = null
            var dependency: String? = null
            var scope: String? = null

            readObjectProperties {
                when (it) {
                    "moduleName" -> {
                        type = DependencyType.MODULE
                        dependency = nextString()
                    }
                    "library" -> {
                        type = DependencyType.EXTERNAL_LIBRARY
                        dependency = nextString()
                    }
                    "method" -> {
                        scope = nextString()
                    }
                    else -> skipValue()
                }
            }
            dependencies.add(
                PoetDependenciesInfo(type!!, scope!!, dependency!!))
        }

        return dependencies
    }

    private fun readResources(input: JsonReader): PoetResourceInfo {
        var stringCount = 0
        var imageCount = 0
        var layoutCount = 0

        input.readObjectProperties {
            when (it) {
                "stringCount" -> stringCount = nextInt()
                "imageCount" -> imageCount = nextInt()
                "layoutCount" -> layoutCount = nextInt()
                else -> skipValue()
            }
        }

        return PoetResourceInfo(stringCount, imageCount, layoutCount)
    }

    private fun readAndroidBuildConfig(input: JsonReader) : AndroidBuildConfig {
        val buildConfig = AndroidBuildConfig()
        input.readObjectProperties {
            when(it) {
                "minSdkVersion" -> buildConfig.minSdkVersion = nextInt()
                "targetSdkVersion" -> buildConfig.targetSdkVersion = nextInt()
                "compileSdkVersion" -> buildConfig.compileSdkVersion = nextInt()
                else -> skipValue()
            }
        }
        return buildConfig
    }
}

private inline fun JsonReader.readObjectProperties(consumer: JsonReader.(String) -> Unit) {
    beginObject()
    while (hasNext()) {
        consumer(this, nextName())
    }
    endObject()
}

private inline fun JsonReader.readArray(objectReader: JsonReader.() -> Unit) {
    beginArray()
    while (hasNext()) {
        objectReader()
    }
    endArray()
}
