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
package com.android.tools.lint.detector.api

import com.android.tools.lint.client.api.Configuration
import com.google.common.collect.ComparisonChain
import com.google.common.collect.Sets
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Contract
import org.jetbrains.uast.UElement
import org.w3c.dom.Node
import java.io.File
import java.util.Comparator
import java.util.HashSet

/**
 * A [Incident] represents a specific error or warning that has been found and reported.
 * The client stores these as they are reported into a list of warnings such that it can
 * sort them all before presenting them all at the end.
 */
class Incident(
    /** The [Issue] corresponding to the incident */
    val issue: Issue,

    /**
     * The message to display to the user. This message should typically include the
     * details of the specific incident instead of just being a generic message, to make the
     * errors more useful when there are multiple errors in the same file. For example,
     * instead of just saying "Duplicate resource name", say "Duplicate resource name my_string".
     *
     * (Note that the message should be in [TextFormat.RAW] format.)
     */
    var message: String,

    /**
     * The primary location of the error. Secondary locations can be linked from the
     * primary location.
     */
    var location: Location,

    /**
     * The scope element of the error. This is used by lint to search the AST (or XML doc
     * etc) for suppress annotations or comments. In a Kotlin or Java file, this would be
     * the nearest [UElement] or [PsiElement]; in an XML document it's the [Node], etc.
     */
    val scope: Any? = null,

    /** A quickfix descriptor, if any, capable of addressing this issue */
    val fix: LintFix? = null
) : Comparable<Incident> {
    /** Secondary constructor for convenience from Java where default arguments are not available */
    constructor(issue: Issue, message: String, location: Location) :
        this(issue, message, location, null, null)

    /** Secondary constructor for convenience from Java where default arguments are not available */
    constructor(issue: Issue, message: String, location: Location, fix: LintFix?) :
        this(issue, message, location, null, fix)

    /**
     * Secondary constructor which mirrors the old {@link Context#report} signature
     * parameter orders to make it easy to migrate code: just put new Indent() around
     * argument list
     */
    constructor(issue: Issue, location: Location, message: String) :
        this(issue, message, location, null, null)

    /**
     * Secondary constructor which mirrors the old {@link Context#report} signature
     * parameter orders to make it easy to migrate code: just put new Indent() around
     * argument list
     */
    constructor(issue: Issue, location: Location, message: String, fix: LintFix?) :
        this(issue, message, location, null, fix)

    /**
     * Secondary constructor which mirrors the old {@link Context#report} signature
     * parameter orders to make it easy to migrate code: just put new Indent() around
     * argument list
     */
    constructor(issue: Issue, scope: Any, location: Location, message: String) :
        this(issue, message, location, scope, null)

    /**
     * Secondary constructor which mirrors the old {@link Context#report} signature
     * parameter orders to make it easy to migrate code: just put new Indent() around
     * argument list
     */
    constructor(issue: Issue, scope: Any, location: Location, message: String, fix: LintFix?) :
        this(issue, message, location, scope, fix)

    /** The associated [Project] */
    var project: Project? = null

    /**
     * The display path for this error, which is typically relative to the
     * project's reference dir, but if project is null, it can also be
     * displayed relative to the given root directory
     */
    fun getDisplayPath(): String {
        return project?.getDisplayPath(file)
            ?: file.path
    }

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
     * Notes related to this incident. This is intended to be used by
     * detectors when asked to merge previously reported results from
     * upstream modules.
     *
     * This is limited to a few primitive data types (because it needs
     * to be safely persisted across lint invocations.)
     */
    var notes: LintMap? = null

    /**
     * Records a string note related to this incident.
     *
     * This is intended to be used by detectors when asked to merge
     * previously reported results from upstream modules.
     */
    fun put(key: String, value: String): Incident {
        val map = notes ?: LintMap().also { notes = it }
        map.put(key, value)
        return this
    }

    /** Like [put] but for integers */
    fun put(key: String, value: Int): Incident {
        val map = notes ?: LintMap().also { notes = it }
        map.put(key, value)
        return this
    }

    /** Like [put] but for booleans */
    fun put(key: String, value: Boolean): Incident {
        val map = notes ?: LintMap().also { notes = it }
        map.put(key, value)
        return this
    }

    /** Returns a note previously stored as a String by [put] */
    @Contract("_, !null -> !null")
    fun getString(key: String, default: String? = null): String? {
        return notes?.getString(key, default)
    }

    /** Returns a note previously stored as an integer by [put] */
    @Contract("_, !null -> !null")
    fun getInt(key: String, default: Int? = null): Int? {
        return notes?.getInt(key, default)
    }

    /** Returns an API level previously stored as an integer or string by [put] */
    @Contract("_, !null -> !null")
    fun getApi(key: String, default: Int? = null): Int? {
        return notes?.getApi(key, default)
    }

    /** Returns a note previously stored as a boolean by [put] */
    @Contract("_, !null -> !null")
    fun getBoolean(key: String, default: Boolean? = null): Boolean? {
        return notes?.getBoolean(key, default)
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
            )
            // This handles the case where you have a huge XML document without newlines,
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
        return "Incident(issue='$issue', message='$message', file=${project?.getDisplayPath(file) ?: file}, line=$line)"
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
