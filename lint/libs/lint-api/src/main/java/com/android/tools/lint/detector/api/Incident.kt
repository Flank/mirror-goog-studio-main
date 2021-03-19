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
import com.android.tools.lint.client.api.IssueRegistry
import com.google.common.collect.ComparisonChain
import com.google.common.collect.Sets
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.w3c.dom.Node
import java.io.File
import java.util.Comparator
import java.util.HashSet

/**
 * A [Incident] represents a specific error or warning that has been
 * found and reported. The client stores these as they are reported into
 * a list of warnings such that it can sort them all before presenting
 * them all at the end.
 */
class Incident(
    /** The [Issue] corresponding to the incident. */
    var issue: Issue,

    /**
     * The message to display to the user. This message should typically
     * include the details of the specific incident instead of just
     * being a generic message, to make the errors more useful when
     * there are multiple errors in the same file. For example, instead
     * of just saying "Duplicate resource name", say "Duplicate resource
     * name my_string".
     *
     * (Note that the message should be in [TextFormat.RAW] format.)
     */
    var message: String,

    /**
     * The primary location of the error. Secondary locations can be
     * linked from the primary location.
     */
    var location: Location,

    /**
     * The scope element of the error. This is used by lint to search
     * the AST (or XML doc etc) for suppress annotations or comments. In
     * a Kotlin or Java file, this would be the nearest [UElement] or
     * [PsiElement]; in an XML document it's the [Node], etc.
     */
    var scope: Any? = location.source,

    /**
     * A quickfix descriptor, if any, capable of addressing this issue.
     */
    var fix: LintFix? = null
) : Comparable<Incident> {

    // This class has a large number of secondary constructors in order to make it
    // trivial to convert a context.report(args) call into context.report(Incident(args))

    /**
     * Secondary constructor for convenience from Java where default
     * arguments are not available.
     */
    constructor(issue: Issue, message: String, location: Location) :
        this(issue, message, location, null, null)

    /**
     * Secondary constructor for convenience from Java where default
     * arguments are not available.
     */
    constructor(issue: Issue, message: String, location: Location, fix: LintFix?) :
        this(issue, message, location, null, fix)

    /**
     * Secondary constructor which mirrors the old {@link
     * Context#report} signature parameter orders to make it easy to
     * migrate code: just put new Incident() around argument list.
     */
    constructor(issue: Issue, location: Location, message: String) :
        this(issue, message, location, null, null)

    /**
     * Secondary constructor which mirrors the old {@link
     * Context#report} signature parameter orders to make it easy to
     * migrate code: just put new Incident() around argument list.
     */
    constructor(issue: Issue, location: Location, message: String, fix: LintFix?) :
        this(issue, message, location, null, fix)

    /**
     * Secondary constructor which mirrors the old {@link
     * Context#report} signature parameter orders to make it easy to
     * migrate code: just put new Incident() around argument list.
     */
    constructor(issue: Issue, scope: Any, location: Location, message: String) :
        this(issue, message, location, scope, null)

    /**
     * Secondary constructor which mirrors the old {@link
     * Context#report} signature parameter orders to make it easy to
     * migrate code: just put new Incident() around argument list.
     */
    constructor(issue: Issue, scope: Any, location: Location, message: String, fix: LintFix?) :
        this(issue, message, location, scope, fix)

    /** The associated [Project] */
    var project: Project? = null

    /**
     * The display path for this error, which is typically relative to
     * the project's reference dir, but if project is null, it can also
     * be displayed relative to the given root directory.
     */
    fun getDisplayPath(): String {
        return project?.getDisplayPath(file)
            ?: file.path
    }

    /** The associated file; shorthand for [Location.file] */
    val file: File get() = location.file

    /**
     * The starting number of the issue, or -1 if there is no line
     * number (e.g. if it is not in a text file, such as an icon, or if
     * it is not accurately known, as is the case for certain errors in
     * build.gradle files.)
     */
    val line: Int get() = location.start?.line ?: -1

    /**
     * The starting offset or the text range for this incident, or -1 if
     * not known or applicable.
     */
    val startOffset: Int get() = location.start?.offset ?: -1

    /**
     * The ending offset of the text range for this incident, or the
     * same as the [startOffset] if there's no range, just a known
     * starting point.
     */
    val endOffset: Int get() = location.end?.offset ?: startOffset

    /**
     * The severity of the incident. This should typically **not** be
     * set by detectors; it should be computed from the [Configuration]
     * hierarchy by lint itself, such that users can configure lint
     * severities via lint.xml files, build.gradle.kts, and so on.
     */
    var severity: Severity = issue.defaultSeverity

    /** Whether this incident has been fixed via a quickfix. */
    var wasAutoFixed = false

    /**
     * Additional details if this incident is reported in a multiple
     * variants scenario, such as running the "lint" target with the
     * Android Gradle plugin, which will run it repeatedly for each
     * variant and then accumulate information here about which variants
     * the incident is found in and which ones it is not found in.
     */
    var applicableVariants: ApplicableVariants? = null

    /**
     * Data related to this incident. This is intended to be used
     * internally by lint.
     */
    var clientProperties: LintMap? = null

    // Constructor chaining builders

    /**
     * Associated context. This is ONLY used for the chained
     * construction of incidents, allowing convenient location lookup
     * and reporting; it's not persisted across lint invocations etc.
     */
    @Transient
    internal var context: Context? = null

    /**
     * Constructs an [Incident], to be customized with [issue],
     * [message], etc.
     */
    constructor() : this(IssueRegistry.LINT_ERROR, "<missing>", Location.NONE)

    /** Sets the [issue] property. */
    fun issue(issue: Issue): Incident {
        if (this.issue == IssueRegistry.LINT_ERROR) {
            this.severity = issue.defaultSeverity
        }
        this.issue = issue
        return this
    }

    /** Sets the [message] property. */
    fun message(message: String): Incident {
        this.message = message
        return this
    }

    /** Sets the [location] property. */
    fun location(location: Location): Incident {
        this.location = location
        return this
    }

    /**
     * Sets the [severity] property to a specific severity. This
     * overrides the default severity for this issue for this specific
     * instance only, but note that this will only be respected if the
     * issue severity is not configured specifically (for example by
     * Gradle DSL flags like `error 'MyIssue'` or `warningsAsErrors
     * true`.)
     */
    fun overrideSeverity(severity: Severity): Incident {
        this.severity = severity
        return this
    }

    /** Sets the [location] and [scope] properties. */
    fun at(scope: Any): Incident {
        val context = this.context
            ?: error("This method can only be used when the Incident(context) is used")
        this.scope = scope
        location = when (scope) {
            is UElement -> {
                val javaContext = context as? JavaContext
                    ?: error("Associated context must be a JavaContext")
                if (scope is UClass || scope is UMethod) {
                    javaContext.getNameLocation(scope)
                } else {
                    javaContext.getLocation(scope)
                }
            }
            is PsiElement -> {
                val javaContext = context as? JavaContext
                    ?: error("Associated context must be a JavaContext")
                if (scope is PsiClass || scope is PsiMethod) {
                    javaContext.getNameLocation(scope)
                } else {
                    javaContext.getLocation(scope)
                }
                javaContext.getLocation(scope)
            }
            is Node -> {
                val xmlContext = context as? XmlContext
                    ?: error("Associated context must be a JavaContext")
                xmlContext.getLocation(scope)
            }
            else -> {
                if (context is GradleContext) {
                    context.getLocation(scope)
                } else {
                    error(
                        "Could not compute a location for scope element $scope; " +
                            "if necessary use one of the Context.getLocation methods"
                    )
                }
            }
        }
        return this
    }

    /** Sets the [location] property. */
    fun scope(scope: Any?): Incident {
        this.scope = scope
        return this
    }

    /** Sets the [project] property. */
    fun project(project: Project?): Incident {
        this.project = project
        return this
    }

    /** Sets the [fix] property. */
    fun fix(fix: LintFix?): Incident {
        this.fix = fix
        return this
    }

    /**
     * Reports this incident. This is a method here to make it possible
     * to report issues like this:
     *
     *     Incident()
     *       .issue(MY_ISSUE)
     *       .message("This is the message")
     *       .scope(element)
     *       .location(context.getLocation(element)
     *       .report(context)
     */
    fun report(context: Context) {
        context.report(this)
    }

    /**
     * Reports this incident. This is a method here to make it possible
     * to report issues like this:
     *
     *     Incident(context)
     *       .issue(MY_ISSUE)
     *       .message("This is the message")
     *       .at(element)
     *       .report()
     */
    fun report() {
        val context = this.context
            ?: error("This method can only be used when the Incident(context) is used")
        context.report(this)
    }

    // This comparator is used for example to sort incidents in the text and XML reports
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
            .compare(severity, other.severity)
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
        return "Incident(\n issue='$issue',\n message='$message',\n file=${project?.getDisplayPath(file) ?: file},\n line=$line\n)"
    }
}

