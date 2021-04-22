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

package com.android.build.gradle.internal.tasks.bundle

import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.LibraryDependencies
import com.android.tools.build.libraries.metadata.ModuleDependencies
import com.android.tools.build.libraries.metadata.Repository
import com.google.protobuf.Int32Value

internal fun appDependencies(buildAction: ADBuilder.() -> Unit): AppDependencies =
    AppDependencies.newBuilder().also { ADBuilder(it).apply(buildAction) }.build()

/* Convenience DSL for concise [AppDependencies] initialization */
internal class ADBuilder(private val wrappedValue: AppDependencies.Builder) {

    fun addLibrary(groupId: String, artifactId: String, version: String)
            : Library.Builder =
        wrappedValue.addLibraryBuilder().apply {
            mavenLibraryBuilder.setGroupId(groupId).setArtifactId(artifactId).setVersion(version)
        }

    fun Library.Builder.setRepoIndex(value: Int): Library.Builder =
        setRepoIndex(Int32Value.of(value))

    fun addModuleDeps(moduleName: String, vararg dependencyIndices: Int)
            : ModuleDependencies.Builder =
        wrappedValue.addModuleDependenciesBuilder().apply {
            setModuleName(moduleName)
            dependencyIndices.forEach { addDependencyIndex(it) }
        }

    fun addLibraryDeps(libraryIndex: Int, vararg libraryDepIndices: Int)
            : LibraryDependencies.Builder =
        wrappedValue.addLibraryDependenciesBuilder().apply {
            setLibraryIndex(libraryIndex)
            libraryDepIndices.forEach { addLibraryDepIndex(it) }
        }

    fun addMavenRepository(url: String): Repository.Builder =
        wrappedValue.addRepositoriesBuilder().apply {
            mavenRepoBuilder.url = url
        }

    fun addIvyRepository(url: String): Repository.Builder =
        wrappedValue.addRepositoriesBuilder().apply {
            ivyRepoBuilder.url = url
        }
}
