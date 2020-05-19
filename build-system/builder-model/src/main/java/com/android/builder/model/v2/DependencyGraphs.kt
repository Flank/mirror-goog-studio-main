/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.builder.model.v2

/**
 * A set of dependency Graphs.
 *
 * It contains both the compile and the package graphs, through the latter could be empty in
 * non full sync.
 *
 * Each graph is fairly lightweight, with each artifact node being mostly an address, children,
 * and modifiers that are specific to this particular usage of the artifact rather than
 * artifact properties.*
 */
interface DependencyGraphs {
    /**
     * Returns the compile dependency graph.
     */
    val compileDependencies: List<GraphItem>

    /**
     * Returns the package dependency graph.
     *
     * Only valid in full dependency mode.
     */
    val packageDependencies: List<GraphItem>

    /**
     * Returns the list of provided libraries.
     *
     * The values in the list match the values returned by [GraphItem.getArtifactAddress]
     * and [Library.getArtifactAddress].
     *
     * Only valid in full dependency mode.
     */
    val providedLibraries: List<String>

    /**
     * Returns the list of skipped libraries.
     *
     * The values in the list match the values returned by [GraphItem.getArtifactAddress]
     * and [Library.getArtifactAddress].
     *
     * Only valid in full dependency mode.
     */
    val skippedLibraries: List<String>
}