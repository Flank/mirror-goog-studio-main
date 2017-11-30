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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.MainDexListTransform.ProguardInput.INPUT_JAR
import com.android.build.gradle.internal.transforms.MainDexListTransform.ProguardInput.LIBRARY_JAR
import com.android.builder.multidex.D8MainDexList
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

/**
 * Calculate the main dex list using D8.
 */
class D8MainDexListTransform(
        private val manifestProguardRules: Path,
        private val userProguardRules: Path? = null,
        private val userClasses: Path? = null,
        private val outputMainDexList: Path,
        private val bootClasspath: Supplier<List<Path>>) : Transform() {

    private val logger = LoggerWrapper.getLogger(D8MainDexListTransform::class.java)

    constructor(variantScope: VariantScope) :
            this(
                    variantScope.manifestKeepListProguardFile.toPath(),
                    variantScope.variantConfiguration.multiDexKeepProguard?.toPath(),
                    variantScope.variantConfiguration.multiDexKeepFile?.toPath(),
                    variantScope.mainDexListFile.toPath(),
                    Supplier {
                        variantScope
                                .globalScope
                                .androidBuilder
                                .getBootClasspath(true)
                                .map { it.toPath() }})

    override fun getName(): String = "multidexlist"

    override fun getInputTypes(): ImmutableSet<out ContentType> =
            Sets.immutableEnumSet(QualifiedContent.DefaultContentType.CLASSES)

    override fun getScopes(): ImmutableSet<in Scope> = ImmutableSet.of()

    override fun getReferencedScopes(): ImmutableSet<in Scope> =
            Sets.immutableEnumSet(
                    Scope.PROJECT,
                    Scope.SUB_PROJECTS,
                    Scope.EXTERNAL_LIBRARIES,
                    Scope.PROVIDED_ONLY,
                    Scope.TESTED_CODE)

    override fun isIncremental(): Boolean = false

    override fun isCacheable(): Boolean = true

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> =
            listOfNotNull(manifestProguardRules, userProguardRules, userClasses)
                    .map { it.toFile() }
                    .map { SecondaryFile.nonIncremental(it) }
                    .toCollection(ArrayList())

    override fun getSecondaryFileOutputs(): ImmutableList<File> =
            ImmutableList.of(outputMainDexList.toFile())

    override fun getParameterInputs(): ImmutableMap<String, Any> =
            ImmutableMap.of("implementation", D8MainDexListTransform::class.java.name)

    override fun transform(invocation: TransformInvocation) {
        logger.verbose("Generating the main dex list using D8.")
        try {
            val inputs = MainDexListTransform.getByInputType(invocation)
            val programFiles = inputs[INPUT_JAR]!!.map { it.toPath() }
            val libraryFiles = inputs[LIBRARY_JAR]!!.map { it.toPath() } + bootClasspath.get()
            logger.verbose("Program files: %s", programFiles.joinToString())
            logger.verbose("Library files: %s", libraryFiles.joinToString())
            logger.verbose(
                    "Proguard rule files: %s",
                    listOfNotNull(manifestProguardRules, userProguardRules).joinToString())

            val proguardRules = listOfNotNull(manifestProguardRules, userProguardRules)
            val mainDexClasses = mutableSetOf<String>()

            val keepRules = MainDexListTransform.getPlatformRules().map { it -> "-keep " + it }
            mainDexClasses.addAll(
                    D8MainDexList.generate(
                            keepRules,
                            proguardRules,
                            programFiles,
                            libraryFiles))

            if (userClasses != null) {
                mainDexClasses.addAll(Files.readAllLines(userClasses))
            }

            Files.deleteIfExists(outputMainDexList)
            Files.write(outputMainDexList, mainDexClasses)
        } catch (e: D8MainDexList.MainDexListException) {
            throw TransformException("Error while generating the main dex list.", e)
        }

    }
}