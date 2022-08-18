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

package com.android.build.gradle.internal.services

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.google.common.truth.Truth
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/** Unit tests for [LintParallelBuildService] */
class LintParallelBuildServiceTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var projectOptions: ProjectOptions

    @Test fun testCalculateMaxParallelUsages() {
        // first test in process cases
        Mockito.`when`(projectOptions.get(BooleanOption.RUN_LINT_IN_PROCESS)).thenReturn(true)

        // Check normal case
        Truth.assertThat(
            LintParallelBuildService.calculateMaxParallelUsages(
                projectOptions,
                maxRuntimeMemory = 20 * GB,
                totalPhysicalMemory = 40 * GB
            )
        ).isEqualTo(30)

        // Check case when there's not enough memory, but should still return 1
        Truth.assertThat(
            LintParallelBuildService.calculateMaxParallelUsages(
                projectOptions,
                maxRuntimeMemory = 0,
                totalPhysicalMemory = 40 * GB
            )
        ).isEqualTo(1)

        // then test out of process cases
        Mockito.`when`(projectOptions.get(BooleanOption.RUN_LINT_IN_PROCESS)).thenReturn(false)

        // Check no specified lint heap size
        Mockito.`when`(projectOptions.get(StringOption.LINT_HEAP_SIZE)).thenReturn(null)
        Truth.assertThat(
            LintParallelBuildService.calculateMaxParallelUsages(
                projectOptions,
                maxRuntimeMemory = 10 * GB,
                totalPhysicalMemory = 40 * GB
            )
        ).isEqualTo(2)

        // Check with specified lint heap size
        Mockito.`when`(projectOptions.get(StringOption.LINT_HEAP_SIZE)).thenReturn("2g")
        Truth.assertThat(
            LintParallelBuildService.calculateMaxParallelUsages(
                projectOptions,
                maxRuntimeMemory = 10 * GB,
                totalPhysicalMemory = 40 * GB
            )
        ).isEqualTo(18)

        // Check case when there's not enough memory, but should still return 1
        Truth.assertThat(
            LintParallelBuildService.calculateMaxParallelUsages(
                projectOptions,
                maxRuntimeMemory = 1 * GB,
                totalPhysicalMemory = 1 * GB
            )
        ).isEqualTo(1)

        // Check case when user specifies invalid lint heap size
        Mockito.`when`(projectOptions.get(StringOption.LINT_HEAP_SIZE)).thenReturn("invalid")
        try {
            LintParallelBuildService.calculateMaxParallelUsages(
                projectOptions,
                maxRuntimeMemory = 1 * GB,
                totalPhysicalMemory = 1 * GB
            )
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            Truth.assertThat(e.message)
                .isEqualTo(
                    "Failed to parse ${StringOption.LINT_HEAP_SIZE.propertyName} \"invalid\"."
                )
        }
    }
}

private const val GB = 1024 * 1024 * 1024L
