/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockablePropertyTest {

    private val issueReporter: FakeSyncIssueReporter = FakeSyncIssueReporter()
    private val factory: DslVariableFactory = DslVariableFactory(issueReporter)

    @Test
    fun checkInitial() {
        val testObject = object {
            var x: String by factory.newProperty("initial")
        }
        assertThat(testObject.x).isEqualTo("initial")
    }

    @Test
    fun checkSet() {
        val testObject = object {
            var x: String by factory.newProperty("initial")
        }
        testObject.x = "set"
        assertThat(testObject.x).isEqualTo("set")
    }

    @Test
    fun checkSetAfterLock() {
        val testObject = object {
            var x: String by factory.newProperty("initial")
        }
        testObject.x = "set1"
        factory.disableWrite()
        testObject.x = "set2"
        assertThat(testObject.x).isEqualTo("set1")
        assertThat(issueReporter.errors).hasSize(1)
        assertThat(issueReporter.errors[0]).isEqualTo("""
            It is too late to set property 'x' to 'set2'. (It has value 'set1')
            The DSL is now locked as the variants have been created.
            Either move this call earlier, or use the variant API to customize individual variants.
            """.trimIndent()
        )
    }
}