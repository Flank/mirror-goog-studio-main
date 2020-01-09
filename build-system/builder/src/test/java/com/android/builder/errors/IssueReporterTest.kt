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

package com.android.builder.errors

import com.android.builder.model.SyncIssue
import org.junit.Test

class IssueReporterTest {

    @Test
    fun `check that sync issues have valid type names and type values`() {
        // Each sync issue has a type. Each type has a name and a value.
        // E.g., sync issue PLUGIN_OBSOLETE has a type named TYPE_PLUGIN_OBSOLETE with value 1.

        // First, build a map from the type's value to the type's name.
        val typeValueToNameMap = mutableMapOf<Int, String>()
        val syncIssueTypes = SyncIssue::class.java.fields.filter { it.name.startsWith("TYPE_") }
        for (type in syncIssueTypes) {
            val typeValue = type.getInt(SyncIssue::class.java)
            typeValueToNameMap[typeValue] = type.name
        }

        // Then, check that each sync issue has a valid type name and type value.
        for ((index, syncIssueField) in IssueReporter.Type::class.java.fields.withIndex()) {
            val syncIssue =
                syncIssueField.get(IssueReporter.Type::class.java) as IssueReporter.Type

            val expectedTypeName = "TYPE_${syncIssue.name}"
            // The expected type value shifts at index 29 due to bug 138278313.
            val expectedTypeValue = if (index >= 29) index + 1 else index

            val actualTypeName = typeValueToNameMap[syncIssue.type]
            val actualTypeValue = syncIssue.type

            assert(expectedTypeName == actualTypeName)
                    { "Expected type name $expectedTypeName" +
                            " but found $actualTypeName for ${syncIssue.name}" }
            assert(expectedTypeValue == actualTypeValue)
                    { "Expected type value $expectedTypeValue" +
                            " but found $actualTypeValue for ${syncIssue.name}" }
        }
    }
}