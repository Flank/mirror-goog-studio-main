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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.testutils.MavenRepoGenerator

class DependenciesBuilderImpl() : DependenciesBuilder {
    private val implementation = mutableListOf<MavenRepoGenerator.Library>()

    val allLibraries: List<MavenRepoGenerator.Library>
        get() = implementation

    override fun implementation(library: MavenRepoGenerator.Library) {
        implementation.add(library)
    }

    fun writeBuildFile(sb: StringBuilder) {
        sb.append("\ndependencies {\n")
        for (library in implementation) {
            sb.append("implementation '${library.mavenCoordinate}'")
        }
        sb.append("}\n")
    }
}
