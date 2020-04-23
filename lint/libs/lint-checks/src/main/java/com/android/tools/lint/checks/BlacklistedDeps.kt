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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.GradleDetector.Companion.getCompileDependencies
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.LmLibrary
import java.util.ArrayDeque

data class Coordinate(val group: String, val artifact: String) : Comparable<Coordinate> {
    override fun compareTo(other: Coordinate): Int {
        val delta = group.compareTo(other.group)
        if (delta != 0) {
            return delta
        }
        return artifact.compareTo(other.artifact)
    }
}

/**
 * This class finds blacklisted dependencies in a project by looking
 * transitively
 */
class BlacklistedDeps(val project: Project) {

    private var map: MutableMap<Coordinate, List<LmLibrary>>? = null

    init {
        val dependencies = getCompileDependencies(project)
        if (dependencies != null) {
            visitLibraries(ArrayDeque(), dependencies.direct)
        }
    }

    /**
     * Returns the path from this dependency to one of the blacklisted dependencies,
     * or null if this dependency is not blacklisted. If [remove] is true, the
     * dependency is removed from the map after this.
     */
    fun checkDependency(groupId: String, artifactId: String, remove: Boolean): List<LmLibrary>? {
        val map = this.map ?: return null
        val coordinate = Coordinate(groupId, artifactId)
        val path = map[coordinate] ?: return null
        if (remove) {
            map.remove(coordinate)
        }
        return path
    }

    /**
     * Returns all the dependencies found in this project that lead to a
     * blacklisted dependency. Each list is a list from the root dependency
     * to the blacklisted dependency.
     */
    fun getBlacklistedDependencies(): List<List<LmLibrary>> {
        val map = this.map ?: return emptyList()
        return map.values.toMutableList().sortedBy { it[0].resolvedCoordinates.groupId }
    }

    private fun visitLibraries(
        stack: ArrayDeque<LmLibrary>,
        libraries: List<LmLibrary>
    ) {
        for (library in libraries) {
            visitLibrary(stack, library)
        }
    }

    private fun visitLibrary(stack: ArrayDeque<LmLibrary>, library: LmLibrary) {
        stack.addLast(library)
        checkLibrary(stack, library)
        visitLibraries(stack, library.dependencies)
        stack.removeLast()
    }

    private fun checkLibrary(stack: ArrayDeque<LmLibrary>, library: LmLibrary) {
        // Provided dependencies are fine
        if (library.provided /* && stack.size == 1*/) {
            return
        }

        val coordinates = library.resolvedCoordinates
        if (isBlacklistedDependency(coordinates.groupId, coordinates.artifactId)) {
            if (map == null) {
                map = HashMap()
            }
            val root = stack.first.resolvedCoordinates
            map?.put(Coordinate(root.groupId, root.artifactId), ArrayList(stack))
        }
    }

    private fun isBlacklistedDependency(
        groupId: String,
        artifactId: String
    ): Boolean {
        when (groupId) {
            // org.apache.http.*
            "org.apache.httpcomponents" -> return "httpclient" == artifactId

            // org.xmlpull.v1.*
            "xpp3" -> return "xpp3" == artifactId

            // org.apache.commons.logging
            "commons-logging" -> return "commons-logging" == artifactId

            // org.xml.sax.*, org.w3c.dom.*
            "xerces" -> return "xmlParserAPIs" == artifactId

            // org.json.*
            "org.json" -> return "json" == artifactId

            // javax.microedition.khronos.*
            "org.khronos" -> return "opengl-api" == artifactId

            // all of the above
            "com.google.android" -> return "android" == artifactId
        }

        return false
    }
}
