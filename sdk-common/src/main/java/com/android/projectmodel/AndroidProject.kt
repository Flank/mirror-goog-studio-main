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

/**
 * Represents a single Android project. This is the collection of sources and metadata needed to construct a single android artifact (an
 * application, library, etc.). An android project contains one or more [Variant]s, which are alternative ways of constructing the project.
 */
data class AndroidProject(
        /**
         * Unique identifier of the project, provided by the build system. This is used for cross-referencing the project when it appears
         * as a dependency for other projects. Should remain invariant across syncs, but does not need to remain invariant across
         * machines. This will be displayed to the user as the project's identifier, so it should be something the user would be familiar
         * with (such as the project's build target or root folder). For example, in Gradle this will be the project path, e.g.
         * :util:widgets.
         */
        val name: String,
        /**
         * Indicates the type of project (the type of the project's main artifact).
         */
        val type: ProjectType,
        /**
         * List of variants for the project.
         */
        val variants: List<Variant> = emptyList()
)
