/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult

class FakeResolvedComponentResult(
    private val dependents: MutableSet<ResolvedDependencyResult> = mutableSetOf(),
    private val id: ComponentIdentifier? = null,
    private val dependencies: MutableSet<DependencyResult> = mutableSetOf(),
    private val selectionReason: ComponentSelectionReason? = null,
    private val variant: ResolvedVariantResult? = null,
    private val moduleVersion: ModuleVersionIdentifier? = null
) : ResolvedComponentResult {

    override fun getDependents() = dependents
    override fun getId() = id ?: error("value not set")
    override fun getDependencies() = dependencies
    override fun getSelectionReason() = selectionReason ?: error("value not set")
    override fun getVariant() = variant ?: error("value not set")
    override fun getModuleVersion() = moduleVersion ?: error("value not set")
    override fun getVariants(): List<ResolvedVariantResult> = variant?.let { listOf(it) } ?: error("value not set")
    override fun getDependenciesForVariant(p0: ResolvedVariantResult): MutableList<out DependencyResult> =
        dependencies.toMutableList()
}
