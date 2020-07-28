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

package com.android.tools.lint.gradle

import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.lint.gradle.api.ExtractAnnotationRequest
import org.gradle.api.logging.LogLevel
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.ArrayList

@Suppress("unused") // Accessed via reflection
class LintExtractAnnotations {
    fun extractAnnotations(request: ExtractAnnotationRequest) {
        val typedefFile = request.typedefFile
        val logger = request.logger
        val classDir = request.classDir
        val output = request.output
        val sourceFiles = request.sourceFiles
        val sourceRoots = request.sourceRoots
        val classpathRoots = request.classpathRoots

        val config = UastEnvironment.Configuration.create()
        config.addSourceRoots(sourceRoots)
        config.addClasspathRoots(classpathRoots)

        val env = UastEnvironment.create(config)
        try {
            val parsedUnits = Extractor.createUnitsForFiles(env.ideaProject, sourceFiles)

            val ktFiles = ArrayList<File>()
            for (file in sourceFiles) {
                if (file.path.endsWith(DOT_KT)) {
                    ktFiles.add(file)
                }
            }

            env.analyzeFiles(ktFiles)

            val displayInfo = logger.isEnabled(LogLevel.INFO)
            val extractor = Extractor(null, classDir.files, displayInfo, false, false)

            extractor.extractFromProjectSource(parsedUnits)
            extractor.export(output, null)
            extractor.writeTypedefFile(typedefFile)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        } finally {
            env.dispose()
        }
    }
}
