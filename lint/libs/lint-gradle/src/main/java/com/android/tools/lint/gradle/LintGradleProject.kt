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
package com.android.tools.lint.gradle

import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.LintModelMavenName
import com.android.utils.XmlUtils
import java.io.File
import java.io.IOException

/**
 * An implementation of Lint's [Project] class wrapping a Gradle model (project or library)
 */
open class LintGradleProject(
    client: LintGradleClient,
    dir: File,
    referenceDir: File,
    manifest: File?
) : Project(client, dir, referenceDir) {
    init {
        gradleProject = true
        mergeManifests = true
        directLibraries = mutableListOf()
        manifest?.let { readManifest(it) }
    }

    @JvmField
    var kotlinSourceFolders: List<File>? = null

    fun setExternalLibrary(external: Boolean) {
        externalLibrary = external
    }

    fun setMavenCoordinates(mc: LintModelMavenName) {
        mavenCoordinates = mc
    }

    fun addDirectLibrary(project: Project) {
        directLibraries.add(project)
    }

    override fun initialize() {
        // Deliberately not calling super; that code is for ADT compatibility
    }

    private fun readManifest(manifest: File) {
        if (manifest.exists()) {
            try {
                val xml = manifest.readText()
                val document = XmlUtils.parseDocumentSilently(xml, true)
                document?.let { readManifest(it) }
            } catch (e: IOException) {
                client.log(e, "Could not read manifest %1\$s", manifest)
            }
        }
    }

    override fun isGradleProject(): Boolean = true
}
