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

import com.android.build.gradle.internal.dsl.BundleExtensionImpl
import com.android.tools.build.bundletool.BuildBundleCommand
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import java.io.File
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task that bundles the different module into a single bundle.
 *
 * The inputs should be files created by [ZipModuleTask].
 *
 * @Deprecated
 */
open class OldBundleTask: DefaultTask() {

    @get:OutputFile
    lateinit var outputFile: Provider<RegularFile>
        internal set

    internal lateinit var extension: BundleExtensionImpl

    @Suppress("unused")
    @get:Classpath
    val inputModules: Collection<FileCollection>
        get() = extension.modules.values

    @get:Optional
    @get:InputFile
    val configFile: File?
    get() {
      val file = project.file("BundleConfig.xml")
      return if (file.exists()) file else null
    }

    @TaskAction
    fun bundle() {
        val bundleFile = outputFile.get().asFile
        if (bundleFile.isFile) {
            FileUtils.delete(bundleFile)
        }

        val modules = extension
                .modules
                .values
                .stream()
                .map { it.singleFile.toPath() }
                .collect(ImmutableList.toImmutableList())

        val command = BuildBundleCommand.builder()
                .setOutputPath(bundleFile.toPath())
                .setModulesPaths(modules)
        configFile?.let {
            command.setBundleConfigPath(it.toPath())
        }

        command.build().execute()
    }
}
