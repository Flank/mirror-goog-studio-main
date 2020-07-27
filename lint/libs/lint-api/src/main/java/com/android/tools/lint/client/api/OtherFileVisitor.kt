/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_ASSETS
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.utils.SdkUtils
import com.google.common.collect.Lists
import java.io.File
import java.util.ArrayList
import java.util.EnumMap
import java.util.EnumSet

/**
 * Visitor for "other" files: files that aren't java sources,
 * XML sources, etc -- or which should have custom handling in some
 * other way.
 */
internal class OtherFileVisitor(private val detectors: List<Detector>) {

    private val files = EnumMap<Scope, List<File>>(Scope::class.java)

    /** Analyze other files in the given project  */
    fun scan(
        driver: LintDriver,
        project: Project,
        main: Project?
    ) {
        // Collect all project files
        val projectFolder = project.dir

        var scopes = EnumSet.noneOf(Scope::class.java)
        for (detector in detectors) {
            val fileScanner = detector as OtherFileScanner
            val applicable = fileScanner.getApplicableFiles()
            if (applicable.contains(Scope.OTHER)) {
                scopes = Scope.ALL
                break
            }
            scopes.addAll(applicable)
        }

        val subset = project.subset

        if (scopes.contains(Scope.RESOURCE_FILE)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (SdkUtils.endsWith(
                        file.path,
                        DOT_XML
                    ) && file.name != ANDROID_MANIFEST_XML
                    ) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.RESOURCE_FILE] = files
                }
            } else {
                val files = Lists.newArrayListWithExpectedSize<File>(100)
                for (res in project.resourceFolders) {
                    collectFiles(files, res)
                }
                val assets = File(projectFolder, FD_ASSETS)
                if (assets.exists()) {
                    collectFiles(files, assets)
                }
                if (!files.isEmpty()) {
                    this.files[Scope.RESOURCE_FILE] = files
                }
            }
        }

        if (scopes.contains(Scope.JAVA_FILE)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (file.path.endsWith(DOT_JAVA) || file.path.endsWith(DOT_KT)) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.JAVA_FILE] = files
                }
            } else {
                val files = Lists.newArrayListWithExpectedSize<File>(100)
                for (srcFolder in project.javaSourceFolders) {
                    collectFiles(files, srcFolder)
                }
                if (!files.isEmpty()) {
                    this.files[Scope.JAVA_FILE] = files
                }
            }
        }

        if (scopes.contains(Scope.CLASS_FILE)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (file.path.endsWith(DOT_CLASS)) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.CLASS_FILE] = files
                }
            } else {
                val files = Lists.newArrayListWithExpectedSize<File>(100)
                for (classFolder in project.javaClassFolders) {
                    collectFiles(files, classFolder)
                }
                if (!files.isEmpty()) {
                    this.files[Scope.CLASS_FILE] = files
                }
            }
        }

        if (scopes.contains(Scope.MANIFEST)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (file.name == ANDROID_MANIFEST_XML) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.MANIFEST] = files
                }
            } else {
                val manifestFiles = project.manifestFiles
                files[Scope.MANIFEST] = manifestFiles
            }
        }

        if (scopes.contains(Scope.GRADLE_FILE)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (file.name.endsWith(DOT_GRADLE) || file.name.endsWith(DOT_KTS)) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.GRADLE_FILE] = files
                }
            } else {
                val manifestFiles = project.gradleBuildScripts
                files[Scope.GRADLE_FILE] = manifestFiles
            }
        }

        if (scopes.contains(Scope.PROGUARD_FILE)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (file.name.startsWith("proguard")) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.PROPERTY_FILE] = files
                }
            } else {
                val manifestFiles = project.proguardFiles
                files[Scope.PROGUARD_FILE] = manifestFiles
            }
        }

        if (scopes.contains(Scope.PROPERTY_FILE)) {
            if (subset != null && !subset.isEmpty()) {
                val files = ArrayList<File>(subset.size)
                for (file in subset) {
                    if (file.name.endsWith(".properties")) {
                        files.add(file)
                    }
                }
                if (!files.isEmpty()) {
                    this.files[Scope.PROPERTY_FILE] = files
                }
            } else {
                val propertyFiles = project.propertyFiles
                files[Scope.PROPERTY_FILE] = propertyFiles
            }
        }

        for ((scope, files) in files) {
            val applicable = ArrayList<Detector>(detectors.size)
            for (detector in detectors) {
                val fileScanner = detector as OtherFileScanner
                val appliesTo = fileScanner.getApplicableFiles()
                if (appliesTo.contains(Scope.OTHER) || appliesTo.contains(scope)) {
                    applicable.add(detector)
                }
            }
            if (!applicable.isEmpty()) {
                for (file in files) {
                    val context = Context(driver, project, main, file)
                    for (detector in applicable) {
                        detector.beforeCheckFile(context)
                        detector.run(context)
                        detector.afterCheckFile(context)
                        driver.fileCount++
                    }
                    if (driver.isCanceled) {
                        return
                    }
                }
            }
        }
    }

    private fun collectFiles(files: MutableList<File>, file: File) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    collectFiles(files, child)
                }
            }
        } else {
            files.add(file)
        }
    }
}
