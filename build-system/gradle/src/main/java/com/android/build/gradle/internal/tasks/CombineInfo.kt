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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

class CombineInfo: DefaultTask() {

    @get:InputFiles
    lateinit var subModules: FileCollection

    // optional module info if root project is also a module with plugins applied
    var localModuleInfo: Provider<RegularFile>? = null

    // output
    lateinit var outputProvider: Provider<RegularFile>

    @get:InputFile
    @get:Optional
    val localOutputFile: File?
        get() = localModuleInfo?.get()?.asFile


    @get:OutputFile
    val outputFile: File
        get() = outputProvider.get().asFile

    @TaskAction
    fun action() {

    }
}