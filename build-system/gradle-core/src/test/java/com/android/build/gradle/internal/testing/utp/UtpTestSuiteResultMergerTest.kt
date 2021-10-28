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

package com.android.build.gradle.internal.testing.utp

import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus

/**
 * Unit tests for [UtpTestSuiteResultMerger].
 */
class UtpTestSuiteResultMergerTest {
    private fun merge(vararg results: TestSuiteResult): TestSuiteResult {
        val merger = UtpTestSuiteResultMerger()
        results.forEach(merger::merge)
        return merger.result
    }

    private val passedResult = TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_status: PASSED
            test_result {
              test_case {
                test_class: "ExamplePassedInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: PASSED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)

    private val skippedResult = TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_status: SKIPPED
            test_result {
              test_case {
                test_class: "ExampleSkippedInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: SKIPPED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)

    private val failedResult = TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_status: FAILED
            test_result {
              test_case {
                test_class: "ExampleFailedInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: FAILED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)

    @Test
    fun mergeZeroResults() {
        assertThat(merge()).isEqualTo(TestSuiteResult.getDefaultInstance())
    }

    @Test
    fun mergePassedAndFailedResults() {
        val mergedResult = merge(passedResult, failedResult)

        assertThat(mergedResult.testStatus).isEqualTo(TestStatus.FAILED)
        assertThat(mergedResult.testSuiteMetaData.scheduledTestCaseCount).isEqualTo(2)
        assertThat(mergedResult.testResultList).hasSize(2)
    }

    @Test
    fun mergePassedAndSkippedResults() {
        val mergedResult = merge(passedResult, skippedResult)

        assertThat(mergedResult.testStatus).isEqualTo(TestStatus.PASSED)
        assertThat(mergedResult.testSuiteMetaData.scheduledTestCaseCount).isEqualTo(2)
        assertThat(mergedResult.testResultList).hasSize(2)
    }
}
