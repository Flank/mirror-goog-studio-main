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

package com.android.tools.lint.detector.api

import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.SUPPRESS_ALL
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_MANIFEST
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintDriver.Companion.STUDIO_ID_PREFIX
import com.android.tools.lint.client.api.LintDriver.DriverMode
import com.android.tools.lint.client.api.SdkInfo
import com.android.utils.CharSequences
import com.android.utils.CharSequences.indexOf
import com.google.common.annotations.Beta
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.EnumSet
import java.util.HashSet

/**
 * Context passed to the detectors during an analysis run. It provides
 * information about the file being analyzed, it allows shared
 * properties (so the detectors can share results), etc.
 *
 * *NOTE: This is not a public or final API; if you rely on this be
 * prepared to adjust your code for the next tools release.**
 */
@Beta
open class Context(
    /** The driver running through the checks. */
    val driver: LintDriver,
    /** The project containing the file being checked. */
    val project: Project,
    /**
     * The "main" project. For normal projects, this is the same as
     * [project], but for library projects, it's the root project that
     * includes (possibly indirectly) the various library projects and
     * their library projects.
     *
     * Note that this is a property on the [Context], not the [Project],
     * since a library project can be included from multiple different
     * top level projects, so there isn't **one** main project, just
     * one per main project being analyzed with its library projects.
     */
    private val main: Project?,

    /**
     * The file being checked. Note that this may not always be to a
     * concrete file. For example, in the [Detector.beforeCheckProject]
     * method, the context file is the directory of the project.
     */
    @JvmField
    val file: File,

    /** The contents of the file. */
    private var contents: CharSequence? = null
) {

    /**
     * The current configuration controlling which checks are enabled
     * etc.
     */
    val configuration: Configuration = client.getConfiguration(file)
        ?: project.getConfiguration(driver)

    /**
     * Whether this file contains any suppress markers (null means not
     * yet determined)
     */
    private var containsCommentSuppress: Boolean? = null

    /** The scope for the lint job. */
    val scope: EnumSet<Scope>
        get() = driver.scope

    /**
     * Returns the main project if this project is a library project,
     * or self if this is not a library project. The main project is
     * the root project of all library projects, not necessarily the
     * directly including project.
     *
     * @return the main project, never null
     */
    val mainProject: Project
        get() {
            if (forbidMainAccess) {
                val forbidden = checkForbidden("context.getMainProject()", file, driver)
                if (forbidden) {
                    return project
                }
            }
            return main ?: project
        }

    /** The lint client requesting the lint check. */
    val client: LintClient
        get() = driver.client

    /**
     * Returns the contents of the file. This may not be the contents of
     * the file on disk, since it delegates to the [LintClient], which
     * in turn may decide to return the current edited contents of the
     * file open in an editor.
     *
     * @return the contents of the given file, or null if an error
     *     occurs.
     */
    open fun getContents(): CharSequence? {
        if (contents == null) {
            contents = driver.client.readFile(file)
        }

        return contents
    }

    /**
     * Gets the SDK info for the current project.
     *
     * @return the SDK info for the current project, never null
     */
    val sdkInfo: SdkInfo
        get() = project.getSdkInfo()

    /**
     * Returns the location of the given [node], which could be an AST
     * node, an XML element, and so on.
     */
    fun getLocation(node: Any?, type: LocationType = LocationType.DEFAULT): Location {
        node ?: return Location.NONE

        // Switch location lookup by element type. Note that we handle all the cases
        // here instead of via overriding this method in each [Context] subclass
        // because we want to allow looking up locations even if the context isn't
        // of the right type (which is relevant when you for example try to look up
        // nodes in an after-analysis project hook).
        when (node) {
            is UElement -> {
                val context: JavaContext = if (this is JavaContext) {
                    this
                } else {
                    val file = node.sourcePsi?.containingFile?.virtualFile?.let {
                        VfsUtilCore.virtualToIoFile(it)
                    } ?: file
                    JavaContext(driver, project, main, file)
                }
                return when (type) {
                    LocationType.DEFAULT -> context.getLocation(node)
                    LocationType.ALL -> context.getLocation(node)
                    LocationType.NAME -> context.getNameLocation(node)
                    LocationType.CALL_WITH_ARGUMENTS -> context.getCallLocation(
                        node as UCallExpression,
                        includeReceiver = false,
                        includeArguments = true
                    )
                    LocationType.CALL_WITH_RECEIVER -> context.getCallLocation(
                        node as UCallExpression,
                        includeReceiver = true,
                        includeArguments = false
                    )
                    LocationType.VALUE -> error("$type not supported for ${node.javaClass}")
                }
            }
            is PsiElement -> {
                val context: JavaContext = if (this is JavaContext) {
                    this
                } else {
                    val file = node.containingFile?.virtualFile?.let {
                        VfsUtilCore.virtualToIoFile(it)
                    } ?: file
                    JavaContext(driver, project, main, file)
                }
                return when (type) {
                    LocationType.DEFAULT -> context.getLocation(node)
                    LocationType.ALL -> context.getLocation(node)
                    LocationType.NAME -> context.getNameLocation(node)
                    LocationType.CALL_WITH_ARGUMENTS -> context.getCallLocation(
                        node as UCallExpression,
                        includeReceiver = false,
                        includeArguments = true
                    )
                    LocationType.CALL_WITH_RECEIVER -> context.getCallLocation(
                        node as UCallExpression,
                        includeReceiver = true,
                        includeArguments = false
                    )
                    LocationType.VALUE -> error("$type not supported for ${node.javaClass}")
                }
            }
            is Node -> {
                if (client.isMergeManifestNode(node)) {
                    if (node.nodeName == TAG_APPLICATION && node.parentNode?.nodeName == TAG_MANIFEST) {
                        // The manifest merger does a really poor job picking the right blame file
                        // attribution for the application element -- it is often the first library
                        // in the path that it processed (such as unpacked AAR library manifest
                        // resources in .gradle/caches/transforms-3/...). Special case this element
                        // to the current project's manifest
                        return findNodeInProject(node, type)
                    }
                    val source = client.findManifestSourceNode(node)
                    if (source != null && source.second !== node) {
                        //noinspection FileComparisons
                        if (source.first == file) {
                            return getLocation(source.second, type)
                        }
                        val doc = node.ownerDocument
                        val xmlContext = XmlContext(
                            driver, project, main, source.first, null, null, doc
                        )
                        return xmlContext.getLocation(source.second, type)
                    }
                }

                // Fallback if we're calling this on a project context and we
                // have no manifest merger records to fall back on: check source
                // manifests.
                if (!file.path.endsWith(DOT_XML)) {
                    return findNodeInProject(node, type)
                }

                val context: XmlContext = if (this is XmlContext) {
                    this
                } else {
                    val doc = node.ownerDocument
                    val file = doc.getUserData(File::class.java.name) as? File
                        ?: (doc.getUserData(PsiFile::class.java.name) as? PsiFile)?.virtualFile?.let {
                            VfsUtilCore.virtualToIoFile(it)
                        }
                        ?: return Location.create(project.getManifestFiles().firstOrNull() ?: project.dir)
                    // We're only calling location methods here so we don't need an accurate
                    // folder type for example
                    XmlContext(driver, project, main, file, null, null, doc)
                }
                return when (type) {
                    LocationType.ALL -> context.getLocation(node)
                    LocationType.DEFAULT ->
                        if (node is Element)
                            context.getElementLocation(node)
                        else
                            context.getLocation(node)
                    LocationType.NAME -> context.getNameLocation(node)
                    LocationType.VALUE ->
                        if (node is Attr)
                            context.getValueLocation(node)
                        else
                            context.getLocation(node)
                    LocationType.CALL_WITH_ARGUMENTS,
                    LocationType.CALL_WITH_RECEIVER -> error("$type not supported for ${node.javaClass}")
                }
            }
            is ClassNode -> {
                if (this is ClassContext) {
                    return this.getLocation(node)
                } else {
                    error("Can only get ClassNode locations on a ClassContext")
                }
            }
            is AbstractInsnNode -> {
                if (this is ClassContext) {
                    return this.getLocation(node)
                } else {
                    error("Can only get AbstractInsnNode locations on a ClassContext")
                }
            }
            else -> {
                if (this is GradleContext) {
                    return this.getLocation(node)
                }
            }
        }

        return Location.create(file)
    }

    /**
     * Given a merged node, search in the projects (starting with this
     * one) to find a match in one of the existing manifest files.
     * If none is found just return this project's primary location.
     */
    private fun findNodeInProject(
        node: Node,
        type: LocationType
    ): Location {
        // Project context - need to find the right source file
        // when there's no merge manifest file
        val element = when (node) {
            is Element -> node
            is Attr -> {
                node.ownerElement
            }
            else -> null
        }
        if (element != null) {
            val projects = sequenceOf(project) +
                project.allLibraries.filter { !it.isExternalLibrary }
            for (p in projects) {
                for (manifest in p.getManifestFiles()) {
                    try {
                        val document = client.xmlParser.parseXml(manifest) ?: continue
                        val sourceNode: Node? = matchXmlElement(element, document)
                        if (sourceNode != null && sourceNode !== element) {
                            val doc = node.ownerDocument
                            val xmlContext = XmlContext(
                                driver, p, main, manifest, null, null, doc
                            )
                            return xmlContext.getLocation(sourceNode, type)
                        }
                    } catch (ignore: Exception) {
                    }
                }
            }
        }
        return Location.create(project.getManifestFiles().firstOrNull() ?: project.dir)
    }

    // ---- Convenience wrappers  ---- (makes the detector code a bit leaner)

    /**
     * Returns false if the given issue has been disabled. Convenience
     * wrapper around [Configuration.getSeverity].
     *
     * @param issue the issue to check
     * @return false if the issue has been disabled
     */
    fun isEnabled(issue: Issue): Boolean = configuration.isEnabled(issue)

    /**
     * Reports an issue. Convenience wrapper around [LintClient.report]
     *
     * @param issue the issue to report
     * @param location the location of the issue
     * @param message the message for this warning
     * @param quickfixData parameterized data for IDE quickfixes
     */
    /*
    Not deprecating this yet: wait until report(Incident) has been available for
    a reasonable number of releases such that third party checks can rely on it
    being present in all lint versions it will be run with. Note that all the
    report methods should be annotated like this, not just this one:
    @Deprecated(
        "Use the new report(Incident) method instead, which is more future proof",
        ReplaceWith(
            "report(Incident(issue, message, location, null, quickfixData))",
            "com.android.tools.lint.detector.api.Incident"
        )
    )
    */
    @JvmOverloads
    open fun report(
        issue: Issue,
        location: Location,
        message: String,
        quickfixData: LintFix? = null
    ) {
        val incident = Incident(issue, location, message, quickfixData)
        driver.client.report(this, incident)
    }

    /**
     * Reports the given incident.
     *
     * See [LintClient.report] for more details.
     */
    fun report(incident: Incident) {
        driver.client.report(this, incident)
    }

    /**
     * Returns true if lint is running in "global" mode: This is where
     * lint is analyzing the whole project, where it is given access to
     * for example library dependencies' sources (if local), where you
     * can look up the main project via [Context.mainProject].
     *
     * This is the opposite of "partial analysis", where lint can be
     * either analyzing an individual module, or performing reporting
     * where it merges individual module results. You can look up the
     * exact mode via [LintDriver.mode].
     */
    fun isGlobalAnalysis(): Boolean = driver.isGlobalAnalysis()

    /**
     * Reports the given incident to lint as a provisional incident,
     * meaning that it is not yet conclusive. Lint will evaluate the
     * given [constraint] for each project that depends on this one and
     * check the condition against the parameters in that project.
     */
    fun report(incident: Incident, constraint: Constraint) {
        client.report(this, incident, constraint)
    }

    /**
     * Reports the given incident to lint as a conditional incident,
     * meaning that it is not yet conclusive. Lint will later ask the
     * detector to decide, via [Detector.filterIncident] whether the
     * issue should be included in the reporting project's report or
     * not. The map can be used to store and retrieve state, since the
     * callback will happen on a different instance of the [Detector].
     *
     * See [LintClient.report] for more details.
     */
    fun report(incident: Incident, map: LintMap) {
        client.report(this, incident, map)
    }

    /**
     * Returns a [PartialResult] where state can be stored for later
     * analysis. This is a more general mechanism for reporting
     * provisional issues when you need to collect a lot of data and do
     * some post processing before figuring out what to report and you
     * can't enumerate out specific [Incident] occurrences up front.
     *
     * Note that in this case, the lint infrastructure will not
     * automatically look up the error location (since there isn't one
     * yet) to see if the issue has been suppressed (via annotations,
     * lint.xml and other mechanisms), so you should do this
     * yourself, via the various [LintDriver.isSuppressed] methods.
     */
    fun getPartialResults(issue: Issue): PartialResult {
        return client.getPartialResults(project, issue)
    }

    /** Finds the right configuration to use for the given file. */
    fun findConfiguration(file: File): Configuration {
        val configurations = driver.client.configurations
        val dir = file.parentFile
        return configurations.getConfigurationForFolder(dir)
            ?: run {
                // If this error was computed for a context where the context corresponds to
                // a project instead of a file, the actual error may be in a different project (e.g.
                // a library project), so adjust the configuration as necessary.
                val project = driver.findProjectFor(file)
                project?.getConfiguration(driver) ?: configuration
            }
    }

    /**
     * Send an exception to the log. Convenience wrapper around
     * [LintClient.log].
     *
     * @param exception the exception, possibly null
     * @param format the error message using [java.lang.String.format]
     *     syntax, possibly null
     * @param args any arguments for the format string
     */
    fun log(
        exception: Throwable?,
        format: String?,
        vararg args: Any
    ) = driver.client.log(exception, format, *args)

    /**
     * Returns the current phase number. The first pass is phase number
     * one, and only one pass will be performed, unless a [Detector]
     * calls [requestRepeat].
     *
     * @return the current phase, usually 1
     */
    val phase: Int
        get() = driver.phase

    /**
     * Requests another pass through the data for the given detector.
     * This is typically done when a detector needs to do more expensive
     * computation, but it only wants to do this once it **knows** that
     * an error is present, or once it knows more specifically what to
     * check for.
     *
     * @param detector the detector that should be included in the next
     *     pass. Note that the lint runner may
     *     refuse to run more than a couple of runs.
     * @param scope the scope to be revisited. This must be a subset of
     *     the current scope ([this.scope], and it is just
     *     a performance hint; in particular, the detector
     *     should be prepared to be called on other scopes as
     *     well (since they may have been requested by other
     *     detectors). You can pall null to indicate "all".
     */
    fun requestRepeat(detector: Detector, scope: EnumSet<Scope>?) =
        driver.requestRepeat(detector, scope)

    /**
     * Returns the comment marker used in Studio to suppress statements
     * for language, if any.
     */
    protected open val suppressCommentPrefix: String?
        get() {
            val path = file.path
            if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_GRADLE)) {
                return SUPPRESS_JAVA_COMMENT_PREFIX
            } else if (path.endsWith(DOT_XML)) {
                return SUPPRESS_XML_COMMENT_PREFIX
            } else if (path.endsWith(".cfg") || path.endsWith(".pro")) {
                return "#suppress "
            }

            return null
        }

    /**
     * Returns whether this file contains any suppress comment markers.
     */
    fun containsCommentSuppress(): Boolean {
        if (containsCommentSuppress == null) {
            containsCommentSuppress = false
            val prefix = suppressCommentPrefix
            if (prefix != null) {
                val contents = getContents()
                if (contents != null) {
                    containsCommentSuppress = indexOf(contents, prefix) != -1
                }
            }
        }

        return containsCommentSuppress!!
    }

    /**
     * Returns true if the given issue is suppressed at the given
     * character offset in the file's contents.
     */
    fun isSuppressedWithComment(startOffset: Int, issue: Issue): Boolean {
        val prefix = suppressCommentPrefix ?: return false

        if (startOffset <= 0) {
            return false
        }

        // Check whether there is a comment marker
        val contents: CharSequence = getContents() ?: ""
        if (startOffset >= contents.length) {
            return false
        }

        // Scan backwards to the previous line and see if it contains the marker
        val lineStart = contents.lastIndexOf('\n', startOffset) + 1
        if (lineStart <= 1) {
            return false
        }
        val index = findPrefixOnPreviousLine(contents, lineStart, prefix)
        if (index != -1 && index + prefix.length < lineStart) {
            val line = contents.subSequence(index + prefix.length, lineStart).toString()
            return isSuppressedWithComment(line, issue)
        }

        return false
    }

    /**
     * When set, access to state outside of the current project is not
     * allowed. This is intended for lint infrastructure usage.
     */
    private val forbidMainAccess: Boolean get() = driver.mode == DriverMode.ANALYSIS_ONLY

    companion object {
        @VisibleForTesting
        fun isSuppressedWithComment(line: String, issue: Issue): Boolean {
            return lineContainsId(line, issue.id) ||
                lineContainsId(line, SUPPRESS_ALL) ||
                isSuppressedWithComment(line, issue.category)
        }

        private fun isSuppressedWithComment(line: String, category: Category): Boolean {
            return lineContainsId(line, category.name) ||
                lineContainsId(line, category.fullName) ||
                category.parent != null && isSuppressedWithComment(line, category.parent)
        }

        // Like line.contains(id), but requires word match (e.g. "MyId" is found
        // in "SomeId,MyId" but not in "NotMyId")
        private fun lineContainsId(line: String, id: String): Boolean {
            var index = 0
            while (index < line.length) {
                index = line.indexOf(id, startIndex = index, ignoreCase = true)
                if (index == -1) {
                    return false
                }

                if (isWord(line, id, index)) {
                    return true
                }

                index += id.length
            }

            return false
        }

        private fun isWord(line: String, word: String, index: Int): Boolean {
            val end = index + word.length
            if (end < line.length && !isWordDelimiter(line[end])) {
                return false
            }

            if (index > 0 && !isWordDelimiter(line[index - 1])) {
                // See if it's prefixed by "AndroidLint"; as a special case we allow
                // that since in the IDE issues are often prefixed by both
                val prefixStart = index - STUDIO_ID_PREFIX.length
                if (index >= STUDIO_ID_PREFIX.length &&
                    line.regionMatches(
                            prefixStart, STUDIO_ID_PREFIX,
                            0, STUDIO_ID_PREFIX.length
                        ) && (prefixStart == 0 || isWordDelimiter(line[prefixStart - 1]))
                ) {
                    return true
                }
                return false
            }
            return true
        }

        private fun isWordDelimiter(c: Char): Boolean = !c.isJavaIdentifierPart()

        private fun findPrefixOnPreviousLine(
            contents: CharSequence,
            lineStart: Int,
            prefix: String
        ): Int {
            // Search backwards on the previous line until you find the prefix start (also look
            // back on previous lines if the previous line(s) contain just whitespace
            val first = prefix[0]
            var offset =
                lineStart - 2 // 0: first char on this line, -1: \n on previous line, -2 last
            var seenNonWhitespace = false
            while (offset >= 0) {
                val c = contents[offset]
                if (seenNonWhitespace && c == '\n') {
                    return -1
                }

                if (!seenNonWhitespace && !Character.isWhitespace(c)) {
                    seenNonWhitespace = true
                }

                if (c == first && CharSequences.regionMatches(
                        contents, offset, prefix, 0,
                        prefix.length
                    )
                ) {
                    return offset
                }
                offset--
            }

            return -1
        }

        private var detectorsWarned: MutableSet<String>? = null

        /** Check forbidden access and report issue if necessary. */
        fun checkForbidden(methodName: String, file: File, driver: LintDriver? = null): Boolean {
            val currentDriver = driver ?: LintDriver.currentDrivers.firstOrNull() ?: return true
            if (currentDriver.mode == DriverMode.ANALYSIS_ONLY) {
                val (detector, issues) = findCallingDetector(currentDriver) ?: return false
                val warnings = detectorsWarned ?: HashSet<String>().also { detectorsWarned = it }
                if (warnings.add(detector)) {
                    val stack = StringBuilder()
                    LintDriver.appendStackTraceSummary(
                        RuntimeException(), stack,
                        skipFrames = 1, maxFrames = 20
                    )
                    val vendors = issues.mapNotNull { it.vendor ?: it.registry?.vendor }.toSet()
                        .sortedBy { it.identifier }
                    val vendorString = if (issues.isNotEmpty()) {
                        val sb = StringBuilder()
                        sb.append("\nIssue Vendors:\n")
                        for (vendor in vendors) {
                            vendor.vendorName?.let { sb.append("Vendor: $it\n") }
                            vendor.identifier?.let { sb.append("Identifier: $it\n") }
                            vendor.contact?.let { sb.append("Contact: $it\n") }
                            vendor.feedbackUrl?.let { sb.append("Feedback: $it\n") }
                            sb.append("\n")
                        }
                        sb.toString()
                    } else {
                        ""
                    }

                    val message =
                        """
                        The lint detector
                            `$detector`
                        called `$methodName` during module analysis.

                        This does not work correctly when running in ${driver?.client?.getClientDisplayName() ?: LintClient.clientName}.

                        In particular, there may be false positives or false negatives because
                        the lint check may be using the minSdkVersion or manifest information
                        from the library instead of any consuming app module.

                        Contact the vendor of the lint issue to get it fixed/updated (if
                        known, listed below), and in the meantime you can try to work around
                        this by disabling the following issues:

                        ${issues.joinToString(separator = ",") { "\"$it\"" }}
                        """.trimIndent() + "\n" + vendorString + "Call stack: $stack"
                    LintClient.report(
                        client = currentDriver.client,
                        issue = IssueRegistry.LINT_ERROR,
                        message = message,
                        location = Location.create(file),
                        driver = currentDriver
                    )
                }

                return true
            }

            return false
        }

        private fun findCallingDetector(driver: LintDriver): Pair<String, List<Issue>>? {
            val throwable = Throwable().fillInStackTrace()
            val frames = throwable.stackTrace

            // Special allowed case:
            if (frames.size >= 4) {
                val callerCaller = frames[3]
                if (callerCaller.methodName == "beforeCheckEachProject" ||
                    callerCaller.methodName == "afterCheckEachProject"
                ) {
                    // Built in compatibility check in Detector; these ones are okay
                    return null
                }
            }

            val result = LintDriver.getAssociatedDetector(throwable, driver)
            if (result != null) {
                return result
            } else {
                // No detector named *Detector on the stack; assume that the caller to report()
                // was by detector
                for (element in frames) {
                    val detectorClass = element.className
                    if (!detectorClass.startsWith("com.android.tools.lint.") ||
                        detectorClass.startsWith("com.android.tools.lint.checks.")
                    ) {
                        return Pair(
                            detectorClass,
                            LintDriver.getDetectorIssues(detectorClass, driver)
                        )
                    }
                }
            }

            return Pair("unknown detector", emptyList())
        }
    }
}
