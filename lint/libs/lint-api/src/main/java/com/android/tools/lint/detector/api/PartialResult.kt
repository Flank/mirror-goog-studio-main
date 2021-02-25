/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.detector.api

/**
 * Captures partial results computed for a specific issue for later
 * aggregation when merging results from many different projects. For
 * example, for the unused resource detector, this will contain the
 * resource usage models (as a string) for each project, such that when
 * computing unused resources for a specific app module, it can look up
 * these partial results, merge the resource usage models and finally
 * produce a warning for each resource found to be declared anywhere but
 * not used anywhere.
 *
 * (This is normally just a [LintMap] for each project, but we're using
 * a wrapper class here to make it straightforward to add more state
 * in the future without breaking detectors overriding the method to
 * process the partial results.
 */
class PartialResult(val issue: Issue, private val data: MutableMap<Project, LintMap>) :
    Iterable<Map.Entry<@JvmSuppressWildcards Project, @JvmSuppressWildcards LintMap>> {
    // @JvmSuppressWildcards above: Make it easy to iterate from Java

    /** Returns the [LintMap] for the given project. */
    fun mapFor(project: Project): LintMap {
        return data[project] ?: LintMap().also { data[project] = it }
    }

    /**
     * Returns the "current" map. This is valid during analysis
     * when you're reporting partial results. In this case there's
     * always just a single project being looked at and this
     * is its map. This is more convenient than having to call
     * [mapFor(context.getProject)].
     */
    fun map(): LintMap = data.values.firstOrNull()
        ?: error("This method should only be called during module analysis")

    /**
     * Returns all the maps with partial results from the various
     * analyzed projects.
     */
    fun maps(): Collection<LintMap> = data.values

    /** Returns all the projects that have partial results. */
    fun projects(): Collection<Project> = data.keys

    /** Are there any partial results? */
    fun isEmpty(): Boolean {
        for (map in maps()) {
            if (map.isNotEmpty()) {
                return false
            }
        }
        return true
    }

    /** Iterates over the pairs (Project, LintMap) */
    override fun iterator(): Iterator<Map.Entry<Project, LintMap>> {
        return data.entries.iterator()
    }
}
