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
import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.builder.packaging.JarMerger
import com.android.utils.FileUtils
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.util.function.Predicate

/** Task to merge the res/classes intermediate jars from a library into a single one  */
@CacheableTask
open class ZipMergingTask : AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var classesJar: BuildableArtifact
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var classesDir: BuildableArtifact
        private set


    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var javaResInputFiles: BuildableArtifact
        private set

    @get:OutputFile
    private lateinit var outputFile: File

    @VisibleForTesting
    internal fun init(
        classesJar: BuildableArtifact,
        classesDir: BuildableArtifact,
        javaResInputFiles: BuildableArtifact,
        outputFile: File) {
        this.classesJar = classesJar
        this.classesDir= classesDir
        this.javaResInputFiles = javaResInputFiles
        this.outputFile = outputFile
    }

    @TaskAction
    @Throws(IOException::class)
    fun merge() {
        FileUtils.cleanOutputDir(outputFile.parentFile)
        val usedNamesPredicate = object:Predicate<String> {
            val usedNames = mutableSetOf<String>()

            override fun test(t: String): Boolean {
                return usedNames.add(t)
            }
        }

        JarMerger(outputFile.toPath(), usedNamesPredicate).use {
            classesJar.files.forEach { jar -> it.addJar(jar.toPath()) }
            classesDir.files.forEach { dir -> it.addDirectory(dir.toPath()) }
            javaResInputFiles.files.forEach { jar -> it.addJar(jar.toPath()) }
        }
    }

    class ConfigAction(private val scope: VariantScope) : TaskConfigAction<ZipMergingTask> {

        override fun getName(): String = scope.getTaskName("createFullJar")
        override fun getType(): Class<ZipMergingTask> = ZipMergingTask::class.java

        override fun execute(task: ZipMergingTask) {
            val buildArtifacts = scope.artifacts
            val mainFullJar = buildArtifacts.appendArtifact(InternalArtifactType.FULL_JAR,
                    task, FN_INTERMEDIATE_FULL_JAR)
            task.init(
                    buildArtifacts.getOptionalFinalArtifactFiles(InternalArtifactType.LIBRARY_CLASSES_JAR),
                    buildArtifacts.getOptionalFinalArtifactFiles(InternalArtifactType.LIBRARY_CLASSES_DIR),
                    buildArtifacts.getOptionalFinalArtifactFiles(InternalArtifactType.LIBRARY_JAVA_RES),
                    mainFullJar)
            task.variantName = scope.fullVariantName
        }
    }
}
