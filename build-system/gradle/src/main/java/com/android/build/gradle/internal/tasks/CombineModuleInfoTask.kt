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

package com.android.build.gradle.internal.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

open class CombineModuleInfoTask : DefaultTask() {

    @get:InputFiles
    lateinit var subModules: FileCollection

    // optional module info if root project is also a module with plugins applied
    var localModuleInfo: Provider<RegularFile>? = null

    // output
    lateinit var outputProvider: Provider<RegularFile>

    @TaskAction
    fun action() {
        // load all the modules
        var infoList = subModules.map { loadModule(it) }

        // gather all the module paths
        val paths = infoList.map { it.path }

        // create a map from path to anonymized paths

        // make segments from path:
        val segmentedPaths: Map<String, List<String>> = paths.map {
            it to it.split(':').filter { it.isNotEmpty() }
        }.toMap()

        // make a tree from the segments.
        val root = Segment("")
        segmentedPaths.values.forEach {
            root.process(it)
        }

        // anonymize the segments.
        root.anonymize()

        val fullPathMap = mutableMapOf<String, String>()

        // loop on all paths and get the anonymized versions
        for ((path, segments) in segmentedPaths) {
            fullPathMap[path] = ":" + root.computeAnonymizedPath(segments)
        }

        // if we have a local module add it to the list.
        localModuleInfo?.get()?.asFile?.let {
            val localModule = loadModule(it)
            val newList = mutableListOf(localModule)
            newList.addAll(infoList)
            infoList = newList
            // no need to anonymize this one
            fullPathMap[":"] = ":"
        }

        // anonymize the modules and save them
        infoList = infoList.map { it.anonymize(fullPathMap) }

        saveModules(infoList, outputProvider.get().asFile)
    }
}

class Segment(val name: String) {
    private val children: MutableList<Segment> = mutableListOf()
    private var anonymousName: String = ""

    fun process(segments: List<String>, index: Int = 0) {
        if (index >= segments.size) {
            return
        }

        val name = segments[index]
        var match = findChild(name)

        if (match == null) {
            val newChild = Segment(name)
            children.add(newChild)
            match = newChild
        }

        match.process(segments, index + 1)
    }

    fun anonymize() {
        var index = 1
        children.forEach {
            it.anonymousName = "module$index"
            it.anonymize()
            index++
        }
    }

    fun computeAnonymizedPath(segments: List<String>, index: Int = 0): String {
        if (index >= segments.size) {
            return ""
        }

        val name = segments[index]
        val match = findChild(name) ?: return ""

        val childrenPath = match.computeAnonymizedPath(segments, index + 1)

        if (childrenPath.isEmpty()) {
            return match.anonymousName
        }

        return match.anonymousName + ":" + childrenPath

    }

    private fun findChild(name: String): Segment? {
        return children.firstOrNull { it.name == name }
    }
}