/**
 * Information about which particular variants an [Incident] applies to.
 */
class ApplicableVariants(
    /** The set of variant names that this incident applies to. */
    private val applicableVariants: Set<String>
) {
    /**
     * Whether this incident is specific to a subset of the applicable
     * variants.
     */
    val variantSpecific: Boolean
        get() = variants.size < applicableVariants.size

    // Storage for the [variants] property
    private var _variants: MutableSet<String>? = null

    /** The set of variants where this incident has been reported. */
    val variants: Set<String>
        get() {
            return _variants ?: emptySet()
        }

    /**
     * Records that this incident has been reported in the named
     * variant.
     */
    fun addVariant(variantName: String) {
        val names = _variants ?: mutableSetOf<String>().also { _variants = it }
        names.add(variantName)
    }

    /**
     * Returns true if this incident is included in more of the
     * applicable variants than those it does not apply to.
     */
    fun includesMoreThanExcludes(): Boolean {
        assert(variantSpecific)
        val variantCount = variants.size
        val allVariantCount = applicableVariants.size
        return variantCount <= allVariantCount - variantCount
    }

    /** The variants this incident is included in. */
    val includedVariantNames: List<String>
        get() = variants.asSequence().sorted().toList()

    /** The variants this incident is not included in. */
    val excludedVariantNames: List<String>
        get() {
            val included: Set<String> = HashSet(includedVariantNames)
            val excluded: Set<String> = Sets.difference(applicableVariants, included)
            return excluded.asSequence().sorted().toList()
        }
}

/** Constructs an incident associated with the given [context] */
fun Incident(context: Context): Incident {
    val incident = Incident()
    incident.context = context
    return incident
}

/**
 * Constructs an incident associated with the given [context] and
 * [issue] type.
 */
fun Incident(context: Context, issue: Issue): Incident {
    val incident = Incident()
    incident.issue = issue
    incident.context = context
    return incident
}
