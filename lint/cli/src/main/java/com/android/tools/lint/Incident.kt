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
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.google.common.collect.ComparisonChain
import com.google.common.collect.Sets
import java.io.File
import java.util.Comparator
import java.util.HashSet

/**
 * A [Incident] represents a specific error or warning that has been found and reported.
 * The client stores these as they are reported into a list of warnings such that it can
 * sort them all before presenting them all at the end.
 */
class Incident(
    /** The [Issue] type of the in incident */
    val issue: Issue,

    /** The associated error message, in [TextFormat.RAW] format */
    val message: String,

    /** The [Location] of the incident */
    val location: Location,

    /** An optional [LintFix] which can address the issue */
    val fix: LintFix? = null
) : Comparable<Incident> {

    /** The associated [Project] */
    var project: Project? = null

    /**
     * The display path for this error, which is typically relative to the
     * project's reference dir
     */
    val displayPath: String get() = project?.getDisplayPath(file) ?: file.path

    /** The associated file; shorthand for [Location.file] */
    val file: File get() = location.file

    /**
     * The starting number of the issue, or -1 if there is no line number (e.g. if
     * it is not in a text file, such as an icon, or if it is not accurately known, as is
     * the case for certain errors in build.gradle files.)
     */
    val line: Int get() = location.start?.line ?: -1

    /**
     * The starting offset or the text range for this incident, or -1 if not known or applicable.
     */
    val startOffset: Int get() = location.start?.offset ?: -1

    /**
     * The ending offset of the text range for this incident, or the same as the [startOffset]
     * if there's no range, just a known starting point.
     */
    val endOffset: Int get() = location.end?.offset ?: startOffset

    /**
     * The severity of the incident. This should typically **not** be set by detectors; it
     * should be computed from the [Configuration] hierarchy by lint itself, such that
     * users can configure lint severities via lint.xml files, build.gradle.kts, and so on.
     */
    var severity: Severity = issue.defaultSeverity

    /** Whether this incident has been fixed via a quickfix */
    var wasAutoFixed = false

    /**
     * Additional details if this incident is reported in a multiple variants
     * scenario, such as running the "lint" target with the Android Gradle plugin,
     * which will run it repeatedly for each variant and then accumulate information
     * here about which variants the incident is found in and which ones it is not
     * found in
     */
    var applicableVariants: ApplicableVariants? = null

    /**
     * Whether this incident has a fix that can be automatically performed without
     * user intervention
     */
    fun hasAutoFix(): Boolean {
        val fixData = fix ?: return false
        return canAutoFix(fixData)
    }

    override fun compareTo(other: Incident): Int {
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
        } else this.compareTo(other as Incident) == 0
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + file.hashCode()
        return result
    }

    override fun toString(): String {
        return "Warning{issue=$issue, message='$message', file=$file, line=$line}"
    }
}

/** Information about which particular variants an [Incident] applies to */
class ApplicableVariants(
    /** The set of variant names that this incident applies to */
    private val applicableVariants: Set<String>
) {
    /** Whether this incident is specific to a subset of the applicable variants */
    val variantSpecific: Boolean
        get() = variants.size < applicableVariants.size

    // Storage for the [variants] property
    private var _variants: MutableSet<String>? = null

    /** The set of variants where this incident has been reported */
    val variants: Set<String>
        get() {
            return _variants ?: emptySet()
        }

    /** Records that this incident has been reported in the named variant */
    fun addVariant(variantName: String) {
        val names = _variants ?: mutableSetOf<String>().also { _variants = it }
        names.add(variantName)
    }

    /**
     * Returns true if this incident is included in more of the applicable variants than
     * those it does not apply to
     */
    fun includesMoreThanExcludes(): Boolean {
        assert(variantSpecific)
        val variantCount = variants.size
        val allVariantCount = applicableVariants.size
        return variantCount <= allVariantCount - variantCount
    }

    /** The variants this incident is included in */
    val includedVariantNames: List<String>
        get() = variants.asSequence().sorted().toList()

    /** The variants this incident is not included in */
    val excludedVariantNames: List<String>
        get() {
            val included: Set<String> = HashSet(includedVariantNames)
            val excluded: Set<String> = Sets.difference(applicableVariants, included)
            return excluded.asSequence().sorted().toList()
        }
}
