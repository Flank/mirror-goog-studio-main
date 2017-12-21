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
import com.android.tools.lint.KotlinLintAnalyzerFacade
import com.android.tools.lint.LintCoreApplicationEnvironment
import com.android.tools.lint.LintCoreProjectEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.lint.gradle.api.ExtractAnnotationRequest
import com.intellij.openapi.util.Disposer
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
        val roots = request.roots

        val appEnv = LintCoreApplicationEnvironment.get()
        val parentDisposable = Disposer.newDisposable()

        try {
            val projectEnvironment = LintCoreProjectEnvironment.create(parentDisposable, appEnv)
            projectEnvironment.registerPaths(roots)
            val parsedUnits = Extractor.createUnitsForFiles(projectEnvironment.project,
                    sourceFiles)

            val ktFiles = ArrayList<File>()
            for (file in sourceFiles) {
                if (file.path.endsWith(DOT_KT)) {
                    ktFiles.add(file)
                }
            }
            KotlinLintAnalyzerFacade.analyze(ktFiles, roots, projectEnvironment.project)

            val displayInfo = logger.isEnabled(LogLevel.INFO)
            val extractor = Extractor(null, classDir.files, displayInfo, false, false)

            extractor.extractFromProjectSource(parsedUnits)
            extractor.export(output, null)
            extractor.writeTypedefFile(typedefFile)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        } finally {
            Disposer.dispose(parentDisposable)
            LintCoreApplicationEnvironment.clearAccessorCache()
        }
    }
}