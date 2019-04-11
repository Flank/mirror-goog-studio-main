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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FN_INTERMEDIATE_FULL_JAR
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.packaging.JarMerger
import com.android.utils.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.function.Predicate

/** Task to merge the res/classes intermediate jars from a library into a single one  */
@CacheableTask
abstract class ZipMergingTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryInputFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var javaResInputFiles: BuildableArtifact
        internal set

    @get:OutputFile
    lateinit var outputFile: File
        internal set

    public override fun doTaskAction() {
        FileUtils.cleanOutputDir(outputFile.parentFile)
        val usedNamesPredicate = object:Predicate<String> {
            val usedNames = mutableSetOf<String>()

            override fun test(t: String): Boolean {
                return usedNames.add(t)
            }
        }

        JarMerger(outputFile.toPath(), usedNamesPredicate).use {
            val lib = libraryInputFile.get().asFile
            if (lib.exists()) {
                it.addJar(lib.toPath())
            }
            javaResInputFiles.files.forEach { jar -> it.addJar(jar.toPath()) }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ZipMergingTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("createFullJar")
        override val type: Class<ZipMergingTask>
            get() = ZipMergingTask::class.java

        private lateinit var mainFullJar: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            mainFullJar = variantScope.artifacts.appendArtifact(InternalArtifactType.FULL_JAR,
                taskName, FN_INTERMEDIATE_FULL_JAR)
        }

        override fun configure(task: ZipMergingTask) {
            super.configure(task)

            val buildArtifacts = variantScope.artifacts
            buildArtifacts.setTaskInputToFinalProduct(InternalArtifactType.RUNTIME_LIBRARY_CLASSES, task.libraryInputFile)
            task.javaResInputFiles = buildArtifacts.getOptionalFinalArtifactFiles(InternalArtifactType.LIBRARY_JAVA_RES)
            task.outputFile = mainFullJar
        }
    }
}
