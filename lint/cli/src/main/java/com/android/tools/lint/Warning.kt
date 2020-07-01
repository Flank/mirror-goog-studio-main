/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tools.lint

import com.android.tools.lint.LintFixPerformer.Companion.canAutoFix
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.google.common.collect.ComparisonChain
import com.google.common.collect.Sets
import java.io.File
import java.util.Comparator
import java.util.HashSet

/**
 * A [Warning] represents a specific warning that has been reported.
 * The client stores these as they are reported into a list of warnings such that it can
 * sort them all before presenting them all at the end.
 */
class Warning(
    client: LintCliClient,
    val issue: Issue,
    val message: String,
    val severity: Severity,
    val project: Project,
    val location: Location,
    val fileContents: CharSequence? = null,
    val errorLine: String? = null,
    val fix: LintFix? = null,
    path: String? = null
) : Comparable<Warning> {
    val file: File = location.file
    val displayPath: String = path ?: client.getDisplayPath(project, location.file) ?: file.path
    val line: Int get() = location.start?.line ?: -1
    val startOffset: Int get() = location.start?.offset ?: -1
    val endOffset: Int get() = location.end?.offset ?: startOffset

    var allVariants: Set<String>? = null
    var variants: MutableSet<String>? = null
    var wasAutoFixed = false

    override fun compareTo(other: Warning): Int {
        val fileName1 = file.name
        val fileName2 = other.file.name
        val start1 = location.start
        val start2 = other.location.start
        val col1 = start1?.column
        val col2 = start2?.column
        val secondary1 = location.secondary
        val secondary2 = other.location.secondary
        val secondFile1 = secondary1?.file
        val secondFile2 = secondary2?.file
        return ComparisonChain.start()
            .compare(issue.category, other.issue.category)
            .compare(
                issue.priority,
                other.issue.priority,
                Comparator.reverseOrder()
            )
            .compare(issue.id, other.issue.id)
            .compare(
                fileName1,
                fileName2,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .compare(line, other.line)
            .compare(message, other.message)
            .compare(
                file,
                other.file,
                Comparator.nullsLast(Comparator.naturalOrder())
            ) // This handles the case where you have a huge XML document without newlines,
            // such that all the errors end up on the same line.
            .compare(
                col1,
                col2,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .compare(
                secondFile1,
                secondFile2,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .result()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other == null || javaClass != other.javaClass) {
            false
        } else this.compareTo(other as Warning) == 0
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + file.hashCode()
        return result
    }

    fun hasAutoFix(): Boolean {
        val fixData = fix ?: return false
        return canAutoFix(fixData)
    }

    val variantSpecific: Boolean
        get() {
            val variantSize = variants?.size ?: return false
            val allVariantsSize = allVariants?.size ?: return false
            return variantSize < allVariantsSize
        }

    fun includesMoreThanExcludes(): Boolean {
        assert(variantSpecific)
        val variantCount = variants!!.size
        val allVariantCount = allVariants!!.size
        return variantCount <= allVariantCount - variantCount
    }

    val includedVariantNames: List<String>
        get() {
            assert(variantSpecific)
            return variants?.asSequence()?.sorted()?.toList() ?: emptyList()
        }

    val excludedVariantNames: List<String>
        get() {
            assert(variantSpecific)
            val included: Set<String> = HashSet(includedVariantNames)
            val excluded: Set<String> = Sets.difference(allVariants!!, included)
            return excluded.asSequence().sorted().toList()
        }

    override fun toString(): String {
        return "Warning{issue=$issue, message='$message', file=$file, line=$line}"
    }
}
