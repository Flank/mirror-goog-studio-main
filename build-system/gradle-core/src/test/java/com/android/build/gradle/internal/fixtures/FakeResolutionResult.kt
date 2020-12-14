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

package com.android.build.gradle.internal.fixtures

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.AttributeContainer

class FakeResolutionResult(private val root: ResolvedComponentResult): ResolutionResult {
    override fun getRoot(): ResolvedComponentResult = root

    override fun getAllComponents(): MutableSet<ResolvedComponentResult> {
        val allComponents = mutableSetOf<ResolvedComponentResult>()
        collectComponentsRecursively(root, allComponents)
        return allComponents
    }

    override fun getRequestedAttributes(): AttributeContainer {
        TODO("Not yet implemented")
    }

    override fun allDependencies(p0: Action<in DependencyResult>) {
        TODO("Not yet implemented")
    }

    override fun allDependencies(p0: Closure<Any>) {
        TODO("Not yet implemented")
    }

    override fun getAllDependencies(): MutableSet<out DependencyResult> {
        TODO("Not yet implemented")
    }

    override fun allComponents(p0: Action<in ResolvedComponentResult>) {
        TODO("Not yet implemented")
    }

    override fun allComponents(p0: Closure<Any>) {
        TODO("Not yet implemented")
    }

    private fun collectComponentsRecursively(component: ResolvedComponentResult, collectedComponent: MutableSet<ResolvedComponentResult>) {
        if (!collectedComponent.add(component)) {
            return
        }
        for (childDependency in component.dependencies) {
            collectComponentsRecursively((childDependency as ResolvedDependencyResult).selected, collectedComponent)
        }
    }
}