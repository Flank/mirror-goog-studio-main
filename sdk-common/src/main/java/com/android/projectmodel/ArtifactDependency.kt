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
package com.android.projectmodel

import com.android.ide.common.repository.GradleCoordinate

/**
 * Represents a node in the dependency graph.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class ArtifactDependency(
        /**
         * The dependency itself.
         */
        val library: Library,
        /**
         * List of other dependencies that are needed to support this dependency. Note that this
         * property forms a DAG between other dependencies.
         */
        val dependencies: List<ArtifactDependency> = emptyList(),
        /**
         * Requested Maven coordinate for this dependency, if one existed in the build file.
         */
        val requestedMavenCoordinate: GradleCoordinate? = null,
        /**
         * Resolved Maven coordinate for this dependency, if one existed and is known to the build system.
         */
        val resolvedMavenCoordinate: GradleCoordinate? = null
)
