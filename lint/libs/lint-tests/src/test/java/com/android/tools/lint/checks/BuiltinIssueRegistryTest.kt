/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestIssueRegistry
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintClient.Companion.clientName
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Scope.values
import junit.framework.TestCase
import java.util.EnumSet

class BuiltinIssueRegistryTest : TestCase() {
    override fun setUp() {
        super.setUp()
        clientName = LintClient.CLIENT_UNIT_TESTS
    }

    @Throws(IllegalAccessException::class)
    fun testCapacities() {
        val registry = TestIssueRegistry()
        for (scope in values()) {
            val scopeSet = EnumSet.of(scope)
            checkCapacity(registry, scopeSet)
        }

        // Also check the commonly used combinations
        for (field in Scope::class.java.declaredFields) {
            if (field.type.isAssignableFrom(EnumSet::class.java)) {
                @Suppress("UNCHECKED_CAST")
                checkCapacity(registry, field.get(null) as EnumSet<Scope>)
            }
        }
    }

    fun testSomething() {
        for (issue in BuiltinIssueRegistry().issues) {
            val implementation = issue.implementation
            if (!Scope.checkSingleFile(implementation.scope) && !implementation.analysisScopes.any { Scope.checkSingleFile(it) }) {
                println("${implementation.detectorClass.simpleName}: ${issue.id}")
            }
        }
        /*
CordovaVersionDetector: VulnerableCordovaVersion
InvalidPackageDetector: InvalidPackage
LayoutConsistencyDetector: InconsistentLayout
LocaleFolderDetector: LocaleFolder
LocaleFolderDetector: GetLocales
LocaleFolderDetector: InvalidResourceFolder
LocaleFolderDetector: UseAlpha2
LocaleFolderDetector: WrongRegion
MediaCapabilitiesDetector: MediaCapabilities
MergeRootFrameLayoutDetector: MergeRootFrame
OverdrawDetector: Overdraw
OverrideDetector: DalvikOverride
RequiredAttributeDetector: RequiredSize
StringCasingDetector: DuplicateStrings
TranslucentViewDetector: TranslucentOrientation
UnpackedNativeCodeDetector: UnpackedNativeCode
UnusedResourceDetector: UnusedResources
UnusedResourceDetector: UnusedIds
WrongThreadInterproceduralDetector: WrongThreadInterprocedural
         */
    }

    fun testUnique() {
        // Check that ids are unique
        val ids = HashSet<String>()
        val categories = HashSet<Category>()
        for (issue in TestIssueRegistry().issues) {
            val id = issue.id
            assertTrue("Duplicate id $id", !ids.contains(id))
            ids.add(id)
            categories.add(issue.category)
        }

        // Also make sure that none of the category names clash with
        // the id's since we want to let you enable/disable checks
        // with category names too
        for ((_, id) in categories) {
            if (ids.contains(id)) {
                assertTrue("Category id clashes with issue id $id", !ids.contains(id))
            }
        }

        // Might as well make sure category id's are unique too
        ids.clear()
        for ((_, id) in categories) {
            if (ids.contains(id)) {
                assertTrue("Duplicate category name $id", !ids.contains(id))
            }
        }
    }

    fun testCacheable() {
        val registry = object : BuiltinIssueRegistry() {
            fun isCacheable() = cacheable()
        }
        val old = clientName
        try {
            TestLintClient(LintClient.CLIENT_STUDIO) // side effect: sets client name
            assertTrue(registry.isCacheable())
            TestLintClient(LintClient.CLIENT_GRADLE)
            assertFalse(registry.isCacheable())
        } finally {
            TestLintClient(old)
        }
    }

    private fun checkCapacity(registry: TestIssueRegistry, scopeSet: EnumSet<Scope>) {
        val issuesForScope = registry.getIssuesForScope(scopeSet)
        val requiredSize = issuesForScope.size
        val capacity = registry.getIssueCapacity(scopeSet)
        if (requiredSize > capacity) {
            fail(
                "For Scope set " +
                    scopeSet +
                    ": capacity " +
                    capacity +
                    " < actual " +
                    requiredSize
            )
        }
    }
}
