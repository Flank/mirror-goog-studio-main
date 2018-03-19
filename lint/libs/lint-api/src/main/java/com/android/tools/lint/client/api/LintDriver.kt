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

package com.android.tools.lint.client.api

import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.CLASS_CONSTRUCTOR
import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.FQCN_SUPPRESS_LINT
import com.android.SdkConstants.KOTLIN_SUPPRESS
import com.android.SdkConstants.RES_FOLDER
import com.android.SdkConstants.SUPPRESS_ALL
import com.android.SdkConstants.SUPPRESS_LINT
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_TRUE
import com.android.annotations.VisibleForTesting
import com.android.ide.common.repository.ResourceVisibilityLookup
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration.QUALIFIER_SPLITTER
import com.android.repository.api.ProgressIndicator
import com.android.resources.ResourceFolderType
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.lint.client.api.LintListener.EventType
import com.android.tools.lint.detector.api.BinaryResourceScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintUtils
import com.android.tools.lint.detector.api.LintUtils.isAnonymousClass
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceFolderScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.Pair
import com.android.utils.SdkUtils.isBitmapFile
import com.google.common.annotations.Beta
import com.google.common.base.Objects
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerExpression
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.Contract
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Arrays
import java.util.Deque
import java.util.EnumMap
import java.util.EnumSet
import java.util.HashMap
import java.util.HashSet
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.regex.Pattern

/**
 * Analyzes Android projects and files
 *
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
class LintDriver
/**
 * Creates a new [LintDriver]
 *
 * @param registry The registry containing issues to be checked
 *
 * @param request The request which points to the original files to be checked,
 * the original scope, the original [LintClient], as well as the release mode.
 *
 * @param client the tool wrapping the analyzer, such as an IDE or a CLI
 */
    (var registry: IssueRegistry, client: LintClient, val request: LintRequest) {
    /** True if execution has been canceled  */
    @Volatile
    internal var isCanceled: Boolean = false
        private set

    /** The original client (not the wrapped one intended to pass to detectors */
    private val realClient: LintClient = client

    /**
     * Stashed circular project (we need to report this but can't report it
     * at the early stage during initialization where this is detected). Cleared
     * once reported.
     */
    private var circularProjectError: CircularDependencyException? = null

    /** The associated [LintClient] */
    val client: LintClient = LintClientWrapper(client)

    private val projectRoots: Collection<Project>

    init {
        projectRoots =
                try {
                    request.getProjects() ?: computeProjects(request.files)
                } catch (e: CircularDependencyException) {
                    circularProjectError = e
                    emptyList()
                }
    }

    /**
     * The scope for the lint job
     */
    var scope: EnumSet<Scope> = request.getScope() ?: Scope.infer(projectRoots)

    private lateinit var applicableDetectors: List<Detector>
    private lateinit var scopeDetectors: Map<Scope, MutableList<Detector>>
    private var listeners: MutableList<LintListener>? = null

    /**
     * Returns the current phase number. The first pass is numbered 1. Only one pass
     * will be performed, unless a [Detector] calls [.requestRepeat].
     *
     * @return the current phase, usually 1
     */
    var phase: Int = 0
        private set

    private var repeatingDetectors: MutableList<Detector>? = null
    private var repeatScope: EnumSet<Scope>? = null
    private var currentProjects: Array<Project>? = null
    private var currentProject: Project? = null

    /**
     * Whether lint should abbreviate output when appropriate.
     */
    var isAbbreviating = true

    private var parserErrors: Boolean = false
    /** Whether we should run all normal checks on test sources  */
    var isCheckTestSources: Boolean = false
    /** Whether we should include generated sources in the analysis  */
    var isCheckGeneratedSources: Boolean = false
    /** Whether we're only analyzing fatal-severity issues  */
    var isFatalOnlyMode: Boolean = false
    /** Baseline to apply to the analysis */
    var baseline: LintBaseline? = null
    /** Whether dependent projects should be checked */
    var checkDependencies = true

    /** Cancels the current lint run as soon as possible  */
    fun cancel() {
        isCanceled = true
    }

    /**
     * Returns the project containing a given file, or null if not found. This searches
     * only among the currently checked project and its library projects, not among all
     * possible projects being scanned sequentially.
     *
     * @param file the file to be checked
     *
     * @return the corresponding project, or null if not found
     */
    fun findProjectFor(file: File): Project? {
        val projects = currentProjects ?: return null
        if (projects.size == 1) {
            return projects[0]
        }
        val path = file.path
        for (project in projects) {
            if (path.startsWith(project.dir.path)) {
                return project
            }
        }

        return null
    }

    /**
     * Returns whether lint has encountered any files with fatal parser errors
     * (e.g. broken source code, or even broken parsers)
     *
     * This is useful for checks that need to make sure they've seen all data in
     * order to be conclusive (such as an unused resource check).
     *
     * @return true if any files were not properly processed because they
     *         contained parser errors
     */
    fun hasParserErrors(): Boolean = parserErrors

    /**
     * Sets whether lint has encountered files with fatal parser errors.
     *
     * @see .hasParserErrors
     * @param hasErrors whether parser errors have been encountered
     */
    fun setHasParserErrors(hasErrors: Boolean) {
        parserErrors = hasErrors
    }

    /**
     * Returns the projects being analyzed
     *
     * @return the projects being analyzed
     */
    val projects: List<Project>
        get() {
            val p = currentProjects ?: return emptyList()
            return Arrays.asList(*p)
        }

    /**
     * Analyze the given files (which can point to Android projects or directories
     * containing Android projects). Issues found are reported to the associated
     * [LintClient].
     *
     *
     * Note that the [LintDriver] is not multi thread safe or re-entrant;
     * if you want to run potentially overlapping lint jobs, create a separate driver
     * for each job.
     */
    fun analyze() {
        isCanceled = false
        assert(!scope.contains(Scope.ALL_RESOURCE_FILES) || scope.contains(Scope.RESOURCE_FILE))

        circularProjectError?.let {
            val project = it.project
            if (project != null) {
                currentProject = project
                LintClient.report(
                    client = client, issue = IssueRegistry.LINT_ERROR,
                    message = it.message ?: "Circular project dependencies",
                    project = project, driver = this, location = it.location
                )
                currentProject = null
            }
            circularProjectError = null
            return
        }

        val projects = projectRoots
        if (projects.isEmpty()) {
            client.log(null, "No projects found for %1\$s", request.files.toString())
            return
        }
        realClient.performInitializeProjects(projects)

        if (isCanceled) {
            realClient.performDisposeProjects(projects)
            return
        }

        for (project in projects) {
            fireEvent(EventType.REGISTERED_PROJECT, project = project)
        }

        registerCustomDetectors(projects)

        // See if the lint.xml file specifies a baseline and we're not in incremental mode
        if (baseline == null && scope.size > 2) {
            val lastProject = Iterables.getLast(projects)
            val mainConfiguration = client.getConfiguration(lastProject, this)
            val baselineFile = mainConfiguration.baselineFile
            if (baselineFile != null) {
                baseline = LintBaseline(client, baselineFile)
            }
        }

        fireEvent(EventType.STARTING, null)

        try {
            for (project in projects) {
                phase = 1

                val main = request.getMainProject(project)

                // The set of available detectors varies between projects
                computeDetectors(project)

                if (applicableDetectors.isEmpty()) {
                    // No detectors enabled in this project: skip it
                    continue
                }

                checkProject(project, main)
                if (isCanceled) {
                    break
                }

                runExtraPhases(project, main)
            }
        } catch (throwable: Throwable) {
            // Process canceled etc
            if (!handleDetectorError(null, this, throwable)) {
                cancel()
            }
        }

        val baseline = this.baseline
        if (baseline != null && !isCanceled) {
            val lastProject = Iterables.getLast(projects)
            val main = request.getMainProject(lastProject)
            baseline.reportBaselineIssues(this, main)
        }

        fireEvent(if (isCanceled) EventType.CANCELED else EventType.COMPLETED, null)
        realClient.performDisposeProjects(projects)
    }

    private fun registerCustomDetectors(projects: Collection<Project>) {
        // Look at the various projects, and if any of them provide a custom
        // lint jar, "add" them (this will replace the issue registry with
        // a CompositeIssueRegistry containing the original issue registry
        // plus JarFileIssueRegistry instances for each lint jar
        val jarFiles = Sets.newHashSet<File>()
        for (project in projects) {
            jarFiles.addAll(client.findRuleJars(project))
            for (library in project.allLibraries) {
                jarFiles.addAll(client.findRuleJars(library))
            }
        }

        jarFiles.addAll(client.findGlobalRuleJars())

        if (!jarFiles.isEmpty()) {
            val extraRegistries = JarFileIssueRegistry.get(
                client, jarFiles,
                currentProject ?: projects.firstOrNull()
            )
            if (extraRegistries.isNotEmpty()) {
                val registries = ArrayList<IssueRegistry>(jarFiles.size + 1)
                // Include the builtin checks too
                registries.add(registry)
                for (extraRegistry in extraRegistries) {
                    registries.add(extraRegistry)
                }
                registry = CompositeIssueRegistry(registries)
            }
        }
    }

    private fun runExtraPhases(project: Project, main: Project) {
        // Did any detectors request another phase?
        repeatingDetectors ?: return

        // Yes. Iterate up to MAX_PHASES times.

        // During the extra phases, we might be narrowing the scope, and setting it in the
        // scope field such that detectors asking about the available scope will get the
        // correct result. However, we need to restore it to the original scope when this
        // is done in case there are other projects that will be checked after this, since
        // the repeated phases is done *per project*, not after all projects have been
        // processed.
        val oldScope = scope

        do {
            phase++
            fireEvent(
                EventType.NEW_PHASE,
                Context(this, project, null, project.dir)
            )

            // Narrow the scope down to the set of scopes requested by
            // the rules.
            if (repeatScope == null) {
                repeatScope = Scope.ALL
            }
            scope = Scope.intersect(scope, repeatScope!!)
            if (scope.isEmpty()) {
                break
            }

            // Compute the detectors to use for this pass.
            // Unlike the normal computeDetectors(project) call,
            // this is going to use the existing instances, and include
            // those that apply for the configuration.
            repeatingDetectors?.let { computeRepeatingDetectors(it, project) }

            if (applicableDetectors.isEmpty()) {
                // No detectors enabled in this project: skip it
                continue
            }

            checkProject(project, main)
            if (isCanceled) {
                break
            }
        } while (phase < MAX_PHASES && repeatingDetectors != null)

        scope = oldScope
    }

    private fun computeRepeatingDetectors(detectors: List<Detector>, project: Project) {
        // Ensure that the current visitor is recomputed
        currentFolderType = null
        currentVisitor = null
        currentXmlDetectors = null
        currentBinaryDetectors = null

        // Create map from detector class to issue such that we can
        // compute applicable issues for each detector in the list of detectors
        // to be repeated
        val issues = registry.issues
        val issueMap = ArrayListMultimap.create<Class<out Detector>, Issue>(issues.size, 3)
        for (issue in issues) {
            issueMap.put(issue.implementation.detectorClass, issue)
        }

        val detectorToScope = HashMap<Class<out Detector>, EnumSet<Scope>>()
        val scopeToDetectors: MutableMap<Scope, MutableList<Detector>> =
            EnumMap<Scope, MutableList<Detector>>(Scope::class.java)

        val detectorList = ArrayList<Detector>()
        // Compute the list of detectors (narrowed down from repeatingDetectors),
        // and simultaneously build up the detectorToScope map which tracks
        // the scopes each detector is affected by (this is used to populate
        // the scopeDetectors map which is used during iteration).
        val configuration = project.getConfiguration(this)
        for (detector in detectors) {
            val detectorClass = detector.javaClass
            val detectorIssues = issueMap.get(detectorClass)
            if (detectorIssues != null) {
                var add = false
                for (issue in detectorIssues) {
                    // The reason we have to check whether the detector is enabled
                    // is that this is a per-project property, so when running lint in multiple
                    // projects, a detector enabled only in a different project could have
                    // requested another phase, and we end up in this project checking whether
                    // the detector is enabled here.
                    if (!configuration.isEnabled(issue)) {
                        continue
                    }

                    add = true // Include detector if any of its issues are enabled

                    val s = detectorToScope[detectorClass]
                    val issueScope = issue.implementation.scope
                    if (s == null) {
                        detectorToScope[detectorClass] = issueScope
                    } else if (!s.containsAll(issueScope)) {
                        val union = EnumSet.copyOf(s)
                        union.addAll(issueScope)
                        detectorToScope[detectorClass] = union
                    }
                }

                if (add) {
                    detectorList.add(detector)
                    val union = detectorToScope[detector.javaClass]
                    if (union != null) {
                        for (s in union) {
                            var list: MutableList<Detector>? = scopeToDetectors[s]
                            if (list == null) {
                                list = ArrayList()
                                scopeToDetectors[s] = list
                            }
                            list.add(detector)
                        }
                    }
                }
            }
        }

        applicableDetectors = detectorList
        scopeDetectors = scopeToDetectors
        repeatingDetectors = null
        repeatScope = null

        validateScopeList()
    }

    private fun computeDetectors(project: Project) {
        // Ensure that the current visitor is recomputed
        currentFolderType = null
        currentVisitor = null

        val configuration = project.getConfiguration(this)
        val map = EnumMap<Scope, MutableList<Detector>>(Scope::class.java)
        scopeDetectors = map
        applicableDetectors = registry.createDetectors(client, configuration, scope, map)

        validateScopeList()
    }

    /** Development diagnostics only, run with assertions on  */
    private // Turn off warnings for the intentional assertion side effect below
    fun validateScopeList() {
        if (LintUtils.assertionsEnabled()) {
            val resourceFileDetectors = scopeDetectors[Scope.RESOURCE_FILE]
            if (resourceFileDetectors != null) {
                for (detector in resourceFileDetectors) {
                    assert(detector is XmlScanner) { detector }
                }
            }

            val manifestDetectors = scopeDetectors[Scope.MANIFEST]
            if (manifestDetectors != null) {
                for (detector in manifestDetectors) {
                    assert(detector is XmlScanner) { detector }
                }
            }

            val javaCodeDetectors = scopeDetectors[Scope.ALL_JAVA_FILES]
            if (javaCodeDetectors != null) {
                for (detector in javaCodeDetectors) {
                    assert(detector is SourceCodeScanner) { detector }
                }
            }

            val javaFileDetectors = scopeDetectors[Scope.JAVA_FILE]
            if (javaFileDetectors != null) {
                for (detector in javaFileDetectors) {
                    assert(detector is SourceCodeScanner) { detector }
                }
            }

            val classDetectors = scopeDetectors[Scope.CLASS_FILE]
            if (classDetectors != null) {
                for (detector in classDetectors) {
                    assert(detector is ClassScanner) { detector }
                }
            }

            val classCodeDetectors = scopeDetectors[Scope.ALL_CLASS_FILES]
            if (classCodeDetectors != null) {
                for (detector in classCodeDetectors) {
                    assert(detector is ClassScanner) { detector }
                }
            }

            val gradleDetectors = scopeDetectors[Scope.GRADLE_FILE]
            if (gradleDetectors != null) {
                for (detector in gradleDetectors) {
                    assert(detector is GradleScanner) { detector }
                }
            }

            val otherDetectors = scopeDetectors[Scope.OTHER]
            if (otherDetectors != null) {
                for (detector in otherDetectors) {
                    assert(detector is OtherFileScanner) { detector }
                }
            }

            val dirDetectors = scopeDetectors[Scope.RESOURCE_FOLDER]
            if (dirDetectors != null) {
                for (detector in dirDetectors) {
                    assert(detector is ResourceFolderScanner) { detector }
                }
            }

            val binaryDetectors = scopeDetectors[Scope.BINARY_RESOURCE_FILE]
            if (binaryDetectors != null) {
                for (detector in binaryDetectors) {
                    assert(detector is BinaryResourceScanner) { detector }
                }
            }
        }
    }

    private fun registerProjectFile(
        fileToProject: MutableMap<File, Project>,
        file: File,
        projectDir: File,
        rootDir: File
    ) {
        fileToProject[file] = client.getProject(projectDir, rootDir)
    }

    private fun computeProjects(relativeFiles: List<File>): Collection<Project> {
        // Compute list of projects
        val fileToProject = LinkedHashMap<File, Project>()

        var sharedRoot: File? = null

        // Ensure that we have absolute paths such that if you lint
        //  "foo bar" in "baz" we can show baz/ as the root
        val absolute = ArrayList<File>(relativeFiles.size)
        for (file in relativeFiles) {
            absolute.add(file.absoluteFile)
        }
        // Always use absoluteFiles so that we can check the file's getParentFile()
        // which is null if the file is not absolute.
        @Suppress("UnnecessaryVariable")
        val files = absolute

        if (files.size > 1) {
            sharedRoot = LintUtils.getCommonParent(files)
            if (sharedRoot != null && sharedRoot.parentFile == null) { // "/" ?
                sharedRoot = null
            }
        }

        for (file in files) {
            if (file.isDirectory) {
                var rootDir = sharedRoot
                if (rootDir == null) {
                    rootDir = file
                    if (files.size > 1) {
                        rootDir = file.parentFile
                        if (rootDir == null) {
                            rootDir = file
                        }
                    }
                }

                // Figure out what to do with a directory. Note that the meaning of the
                // directory can be ambiguous:
                // If you pass a directory which is unknown, we don't know if we should
                // search upwards (in case you're pointing at a deep java package folder
                // within the project), or if you're pointing at some top level directory
                // containing lots of projects you want to scan. We attempt to do the
                // right thing, which is to see if you're pointing right at a project or
                // right within it (say at the src/ or res/) folder, and if not, you're
                // hopefully pointing at a project tree that you want to scan recursively.
                if (client.isProjectDirectory(file)) {
                    registerProjectFile(fileToProject, file, file, rootDir)
                    continue
                } else {
                    var parent: File? = file.parentFile
                    if (parent != null) {
                        if (client.isProjectDirectory(parent)) {
                            registerProjectFile(fileToProject, file, parent, parent)
                            continue
                        } else {
                            parent = parent.parentFile
                            if (parent != null && client.isProjectDirectory(parent)) {
                                registerProjectFile(fileToProject, file, parent, parent)
                                continue
                            }
                        }
                    }

                    // Search downwards for nested projects
                    addProjects(file, fileToProject, rootDir)
                }
            } else {
                // Pointed at a file: Search upwards for the containing project
                var parent: File? = file.parentFile
                while (parent != null) {
                    if (client.isProjectDirectory(parent)) {
                        registerProjectFile(fileToProject, file, parent, parent)
                        break
                    }
                    parent = parent.parentFile
                }
            }

            if (isCanceled) {
                return emptySet()
            }
        }

        for ((file, project) in fileToProject) {
            if (file != project.dir) {
                if (file.isDirectory) {
                    try {
                        val dir = file.canonicalFile
                        if (dir == project.dir) {
                            continue
                        }
                    } catch (ioe: IOException) {
                        // pass
                    }
                }

                project.addFile(file)
            }
        }

        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)

        val allProjects = fileToProject.values
        val roots = HashSet(allProjects)
        for (project in allProjects) {
            roots.removeAll(project.allLibraries)
        }

        // Report issues for all projects that are explicitly referenced. We need to
        // do this here, since the project initialization will mark all library
        // projects as no-report projects by default.
        for (project in allProjects) {
            // Report issues for all projects explicitly listed or found via a directory
            // traversal -- including library projects.
            project.reportIssues = true
        }

        if (LintUtils.assertionsEnabled()) {
            // Make sure that all the project directories are unique. This ensures
            // that we didn't accidentally end up with different project instances
            // for a library project discovered as a directory as well as one
            // initialized from the library project dependency list
            val projects = IdentityHashMap<Project, Project>()
            for (project in roots) {
                projects[project] = project
                for (library in project.allLibraries) {
                    projects[library] = library
                }
            }
            val dirs = HashSet<File>()
            for (project in projects.keys) {
                assert(!dirs.contains(project.dir))
                dirs.add(project.dir)
            }
        }

        return roots
    }

    private fun addProjects(
        dir: File,
        fileToProject: MutableMap<File, Project>,
        rootDir: File
    ) {
        if (isCanceled) {
            return
        }

        if (client.isProjectDirectory(dir)) {
            registerProjectFile(fileToProject, dir, dir, rootDir)
        } else {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        addProjects(file, fileToProject, rootDir)
                    }
                }
            }
        }
    }

    private fun checkProject(project: Project, main: Project) {
        val projectDir = project.dir

        val projectContext = Context(this, project, null, projectDir)
        fireEvent(EventType.SCANNING_PROJECT, projectContext)

        val allLibraries = project.allLibraries
        val allProjects = HashSet<Project>(allLibraries.size + 1)
        allProjects.add(project)
        allProjects.addAll(allLibraries)
        currentProjects = allProjects.toTypedArray()

        currentProject = project

        for (check in applicableDetectors) {
            check.beforeCheckProject(projectContext)
            if (isCanceled) {
                return
            }
        }

        assert(currentProject === project)
        runFileDetectors(project, main)

        if (checkDependencies && !Scope.checkSingleFile(scope)) {
            val libraries = project.allLibraries
            for (library in libraries) {
                val libraryContext = Context(this, library, project, projectDir)
                fireEvent(EventType.SCANNING_LIBRARY_PROJECT, libraryContext)
                currentProject = library

                for (check in applicableDetectors) {
                    check.beforeCheckLibraryProject(libraryContext)
                    if (isCanceled) {
                        return
                    }
                }
                assert(currentProject === library)

                runFileDetectors(library, main)
                if (isCanceled) {
                    return
                }

                assert(currentProject === library)

                for (check in applicableDetectors) {
                    check.afterCheckLibraryProject(libraryContext)
                    if (isCanceled) {
                        return
                    }
                }
            }
        }

        currentProject = project

        for (check in applicableDetectors) {
            client.runReadAction(Runnable { check.afterCheckProject(projectContext) })
            if (isCanceled) {
                return
            }
        }

        if (isCanceled) {
            client.report(
                projectContext,
                // Must provide an issue since API guarantees that the issue parameter
                IssueRegistry.CANCELLED,
                Severity.INFORMATIONAL,
                Location.create(project.dir),
                "Lint canceled by user", TextFormat.RAW, null
            )
        }

        currentProjects = null
    }

    private fun runFileDetectors(project: Project, main: Project?) {
        // Look up manifest information (but not for library projects)
        if (project.isAndroidProject) {
            for (manifestFile in project.manifestFiles) {
                val parser = client.xmlParser
                val context = createXmlContext(project, main, manifestFile, null, parser)
                if (context != null) {
                    try {
                        project.readManifest(context.document)
                        if ((!project.isLibrary || main != null &&
                                    main.isMergingManifests) && scope.contains(Scope.MANIFEST)
                        ) {
                            val detectors = scopeDetectors[Scope.MANIFEST]
                            if (detectors != null) {
                                val xmlDetectors = ArrayList<XmlScanner>(detectors.size)
                                for (detector in detectors) {
                                    if (detector is XmlScanner) {
                                        xmlDetectors.add(detector)
                                    }
                                }

                                val v = ResourceVisitor(parser, xmlDetectors, null)
                                fireEvent(EventType.SCANNING_FILE, context)
                                v.visitFile(context)
                            }
                        }
                    } finally {
                        disposeXmlContext(context)
                    }
                }
            }

            // Process both Scope.RESOURCE_FILE and Scope.ALL_RESOURCE_FILES detectors together
            // in a single pass through the resource directories.
            if (scope.contains(Scope.ALL_RESOURCE_FILES)
                || scope.contains(Scope.RESOURCE_FILE)
                || scope.contains(Scope.RESOURCE_FOLDER)
                || scope.contains(Scope.BINARY_RESOURCE_FILE)
            ) {
                val dirChecks = scopeDetectors[Scope.RESOURCE_FOLDER]
                val binaryChecks = scopeDetectors[Scope.BINARY_RESOURCE_FILE]
                val checks = union(
                    scopeDetectors[Scope.RESOURCE_FILE],
                    scopeDetectors[Scope.ALL_RESOURCE_FILES]
                ) ?: emptyList()
                var haveXmlChecks = !checks.isEmpty()
                val xmlDetectors: MutableList<XmlScanner>
                if (haveXmlChecks) {
                    xmlDetectors = ArrayList(checks.size)
                    for (detector in checks) {
                        if (detector is XmlScanner) {
                            xmlDetectors.add(detector)
                        }
                    }
                    haveXmlChecks = !xmlDetectors.isEmpty()
                } else {
                    xmlDetectors = mutableListOf()
                }
                if (haveXmlChecks
                    || dirChecks != null && !dirChecks.isEmpty()
                    || binaryChecks != null && !binaryChecks.isEmpty()
                ) {
                    val files = project.subset
                    if (files != null) {
                        checkIndividualResources(
                            project, main, xmlDetectors, dirChecks,
                            binaryChecks, files
                        )
                    } else {
                        val resourceFolders = project.resourceFolders
                        if (!resourceFolders.isEmpty()) {
                            for (res in resourceFolders) {
                                checkResFolder(
                                    project, main, res, xmlDetectors, dirChecks,
                                    binaryChecks
                                )
                            }
                        }
                        if (isCheckGeneratedSources) {
                            val generatedResourceFolders = project.generatedResourceFolders
                            if (!generatedResourceFolders.isEmpty()) {
                                for (res in generatedResourceFolders) {
                                    checkResFolder(
                                        project, main, res, xmlDetectors, dirChecks,
                                        binaryChecks
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isCanceled) {
                return
            }
        }

        if (scope.contains(Scope.JAVA_FILE) || scope.contains(Scope.ALL_JAVA_FILES)) {
            val checks = union(
                scopeDetectors[Scope.JAVA_FILE],
                scopeDetectors[Scope.ALL_JAVA_FILES]
            )
            if (checks != null && !checks.isEmpty()) {
                val files = project.subset
                if (files != null) {
                    checkIndividualJavaFiles(project, main, checks, files)
                } else {
                    val sourceFolders = project.javaSourceFolders
                    val testFolders = if (scope.contains(Scope.TEST_SOURCES))
                        project.testSourceFolders
                    else emptyList<File>()

                    val generatedFolders = if (isCheckGeneratedSources)
                        project.generatedSourceFolders
                    else emptyList<File>()
                    checkJava(project, main, sourceFolders, testFolders, generatedFolders, checks)
                }
            }
        }

        if (isCanceled) {
            return
        }

        if (scope.contains(Scope.CLASS_FILE)
            || scope.contains(Scope.ALL_CLASS_FILES)
            || scope.contains(Scope.JAVA_LIBRARIES)
        ) {
            checkClasses(project, main)
        }

        if (isCanceled) {
            return
        }

        if (scope.contains(Scope.GRADLE_FILE)) {
            checkBuildScripts(project, main)
        }

        if (isCanceled) {
            return
        }

        if (scope.contains(Scope.OTHER)) {
            val checks = scopeDetectors[Scope.OTHER]
            if (checks != null) {
                val visitor = OtherFileVisitor(checks)
                visitor.scan(this, project, main)
            }
        }

        if (isCanceled) {
            return
        }

        if (project === main && scope.contains(Scope.PROGUARD_FILE) &&
            project.isAndroidProject
        ) {
            checkProGuard(project, main)
        }

        if (project === main && scope.contains(Scope.PROPERTY_FILE)) {
            checkProperties(project, main)
        }
    }

    private fun checkBuildScripts(project: Project, main: Project?) {
        val detectors = scopeDetectors[Scope.GRADLE_FILE]
        if (detectors != null) {
            val files = project.subset ?: project.gradleBuildScripts
            for (file in files) {
                // Gradle Kotlin Script? Use Java parsing mechanism instead
                if (file.path.endsWith(DOT_KTS)) {
                    val context = JavaContext(this, project, main, file)
                    val uastParser = client.getUastParser(currentProject)
                    context.uastParser = uastParser

                    uastParser.prepare(listOf(context), emptyList())
                    client.runReadAction(Runnable {
                        val uFile = uastParser.parse(context)
                        if (uFile != null) {
                            context.setJavaFile(uFile.psi) // needed for getLocation
                            context.uastFile = uFile
                            fireEvent(EventType.SCANNING_FILE, context)
                            for (detector in detectors) {
                                detector.beforeCheckFile(context)
                                detector.visitBuildScript(context)
                                detector.afterCheckFile(context)
                            }
                            context.setJavaFile(null)
                            context.uastFile = null
                        }
                    })
                    uastParser.dispose()
                } else {
                    val context = Context(this, project, main, file)
                    fireEvent(EventType.SCANNING_FILE, context)
                    for (detector in detectors) {
                        detector.beforeCheckFile(context)
                        detector.visitBuildScript(context)
                        detector.afterCheckFile(context)
                    }
                }
            }
        }
    }

    private fun checkProGuard(project: Project, main: Project) {
        val detectors = scopeDetectors[Scope.PROGUARD_FILE]
        if (detectors != null) {
            val files = project.proguardFiles
            for (file in files) {
                val context = Context(this, project, main, file)
                fireEvent(EventType.SCANNING_FILE, context)
                for (detector in detectors) {
                    detector.beforeCheckFile(context)
                    detector.run(context)
                    detector.afterCheckFile(context)
                }
            }
        }
    }

    private fun checkProperties(project: Project, main: Project) {
        val detectors = scopeDetectors[Scope.PROPERTY_FILE]
        if (detectors != null) {
            for (file in project.propertyFiles) {
                val context = Context(this, project, main, file)
                fireEvent(EventType.SCANNING_FILE, context)
                for (detector in detectors) {
                    detector.beforeCheckFile(context)
                    detector.run(context)
                    detector.afterCheckFile(context)
                }
            }
        }
    }

    /**
     * Returns the super class for the given class name,
     * which should be in VM format (e.g. java/lang/Integer, not java.lang.Integer).
     * If the super class is not known, returns null. This can happen if
     * the given class is not a known class according to the project or its
     * libraries, for example because it refers to one of the core libraries which
     * are not analyzed by lint.
     *
     * @param name the fully qualified class name
     *
     * @return the corresponding super class name (in VM format), or null if not known
     */
    fun getSuperClass(name: String): String? = client.getSuperClass(currentProject!!, name)

    /**
     * Returns true if the given class is a subclass of the given super class.
     *
     * @param classNode the class to check whether it is a subclass of the given
     *            super class name
     *
     * @param superClassName the fully qualified super class name (in VM format,
     *            e.g. java/lang/Integer, not java.lang.Integer.
     *
     * @return true if the given class is a subclass of the given super class
     */
    fun isSubclassOf(classNode: ClassNode, superClassName: String): Boolean {
        if (superClassName == classNode.superName) {
            return true
        }

        if (currentProject != null) {
            val isSub = client.isSubclassOf(currentProject!!, classNode.name, superClassName)
            if (isSub != null) {
                return isSub
            }
        }

        var className: String? = classNode.name
        while (className != null) {
            if (className == superClassName) {
                return true
            }
            className = getSuperClass(className)
        }

        return false
    }

    /** Check the classes in this project (and if applicable, in any library projects  */
    private fun checkClasses(project: Project, main: Project?) {
        val files = project.subset
        if (files != null) {
            checkIndividualClassFiles(project, main, files)
            return
        }

        // We need to read in all the classes up front such that we can initialize
        // the parent chains (such that for example for a virtual dispatch, we can
        // also check the super classes).

        val libraries = project.getJavaLibraries(false)
        val libraryEntries = ClassEntry.fromClassPath(client, libraries, true)

        val classFolders = project.javaClassFolders
        val classEntries: List<ClassEntry>
        classEntries = if (classFolders.isEmpty()) {
            val message = String.format(
                "No `.class` files were found in project \"%1\$s\", " +
                        "so none of the classfile based checks could be run. " +
                        "Does the project need to be built first?", project.name
            )
            LintClient.report(
                client = client, issue = IssueRegistry.LINT_ERROR, message = message,
                project = project, mainProject = main, driver = this
            )
            emptyList()
        } else {
            ClassEntry.fromClassPath(client, classFolders, true)
        }

        // Actually run the detectors. Libraries should be called before the
        // main classes.
        runClassDetectors(Scope.JAVA_LIBRARIES, libraryEntries, project, main)

        if (isCanceled) {
            return
        }

        runClassDetectors(Scope.CLASS_FILE, classEntries, project, main)
        runClassDetectors(Scope.ALL_CLASS_FILES, classEntries, project, main)
    }

    private fun checkIndividualClassFiles(
        project: Project,
        main: Project?,
        files: List<File>
    ) {
        val classFiles = ArrayList<File>(files.size)
        val classFolders = project.javaClassFolders
        if (!classFolders.isEmpty()) {
            for (file in files) {
                val path = file.path
                if (file.isFile && path.endsWith(DOT_CLASS)) {
                    classFiles.add(file)
                }
            }
        }

        val entries = ClassEntry.fromClassFiles(
            client, classFiles, classFolders,
            true
        )
        if (!entries.isEmpty()) {
            entries.sort()
            runClassDetectors(Scope.CLASS_FILE, entries, project, main)
        }
    }

    /**
     * Stack of [ClassNode] nodes for outer classes of the currently
     * processed class, including that class itself. Populated by
     * [.runClassDetectors] and used by
     * [.getOuterClassNode]
     */
    private var outerClasses: Deque<ClassNode>? = null

    private fun runClassDetectors(
        scope: Scope,
        entries: List<ClassEntry>,
        project: Project,
        main: Project?
    ) {
        if (this.scope.contains(scope)) {
            val classDetectors = scopeDetectors[scope]
            if (classDetectors != null && !classDetectors.isEmpty() && !entries.isEmpty()) {
                val visitor = AsmVisitor(client, classDetectors)

                var sourceContents: CharSequence? = null
                var sourceName = ""
                outerClasses = ArrayDeque<ClassNode>()
                var prev: ClassEntry? = null
                for (entry in entries) {
                    if (prev != null && prev.compareTo(entry) == 0) {
                        // Duplicate entries for some reason: ignore
                        continue
                    }
                    prev = entry

                    val reader: ClassReader
                    val classNode: ClassNode
                    try {
                        reader = ClassReader(entry.bytes)
                        classNode = ClassNode()
                        reader.accept(classNode, 0 /* flags */)
                    } catch (t: Throwable) {
                        client.log(
                            null,
                            "Error processing ${entry.path()}: broken class file? (${t.message})"
                        )
                        continue
                    }

                    var peek: ClassNode?
                    while (true) {
                        peek = outerClasses?.peek()
                        if (peek == null) {
                            break
                        }
                        if (classNode.name.startsWith(peek.name)) {
                            break
                        } else {
                            outerClasses?.pop()
                        }
                    }
                    outerClasses?.push(classNode)

                    if (isSuppressed(null, classNode)) {
                        // Class was annotated with suppress all -- no need to look any further
                        continue
                    }

                    if (sourceContents != null) {
                        // Attempt to reuse the source buffer if initialized
                        // This means making sure that the source files
                        //    foo/bar/MyClass and foo/bar/MyClass$Bar
                        //    and foo/bar/MyClass$3 and foo/bar/MyClass$3$1 have the same prefix.
                        val newName = classNode.name
                        var newRootLength = newName.indexOf('$')
                        if (newRootLength == -1) {
                            newRootLength = newName.length
                        }
                        var oldRootLength = sourceName.indexOf('$')
                        if (oldRootLength == -1) {
                            oldRootLength = sourceName.length
                        }
                        if (newRootLength != oldRootLength || !sourceName.regionMatches(
                                0,
                                newName,
                                0,
                                newRootLength
                            )
                        ) {
                            sourceContents = null
                        }
                    }

                    val context = ClassContext(
                        this, project, main,
                        entry.file, entry.jarFile, entry.binDir, entry.bytes,
                        classNode, scope == Scope.JAVA_LIBRARIES /*fromLibrary*/,
                        sourceContents
                    )

                    try {
                        visitor.runClassDetectors(context)
                    } catch (throwable: Throwable) {
                        // Process canceled etc
                        if (!handleDetectorError(context, this, throwable)) {
                            cancel()
                        }
                    }

                    if (isCanceled) {
                        return
                    }

                    sourceContents = context.getSourceContents(false/*read*/)
                    sourceName = classNode.name
                }

                outerClasses = null
            }
        }
    }

    /** Returns the outer class node of the given class node
     * @param classNode the inner class node
     *
     * @return the outer class node
     */
    fun getOuterClassNode(classNode: ClassNode): ClassNode? {
        val outerName = classNode.outerClass

        val iterator = outerClasses?.iterator() ?: return null
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (outerName != null) {
                if (node.name == outerName) {
                    return node
                }
            } else if (node === classNode) {
                return if (iterator.hasNext()) iterator.next() else null
            }
        }

        return null
    }

    /**
     * Returns the [ClassNode] corresponding to the given type, if possible, or null
     *
     * @param type the fully qualified type, using JVM signatures (/ and $, not . as path
     *             separators)
     *
     * @param flags the ASM flags to pass to the [ClassReader], normally 0 but can
     *              for example be [ClassReader.SKIP_CODE] and/oor
     *              [ClassReader.SKIP_DEBUG]
     *
     * @return the class node for the type, or null
     */
    fun findClass(context: ClassContext, type: String, flags: Int): ClassNode? {
        val relative = type.replace('/', File.separatorChar) + DOT_CLASS
        val classFile = findClassFile(context.project, relative)
        if (classFile != null) {
            if (classFile.path.endsWith(DOT_JAR)) {
                // TODO: Handle .jar files
                return null
            }

            try {
                val bytes = client.readBytes(classFile)
                val reader = ClassReader(bytes)
                val classNode = ClassNode()
                reader.accept(classNode, flags)

                return classNode
            } catch (t: Throwable) {
                client.log(
                    null,
                    "Error processing ${classFile.path}: broken class file? (${t.message})"
                )
            }
        }

        return null
    }

    private fun findClassFile(project: Project, relativePath: String): File? {
        for (root in client.getJavaClassFolders(project)) {
            val path = File(root, relativePath)
            if (path.exists()) {
                return path
            }
        }
        // Search in the libraries
        for (root in client.getJavaLibraries(project, true)) {
            // TODO: Handle .jar files!
            val path = File(root, relativePath)
            if (path.exists()) {
                return path
            }
        }

        // Search dependent projects
        for (library in project.directLibraries) {
            val path = findClassFile(library, relativePath)
            if (path != null) {
                return path
            }
        }

        return null
    }

    private fun checkJava(
        project: Project,
        main: Project?,
        sourceFolders: List<File>,
        testSourceFolders: List<File>,
        generatedSources: List<File>,
        checks: List<Detector>
    ) {
        assert(!checks.isEmpty())

        // Gather all Java source files in a single pass; more efficient.
        val sources = ArrayList<File>(100)
        for (folder in sourceFolders) {
            gatherJavaFiles(folder, sources)
        }
        for (folder in generatedSources) {
            gatherJavaFiles(folder, sources)
        }

        val contexts = ArrayList<JavaContext>(2 * sources.size)
        for (file in sources) {
            val context = JavaContext(this, project, main, file)
            contexts.add(context)
        }

        // Test sources
        sources.clear()
        for (folder in testSourceFolders) {
            gatherJavaFiles(folder, sources)
        }
        val testContexts = ArrayList<JavaContext>(sources.size)
        for (file in sources) {
            val context = JavaContext(this, project, main, file)
            context.isTestSource = true
            testContexts.add(context)
        }

        // Visit all contexts
        if (!contexts.isEmpty() || !testContexts.isEmpty()) {
            visitJavaFiles(checks, project, main, contexts, testContexts)
        }
    }

    private fun visitJavaFiles(
        checks: List<Detector>,
        project: Project,
        main: Project?,
        contexts: List<JavaContext>,
        testContexts: List<JavaContext>
    ) {
        val allContexts: List<JavaContext>
        if (testContexts.isEmpty()) {
            allContexts = contexts
        } else {
            allContexts = ArrayList(contexts.size + testContexts.size)
            allContexts.addAll(contexts)
            allContexts.addAll(testContexts)
        }

        // Force all test sources into the normal source check (where all checks apply) ?
        if (isCheckTestSources) {
            visitJavaFiles(checks, project, main, allContexts, allContexts, emptyList())
        } else {
            visitJavaFiles(checks, project, main, allContexts, contexts, testContexts)
        }
    }

    private fun visitJavaFiles(
        checks: List<Detector>,
        project: Project,
        main: Project?,
        allContexts: List<JavaContext>,
        srcContexts: List<JavaContext>,
        testContexts: List<JavaContext>
    ) {
        // Temporary: we still have some builtin checks that aren't migrated to
        // PSI. Until that's complete, remove them from the list here
        val uastScanners = ArrayList<Detector>(checks.size)
        for (detector in checks) {
            if (detector is SourceCodeScanner) {
                uastScanners.add(detector)
            }
        }

        if (!uastScanners.isEmpty()) {
            val parser = client.getUastParser(currentProject)
            for (context in allContexts) {
                context.uastParser = parser
            }
            val uElementVisitor = UElementVisitor(parser, uastScanners)

            parserErrors = !uElementVisitor.prepare(srcContexts, testContexts)

            for (context in srcContexts) {
                fireEvent(EventType.SCANNING_FILE, context)
                // TODO: Don't hold read lock around the entire process?
                client.runReadAction(Runnable { uElementVisitor.visitFile(context) })
                if (isCanceled) {
                    return
                }
            }

            val projectContext = Context(this, project, main, project.dir)
            uElementVisitor.visitGroups(projectContext, allContexts)
            uElementVisitor.dispose()

            if (!testContexts.isEmpty()) {
                val testScanners = filterTestScanners(uastScanners)
                if (!testScanners.isEmpty()) {
                    val uTestVisitor = UElementVisitor(parser, testScanners)

                    for (context in testContexts) {
                        fireEvent(EventType.SCANNING_FILE, context)
                        // TODO: Don't hold read lock around the entire process?
                        client.runReadAction(Runnable { uTestVisitor.visitFile(context) })
                        if (isCanceled) {
                            return
                        }
                    }

                    uTestVisitor.dispose()
                }
            }
        }
    }

    private fun filterTestScanners(scanners: List<Detector>): List<Detector> {
        val testScanners = ArrayList<Detector>(scanners.size)
        // Compute intersection of Java and test scanners
        var sourceScanners: Collection<Detector> =
            scopeDetectors[Scope.TEST_SOURCES] ?: return emptyList()
        if (sourceScanners.size > 15 && scanners.size > 15) {
            sourceScanners = Sets.newHashSet(sourceScanners) // switch from list to set
        }
        for (check in scanners) {
            if (sourceScanners.contains(check)) {
                testScanners.add(check)
            }
        }
        return testScanners
    }

    private fun checkIndividualJavaFiles(
        project: Project,
        main: Project?,
        checks: List<Detector>,
        files: List<File>
    ) {

        val contexts = ArrayList<JavaContext>(files.size)
        val testContexts = ArrayList<JavaContext>(files.size)
        val testFolders = project.testSourceFolders
        for (file in files) {
            if (file.isFile) {
                val path = file.path
                if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
                    val context = JavaContext(this, project, main, file)

                    // Figure out if this file is a test context
                    if (testFolders.asSequence().any { FileUtil.isAncestor(it, file, false) }) {
                        context.isTestSource = true
                        testContexts.add(context)
                    } else {
                        contexts.add(context)
                    }
                }
            }
        }

        if (contexts.isEmpty() && testContexts.isEmpty()) {
            return
        }

        // We're not sure if these individual files are tests or non-tests; treat them
        // as non-tests now. This gives you warnings if you're editing an individual
        // test file for example.

        visitJavaFiles(checks, project, main, contexts, testContexts)
    }

    private var currentFolderType: ResourceFolderType? = null
    private var currentXmlDetectors: List<XmlScanner>? = null
    private var currentBinaryDetectors: List<Detector>? = null
    private var currentVisitor: ResourceVisitor? = null

    private fun getVisitor(
        type: ResourceFolderType,
        checks: List<XmlScanner>,
        binaryChecks: List<Detector>?
    ): ResourceVisitor? {
        if (type != currentFolderType) {
            currentFolderType = type

            // Determine which XML resource detectors apply to the given folder type
            val applicableXmlChecks = ArrayList<XmlScanner>(checks.size)
            for (check in checks) {
                if (check.appliesTo(type)) {
                    applicableXmlChecks.add(check)
                }
            }
            var applicableBinaryChecks: MutableList<Detector>? = null
            if (binaryChecks != null) {
                applicableBinaryChecks = ArrayList(binaryChecks.size)
                for (check in binaryChecks) {
                    if (check.appliesTo(type)) {
                        applicableBinaryChecks.add(check)
                    }
                }
            }

            // If the list of detectors hasn't changed, then just use the current visitor!
            if (currentXmlDetectors != null && currentXmlDetectors == applicableXmlChecks
                && Objects.equal(currentBinaryDetectors, applicableBinaryChecks)
            ) {
                return currentVisitor
            }

            currentXmlDetectors = applicableXmlChecks
            currentBinaryDetectors = applicableBinaryChecks

            if (applicableXmlChecks.isEmpty() &&
                (applicableBinaryChecks == null || applicableBinaryChecks.isEmpty())
            ) {
                currentVisitor = null
                return null
            }

            val parser = client.xmlParser
            currentVisitor = ResourceVisitor(
                parser, applicableXmlChecks,
                applicableBinaryChecks
            )
        }

        return currentVisitor
    }

    private fun checkResFolder(
        project: Project,
        main: Project?,
        res: File,
        xmlChecks: List<XmlScanner>,
        dirChecks: List<Detector>?,
        binaryChecks: List<Detector>?
    ) {
        val resourceDirs = res.listFiles() ?: return

        // Sort alphabetically such that we can process related folder types at the
        // same time, and to have a defined behavior such that detectors can rely on
        // predictable ordering, e.g. layouts are seen before menus are seen before
        // values, etc (l < m < v).

        Arrays.sort(resourceDirs)
        for (dir in resourceDirs) {
            val type = ResourceFolderType.getFolderType(dir.name)
            if (type != null) {
                checkResourceFolder(project, main, dir, type, xmlChecks, dirChecks, binaryChecks)
            }

            if (isCanceled) {
                return
            }
        }
    }

    private fun checkResourceFolder(
        project: Project,
        main: Project?,
        dir: File,
        type: ResourceFolderType,
        xmlChecks: List<XmlScanner>,
        dirChecks: List<Detector>?,
        binaryChecks: List<Detector>?
    ) {

        // Process the resource folder

        if (dirChecks != null && !dirChecks.isEmpty()) {
            val context = ResourceContext(this, project, main, dir, type, "")
            val folderName = dir.name
            fireEvent(EventType.SCANNING_FILE, context)
            for (check in dirChecks) {
                if (check.appliesTo(type)) {
                    check.beforeCheckFile(context)
                    check.checkFolder(context, folderName)
                    check.afterCheckFile(context)
                }
            }
            if (binaryChecks == null && xmlChecks.isEmpty()) {
                return
            }
        }

        val files = dir.listFiles()
        if (files == null || files.isEmpty()) {
            return
        }

        val visitor = getVisitor(type, xmlChecks, binaryChecks)
        if (visitor != null) { // if not, there are no applicable rules in this folder
            val parser = visitor.parser

            // Process files in alphabetical order, to ensure stable output
            // (for example for the duplicate resource detector)
            Arrays.sort(files)
            for (file in files) {
                if (LintUtils.isXmlFile(file)) {
                    val context = createXmlContext(project, main, file, type, parser) ?: continue
                    try {
                        fireEvent(EventType.SCANNING_FILE, context)
                        visitor.visitFile(context)
                    } finally {
                        disposeXmlContext(context)
                    }
                } else if (binaryChecks != null &&
                    (isBitmapFile(file) || type == ResourceFolderType.RAW)
                ) {
                    val context = object : ResourceContext(this, project, main, file, type, "") {
                        override val resourceFolder: File?
                        // Like super, but for the parent folder instead of the context file
                            get() = if (resourceFolderType != null) file.parentFile else null
                    }
                    fireEvent(EventType.SCANNING_FILE, context)
                    visitor.visitBinaryResource(context)
                }
                if (isCanceled) {
                    return
                }
            }
        }
    }

    private fun disposeXmlContext(context: XmlContext) =
        context.parser.dispose(context, context.document)

    private fun createXmlContext(
        project: Project,
        main: Project?,
        file: File,
        type: ResourceFolderType?,
        parser: XmlParser
    ): XmlContext? {
        assert(LintUtils.isXmlFile(file))
        val contents = client.readFile(file)
        if (contents.isEmpty()) {
            return null
        }
        val xml = contents.toString()
        val document = parser.parseXml(xml, file) ?: return null

        // Ignore empty documents
        document.documentElement ?: return null

        return XmlContext(this, project, main, file, type, parser, xml, document)
    }

    /** Checks individual resources  */
    private fun checkIndividualResources(
        project: Project,
        main: Project?,
        xmlDetectors: List<XmlScanner>,
        dirChecks: List<Detector>?,
        binaryChecks: List<Detector>?,
        files: List<File>
    ) {
        for (file in files) {
            if (file.isDirectory) {
                // Is it a resource folder?
                val type = ResourceFolderType.getFolderType(file.name)
                if (type != null && File(file.parentFile, RES_FOLDER).exists()) {
                    // Yes.
                    checkResourceFolder(
                        project, main, file, type, xmlDetectors, dirChecks,
                        binaryChecks
                    )
                } else if (file.name == RES_FOLDER) { // Is it the res folder?
                    // Yes
                    checkResFolder(project, main, file, xmlDetectors, dirChecks, binaryChecks)
                }
            } else if (file.isFile && LintUtils.isXmlFile(file)) {
                // Yes, find out its resource type
                val folderName = file.parentFile.name
                val type = ResourceFolderType.getFolderType(folderName)
                if (type != null) {
                    val visitor = getVisitor(type, xmlDetectors, binaryChecks)
                    if (visitor != null) {
                        val parser = visitor.parser
                        val context = createXmlContext(project, main, file, type, parser)
                        if (context != null) {
                            try {
                                fireEvent(EventType.SCANNING_FILE, context)
                                visitor.visitFile(context)
                            } finally {
                                disposeXmlContext(context)
                            }
                        }
                    }
                }
            } else if (binaryChecks != null && file.isFile && isBitmapFile(file)) {
                // Yes, find out its resource type
                val folderName = file.parentFile.name
                val type = ResourceFolderType.getFolderType(folderName)
                if (type != null) {
                    val visitor = getVisitor(type, xmlDetectors, binaryChecks)
                    if (visitor != null) {
                        val context = ResourceContext(this, project, main, file, type, "")
                        fireEvent(EventType.SCANNING_FILE, context)
                        visitor.visitBinaryResource(context)
                        if (isCanceled) {
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a listener to be notified of lint progress
     *
     * @param listener the listener to be added
     */
    fun addLintListener(listener: LintListener) {
        if (listeners == null) {
            listeners = ArrayList(1)
        }
        listeners!!.add(listener)
    }

    /**
     * Removes a listener such that it is no longer notified of progress
     *
     * @param listener the listener to be removed
     */
    fun removeLintListener(listener: LintListener) {
        listeners!!.remove(listener)
        if (listeners!!.isEmpty()) {
            listeners = null
        }
    }

    /** Notifies listeners, if any, that the given event has occurred  */
    private fun fireEvent(
        type: LintListener.EventType,
        context: Context? = null,
        project: Project? = context?.project
    ) {
        if (listeners != null) {
            for (listener in listeners!!) {
                listener.update(this, type, project, context)
            }
        }
    }

    /**
     * Wrapper around the lint client. This sits in the middle between a
     * detector calling for example [LintClient.report] and
     * the actual embedding tool, and performs filtering etc such that detectors
     * and lint clients don't have to make sure they check for ignored issues or
     * filtered out warnings.
     */
    private inner class LintClientWrapper(private val delegate: LintClient) :
        LintClient(clientName) {

        override fun getMergedManifest(project: Project): Document? =
            delegate.getMergedManifest(project)

        override fun resolveMergeManifestSources(
            mergedManifest: Document,
            reportFile: Any
        ) =
            delegate.resolveMergeManifestSources(mergedManifest, reportFile)

        override fun findManifestSourceNode(
            mergedNode: org.w3c.dom.Node
        ): Pair<File, out org.w3c.dom.Node>? =
            delegate.findManifestSourceNode(mergedNode)

        override fun findManifestSourceLocation(mergedNode: org.w3c.dom.Node): Location? =
            delegate.findManifestSourceLocation(mergedNode)

        override fun report(
            context: Context,
            issue: Issue,
            severity: Severity,
            location: Location,
            message: String,
            format: TextFormat,
            fix: LintFix?
        ) {

            if (currentProject != null && currentProject?.reportIssues == false) {
                return
            }

            val configuration = context.configuration
            if (!configuration.isEnabled(issue)) {
                if (issue.category !== Category.LINT) {
                    delegate.log(
                        null, "Incorrect detector reported disabled issue %1\$s",
                        issue.toString()
                    )
                }
                return
            }

            if (configuration.isIgnored(context, issue, location, message)) {
                return
            }

            if (severity == Severity.IGNORE) {
                return
            }

            if (baseline != null) {
                val filtered = baseline!!.findAndMark(
                    issue, location, message, severity,
                    context.project
                )
                if (filtered) {
                    return
                }
            }

            delegate.report(context, issue, severity, location, message, format, fix)
        }

        private fun unsupported(): Nothing =
            throw UnsupportedOperationException(
                "This method should not be called by lint " +
                        "detectors; it is intended only for usage by the lint infrastructure"
            )

        // Everything else just delegates to the embedding lint client

        override fun getConfiguration(
            project: Project,
            driver: LintDriver?
        ): Configuration =
            delegate.getConfiguration(project, driver)

        override fun getDisplayPath(file: File): String = delegate.getDisplayPath(file)

        override fun log(
            severity: Severity,
            exception: Throwable?,
            format: String?,
            vararg args: Any
        ) = delegate.log(exception, format, *args)

        override fun getTestLibraries(project: Project): List<File> =
            delegate.getTestLibraries(project)

        override fun getClientRevision(): String? = delegate.getClientRevision()

        override fun runReadAction(runnable: Runnable) = delegate.runReadAction(runnable)

        override fun <T> runReadAction(computable: Computable<T>): T =
            delegate.runReadAction(computable)

        override fun readFile(file: File): CharSequence = delegate.readFile(file)

        @Throws(IOException::class)
        override fun readBytes(file: File): ByteArray = delegate.readBytes(file)

        override fun getJavaSourceFolders(project: Project): List<File> =
            delegate.getJavaSourceFolders(project)

        override fun getGeneratedSourceFolders(project: Project): List<File> =
            delegate.getGeneratedSourceFolders(project)

        override fun getJavaClassFolders(project: Project): List<File> =
            delegate.getJavaClassFolders(project)

        override fun getJavaLibraries(project: Project, includeProvided: Boolean): List<File> =
            delegate.getJavaLibraries(project, includeProvided)

        override fun getTestSourceFolders(project: Project): List<File> =
            delegate.getTestSourceFolders(project)

        override fun getBuildTools(project: Project): BuildToolInfo? =
            delegate.getBuildTools(project)

        override fun createSuperClassMap(project: Project): Map<String, String> =
            delegate.createSuperClassMap(project)

        override fun getResourceFolders(project: Project): List<File> =
            delegate.getResourceFolders(project)

        override val xmlParser: XmlParser
            get() = delegate.xmlParser

        override fun replaceDetector(
            detectorClass: Class<out Detector>
        ): Class<out Detector> =
            delegate.replaceDetector(detectorClass)

        override fun getSdkInfo(project: Project): SdkInfo = delegate.getSdkInfo(project)

        override fun getProject(dir: File, referenceDir: File): Project =
            delegate.getProject(dir, referenceDir)

        override fun getUastParser(project: Project?): UastParser = delegate.getUastParser(project)

        override fun findResource(relativePath: String): File? = delegate.findResource(relativePath)

        override fun getCacheDir(name: String?, create: Boolean): File? =
            delegate.getCacheDir(name, create)

        override fun getClassPath(project: Project): LintClient.ClassPathInfo =
            delegate.performGetClassPath(project)

        override fun log(
            exception: Throwable?,
            format: String?,
            vararg args: Any
        ) = delegate.log(exception, format, *args)

        override fun initializeProjects(knownProjects: Collection<Project>): Unit = unsupported()

        override fun disposeProjects(knownProjects: Collection<Project>): Unit = unsupported()

        override fun getSdkHome(): File? = delegate.getSdkHome()

        override fun getTargets(): Array<IAndroidTarget> = delegate.getTargets()

        override fun getSdk(): AndroidSdkHandler? = delegate.getSdk()

        override fun getCompileTarget(project: Project): IAndroidTarget? =
            delegate.getCompileTarget(project)

        override fun getSuperClass(project: Project, name: String): String? =
            delegate.getSuperClass(project, name)

        override fun isSubclassOf(
            project: Project,
            name: String,
            superClassName: String
        ): Boolean? =
            delegate.isSubclassOf(project, name, superClassName)

        override fun getProjectName(project: Project): String = delegate.getProjectName(project)

        override fun isGradleProject(project: Project): Boolean = delegate.isGradleProject(project)

        override fun createProject(dir: File, referenceDir: File): Project = unsupported()

        override fun findGlobalRuleJars(): List<File> = delegate.findGlobalRuleJars()

        override fun findRuleJars(project: Project): List<File> = delegate.findRuleJars(project)

        override fun isProjectDirectory(dir: File): Boolean = delegate.isProjectDirectory(dir)

        override fun registerProject(dir: File, project: Project): Unit = unsupported()

        override fun addCustomLintRules(registry: IssueRegistry): IssueRegistry =
            delegate.addCustomLintRules(registry)

        override fun getAssetFolders(project: Project): List<File> =
            delegate.getAssetFolders(project)

        override fun createUrlClassLoader(urls: Array<URL>, parent: ClassLoader): ClassLoader =
            delegate.createUrlClassLoader(urls, parent)

        override fun checkForSuppressComments(): Boolean = delegate.checkForSuppressComments()

        override fun supportsProjectResources(): Boolean = delegate.supportsProjectResources()

        override fun getResourceRepository(
            project: Project,
            includeModuleDependencies: Boolean,
            includeLibraries: Boolean
        ): AbstractResourceRepository? =
            delegate.getResourceRepository(
                project, includeModuleDependencies,
                includeLibraries
            )

        override fun getRepositoryLogger(): ProgressIndicator = delegate.getRepositoryLogger()

        override fun getResourceVisibilityProvider(): ResourceVisibilityLookup.Provider =
            delegate.getResourceVisibilityProvider()

        override fun createResourceItemHandle(item: ResourceItem): Location.Handle =
            delegate.createResourceItemHandle(item)

        @Throws(IOException::class)
        override fun openConnection(url: URL): URLConnection? = delegate.openConnection(url)

        @Throws(IOException::class)
        override fun openConnection(url: URL, timeout: Int): URLConnection? =
            delegate.openConnection(url, timeout)

        override fun closeConnection(connection: URLConnection) =
            delegate.closeConnection(connection)
    }

    /**
     * Requests another pass through the data for the given detector. This is
     * typically done when a detector needs to do more expensive computation,
     * but it only wants to do this once it **knows** that an error is
     * present, or once it knows more specifically what to check for.
     *
     * @param detector the detector that should be included in the next pass.
     *            Note that the lint runner may refuse to run more than a couple
     *            of runs.
     *
     * @param scope the scope to be revisited. This must be a subset of the
     *       current scope ([.getScope], and it is just a performance hint;
     *       in particular, the detector should be prepared to be called on other
     *       scopes as well (since they may have been requested by other detectors).
     *       You can pall null to indicate "all".
     */
    fun requestRepeat(detector: Detector, scope: EnumSet<Scope>?) {
        if (repeatingDetectors == null) {
            repeatingDetectors = ArrayList()
        }
        repeatingDetectors!!.add(detector)

        if (scope != null) {
            if (repeatScope == null) {
                repeatScope = scope
            } else {
                repeatScope = EnumSet.copyOf(repeatScope)
                repeatScope!!.addAll(scope)
            }
        } else {
            repeatScope = Scope.ALL
        }
    }

    // Unfortunately, ASMs nodes do not extend a common DOM node type with parent
    // pointers, so we have to have multiple methods which pass in each type
    // of node (class, method, field) to be checked.

    /**
     * Returns whether the given issue is suppressed in the given method.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     *
     * @param classNode the class containing the issue
     *
     * @param method the method containing the issue
     *
     * @param instruction the instruction within the method, if any
     *
     * @return true if there is a suppress annotation covering the specific
     *         issue on this method
     */
    fun isSuppressed(
        issue: Issue?,
        classNode: ClassNode,
        method: MethodNode,
        instruction: AbstractInsnNode?
    ): Boolean {
        if (method.invisibleAnnotations != null) {
            @Suppress("UNCHECKED_CAST")
            val annotations = method.invisibleAnnotations as List<AnnotationNode>
            return isSuppressed(issue, annotations)
        }

        // Initializations of fields end up placed in generated methods (<init>
        // for members and <clinit> for static fields).
        if (instruction != null && method.name[0] == '<') {
            val next = LintUtils.getNextInstruction(instruction)
            if (next != null && next.type == AbstractInsnNode.FIELD_INSN) {
                val fieldRef = next as FieldInsnNode?
                val field = findField(classNode, fieldRef!!.owner, fieldRef.name)
                if (field != null && isSuppressed(issue, field)) {
                    return true
                }
            } else if (classNode.outerClass != null && classNode.outerMethod == null &&
                isAnonymousClass(classNode)
            ) {
                if (isSuppressed(issue, classNode)) {
                    return true
                }
            }
        }

        return false
    }

    private fun findField(
        classNode: ClassNode,
        owner: String,
        name: String
    ): FieldNode? {
        var current: ClassNode? = classNode
        while (current != null) {
            if (owner == current.name) {
                val fieldList = current.fields// ASM API
                for (f in fieldList) {
                    val field = f as FieldNode
                    if (field.name == name) {
                        return field
                    }
                }
                return null
            }
            current = getOuterClassNode(current)
        }
        return null
    }

    private fun findMethod(
        classNode: ClassNode,
        name: String,
        includeInherited: Boolean
    ): MethodNode? {
        var current: ClassNode? = classNode
        while (current != null) {
            val methodList = current.methods// ASM API
            for (f in methodList) {
                val method = f as MethodNode
                if (method.name == name) {
                    return method
                }
            }

            current = if (includeInherited) {
                getOuterClassNode(current)
            } else {
                break
            }
        }
        return null
    }

    /**
     * Returns whether the given issue is suppressed for the given field.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     *
     * @param field the field potentially annotated with a suppress annotation
     *
     * @return true if there is a suppress annotation covering the specific
     *         issue on this field
     */
    // API; reserve need to require driver state later
    fun isSuppressed(issue: Issue?, field: FieldNode): Boolean {
        if (field.invisibleAnnotations != null) {
            @Suppress("UNCHECKED_CAST")
            val annotations = field.invisibleAnnotations as List<AnnotationNode>
            return isSuppressed(issue, annotations)
        }

        return false
    }

    /**
     * Returns whether the given issue is suppressed in the given class.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     *
     * @param classNode the class containing the issue
     *
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    fun isSuppressed(issue: Issue?, classNode: ClassNode): Boolean {
        if (classNode.invisibleAnnotations != null) {
            @Suppress("UNCHECKED_CAST")
            val annotations = classNode.invisibleAnnotations as List<AnnotationNode>
            return isSuppressed(issue, annotations)
        }

        if (classNode.outerClass != null && classNode.outerMethod == null &&
            isAnonymousClass(classNode)
        ) {
            val outer = getOuterClassNode(classNode)
            if (outer != null) {
                var m = findMethod(outer, CONSTRUCTOR_NAME, false)
                if (m != null) {
                    val call = findConstructorInvocation(m, classNode.name)
                    if (call != null) {
                        if (isSuppressed(issue, outer, m, call)) {
                            return true
                        }
                    }
                }
                m = findMethod(outer, CLASS_CONSTRUCTOR, false)
                if (m != null) {
                    val call = findConstructorInvocation(m, classNode.name)
                    if (call != null) {
                        if (isSuppressed(issue, outer, m, call)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun isSuppressed(issue: Issue?, annotations: List<AnnotationNode>): Boolean {
        for (annotation in annotations) {
            val desc = annotation.desc

            // We could obey @SuppressWarnings("all") too, but no need to look for it
            // because that annotation only has source retention.

            if (desc.endsWith(SUPPRESS_LINT_VMSIG)) {
                if (annotation.values != null) {
                    var i = 0
                    val n = annotation.values.size
                    while (i < n) {
                        val key = annotation.values[i] as String
                        if (key == "value") {
                            val value = annotation.values[i + 1]
                            if (value is String) {
                                if (matches(issue, value)) {
                                    return true
                                }
                            } else if (value is List<*>) {
                                for (v in value) {
                                    if (v is String) {
                                        if (matches(issue, v)) {
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                        i += 2
                    }
                }
            }
        }

        return false
    }

    fun isSuppressed(
        context: JavaContext?,
        issue: Issue,
        scope: UElement?
    ): Boolean {
        var currentScope = scope
        val checkComments = client.checkForSuppressComments() &&
                context != null && context.containsCommentSuppress()
        while (currentScope != null) {
            if (currentScope is UAnnotated) {
                if (isSuppressed(issue, currentScope)) {
                    return true
                }
            }

            if (checkComments && context != null &&
                context.isSuppressedWithComment(currentScope, issue)
            ) {
                return true
            }

            if (currentScope is UFile) {
                return false
            }
            currentScope = currentScope.uastParent
        }

        return false
    }

    fun isSuppressed(
        context: JavaContext?,
        issue: Issue,
        scope: PsiElement?
    ): Boolean {
        var currentScope = scope
        val checkComments = client.checkForSuppressComments() &&
                context != null && context.containsCommentSuppress()
        while (currentScope != null) {
            if (currentScope is PsiModifierListOwner) {
                if (isSuppressed(issue, currentScope.modifierList)) {
                    return true
                }
            }

            if (checkComments && context!!.isSuppressedWithComment(currentScope, issue)) {
                return true
            }

            currentScope = currentScope.parent
            if (currentScope is PsiFile) {
                return false
            }
        }

        return false
    }

    /**
     * Returns whether the given issue is suppressed in the given XML DOM node.
     *
     * @param issue the issue to be checked, or null to just check for "all"
     *
     * @param node the DOM node containing the issue
     *
     * @return true if there is a suppress annotation covering the specific
     *         issue in this class
     */
    fun isSuppressed(
        context: XmlContext?,
        issue: Issue,
        node: org.w3c.dom.Node?
    ): Boolean {
        var currentNode = node
        if (currentNode is Attr) {
            currentNode = currentNode.ownerElement
        }
        val checkComments = client.checkForSuppressComments()
                && context != null && context.containsCommentSuppress()
        while (currentNode != null) {
            if (currentNode.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val element = currentNode as Element
                if (element.hasAttributeNS(TOOLS_URI, ATTR_IGNORE)) {
                    val ignore = element.getAttributeNS(TOOLS_URI, ATTR_IGNORE)
                    if (isSuppressed(issue, ignore)) {
                        return true
                    }
                } else if (checkComments && context!!.isSuppressedWithComment(currentNode, issue)) {
                    return true
                }
            }

            currentNode = currentNode.parentNode
        }

        return false
    }

    private var cachedFolder: File? = null
    private var cachedFolderVersion = -1

    /**
     * Returns the folder version of the given file. For example, for the file values-v14/foo.xml,
     * it returns 14.
     *
     * @param resourceFile the file to be checked
     *
     * @return the folder version, or -1 if no specific version was specified
     */
    fun getResourceFolderVersion(resourceFile: File): Int {
        val parent = resourceFile.parentFile ?: return -1
        if (parent == cachedFolder) {
            return cachedFolderVersion
        }

        cachedFolder = parent
        cachedFolderVersion = -1

        for (qualifier in QUALIFIER_SPLITTER.split(parent.name)) {
            val matcher = VERSION_PATTERN.matcher(qualifier)
            if (matcher.matches()) {
                val group = matcher.group(1)!!
                cachedFolderVersion = Integer.parseInt(group)
                break
            }
        }

        return cachedFolderVersion
    }

    companion object {
        /**
         * Max number of passes to run through the lint runner if requested by
         * [.requestRepeat]
         */
        private const val MAX_PHASES = 3

        private const val SUPPRESS_LINT_VMSIG = "/$SUPPRESS_LINT;"

        /** Prefix used by the comment suppress mechanism in Studio/IntelliJ  */
        private const val STUDIO_ID_PREFIX = "AndroidLint"

        /**
         * For testing only: returns the number of exceptions thrown during Java AST analysis
         *
         * @return the number of internal errors found
         */
        @get:VisibleForTesting
        @JvmStatic
        var crashCount: Int = 0
            private set

        /** Max number of logs to include  */
        private const val MAX_REPORTED_CRASHES = 20

        /**
         * Handles an exception and returns whether the lint analysis can continue (true means
         * continue, false means abort)
         */
        @JvmStatic
        fun handleDetectorError(
            context: Context?,
            driver: LintDriver,
            throwable: Throwable
        ): Boolean {
            when {
                throwable is IndexNotReadyException -> {
                    // Attempting to access PSI during startup before indices are ready;
                    // ignore these (because once indexing is over highlighting will be
                    // retriggered.)
                    //
                    // See http://b.android.com/176644 for an example.
                    return true
                }
                throwable is ProcessCanceledException -> {
                    // Cancelling inspections in the IDE
                    driver.cancel()
                    return false
                }
                throwable is AssertionError &&
                        throwable.message?.startsWith("Already disposed: ") == true -> {
                    // Editor is in the middle of analysis when project
                    // is created. This isn't common, but is often triggered by Studio UI
                    // testsuite which rapidly opens, edits and closes projects.
                    // Silently abort the analysis.
                    return false
                }
            }

            if (crashCount++ > MAX_REPORTED_CRASHES) {
                // No need to keep spamming the user that a lot of the files
                // are tripping up ECJ, they get the picture.
                return true
            }

            val sb = StringBuilder(100)
            sb.append("Unexpected failure during lint analysis")
            context?.file?.name?.let { sb.append(" of ").append(it) }
            sb.append(" (this is a bug in lint or one of the libraries it depends on)\n\n")
            if (throwable.message?.isNotBlank() == true) {
                sb.append("Message: ${throwable.message}\n")
            }
            sb.append("Stack: ")
            sb.append("`")
            sb.append(throwable.javaClass.simpleName)
            sb.append(':')
            appendStackTraceSummary(throwable, sb)
            sb.append("`")
            sb.append(
                "\n\nYou can set environment variable `LINT_PRINT_STACKTRACE=true` to " +
                        "dump a full stacktrace to stdout."
            )

            val throwableMessage = throwable.message
            if (throwableMessage != null && throwableMessage.startsWith(
                    "loader constraint violation: when resolving field \"QUALIFIER_SPLITTER\" the class loader"
                )
            ) {
                // Rewrite error message
                sb.setLength(0)
                sb.append(
                    """
                    Lint crashed because it is being invoked with the wrong version of Guava
                    (the Android version instead of the JRE version, which is required in the
                    Gradle plugin).

                    This usually happens when projects incorrectly install a dependency resolution
                    strategy in **all** configurations instead of just the compile and run
                    configurations.

                    See https://issuetracker.google.com/71991293 for more information and the
                    proper way to install a dependency resolution strategy.

                    (Note that this breaks a lot of lint analysis so this report is incomplete.)
                    """.trimIndent()
                )
            }

            val project = when {
                driver.currentProject != null -> driver.currentProject
                driver.currentProjects?.isNotEmpty() == true -> driver.currentProjects?.last()
                else -> null
            }
            val message = sb.toString()
            when {
                context != null -> context.report(
                    IssueRegistry.LINT_ERROR,
                    Location.create(context.file),
                    message
                )
                project != null -> {
                    val projectDir = project.dir
                    val projectContext = Context(driver, project, null, projectDir)
                    projectContext.report(
                        IssueRegistry.LINT_ERROR,
                        Location.create(project.dir),
                        message
                    )
                }
                else -> driver.client.log(throwable, message)
            }

            if (VALUE_TRUE == System.getenv("LINT_PRINT_STACKTRACE")) {
                throwable.printStackTrace()
            }

            return true
        }

        fun appendStackTraceSummary(throwable: Throwable, sb: StringBuilder) {
            val stackTrace = throwable.stackTrace
            var count = 0
            for (frame in stackTrace) {
                if (count > 0) {
                    sb.append('\u2190') // Left arrow
                }

                val className = frame.className
                sb.append(className.substring(className.lastIndexOf('.') + 1))
                sb.append('.').append(frame.methodName)
                sb.append('(')
                sb.append(frame.fileName).append(':').append(frame.lineNumber)
                sb.append(')')
                count++
                // Only print the top N frames such that we can identify the bug
                if (count == 8) {
                    break
                }
            }
        }

        /**
         * For testing only: clears the crash counter
         */
        @JvmStatic
        @VisibleForTesting
        fun clearCrashCount() {
            crashCount = 0
        }

        @Contract("!null,_->!null")
        private fun union(
            list1: List<Detector>?,
            list2: List<Detector>?
        ): List<Detector>? =
            when {
                list1 == null -> list2
                list2 == null -> list1
                else -> {
                    // Use set to pick out unique detectors, since it's possible for there to be overlap,
                    // e.g. the DuplicateIdDetector registers both a cross-resource issue and a
                    // single-file issue, so it shows up on both scope lists:
                    val set = HashSet<Detector>(list1.size + list2.size)
                    set.addAll(list1)
                    set.addAll(list2)

                    ArrayList(set)
                }
            }

        private fun gatherJavaFiles(dir: File, result: MutableList<File>) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        val path = file.path
                        if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
                            result.add(file)
                        }
                    } else if (file.isDirectory) {
                        gatherJavaFiles(file, result)
                    }
                }
            }
        }

        private fun findConstructorInvocation(
            method: MethodNode,
            className: String
        ): MethodInsnNode? {
            val nodes = method.instructions
            var i = 0
            val n = nodes.size()
            while (i < n) {
                val instruction = nodes.get(i)
                if (instruction.opcode == Opcodes.INVOKESPECIAL) {
                    val call = instruction as MethodInsnNode
                    if (className == call.owner) {
                        return call
                    }
                }
                i++
            }

            return null
        }

        private fun matches(issue: Issue?, id: String): Boolean {
            if (id.equals(SUPPRESS_ALL, ignoreCase = true)) {
                return true
            }

            if (issue != null) {
                val issueId = issue.id
                if (id.equals(issueId, ignoreCase = true)) {
                    return true
                }
                if (id.startsWith(STUDIO_ID_PREFIX)
                    && id.regionMatches(
                        STUDIO_ID_PREFIX.length,
                        issueId,
                        0,
                        issueId.length,
                        ignoreCase = true
                    )
                    && id.substring(STUDIO_ID_PREFIX.length).equals(issueId, ignoreCase = true)
                ) {
                    return true
                }
            }

            return false
        }

        /**
         * Returns true if the given issue is suppressed by the given suppress string; this
         * is typically the same as the issue id, but is allowed to not match case sensitively,
         * and is allowed to be a comma separated list, and can be the string "all"
         *
         * @param issue the issue id to match
         *
         * @param string the suppress string -- typically the id, or "all", or a comma separated list
         *               of ids
         *
         * @return true if the issue is suppressed by the given string
         */
        private fun isSuppressed(issue: Issue, string: String): Boolean {
            if (string.isEmpty()) {
                return false
            }

            if (string.indexOf(',') == -1) {
                if (matches(issue, string)) {
                    return true
                }
            } else {
                for (id in Splitter.on(',').trimResults().split(string)) {
                    if (matches(issue, id)) {
                        return true
                    }
                }
            }

            return false
        }

        private const val SUPPRESS_WARNINGS_FQCN = "java.lang.SuppressWarnings"

        /**
         * Returns true if the given AST modifier has a suppress annotation for the
         * given issue (which can be null to check for the "all" annotation)
         *
         * @param issue the issue to be checked
         *
         * @param modifierList the modifier to check
         *
         * @return true if the issue or all issues should be suppressed for this
         *         modifier
         */
        @JvmStatic
        fun isSuppressed(
            issue: Issue,
            modifierList: PsiModifierList?
        ): Boolean {
            if (modifierList == null) {
                return false
            }

            for (annotation in modifierList.annotations) {
                val fqcn = annotation.qualifiedName
                if (fqcn != null && (fqcn == FQCN_SUPPRESS_LINT
                            || fqcn == SUPPRESS_WARNINGS_FQCN
                            || fqcn == KOTLIN_SUPPRESS
                            || fqcn == SUPPRESS_LINT)
                ) { // when missing imports
                    val parameterList = annotation.parameterList
                    for (pair in parameterList.attributes) {
                        if (isSuppressed(issue, pair.value)) {
                            return true
                        }
                    }
                }
            }

            return false
        }

        /**
         * Returns true if the given AST modifier has a suppress annotation for the
         * given issue (which can be null to check for the "all" annotation)
         *
         * @param issue the issue to be checked
         *
         * @param annotated the annotated element
         *
         * @return true if the issue or all issues should be suppressed for this
         *         modifier
         */
        @JvmStatic
        fun isSuppressed(issue: Issue, annotated: UAnnotated): Boolean {
            val annotations = annotated.annotations
            if (annotations.isEmpty()) {
                return false
            }

            for (annotation in annotations) {
                val fqcn = annotation.qualifiedName
                if (fqcn != null && (fqcn == FQCN_SUPPRESS_LINT
                            || fqcn == SUPPRESS_WARNINGS_FQCN
                            || fqcn == KOTLIN_SUPPRESS
                            || fqcn == SUPPRESS_LINT)
                ) { // when missing imports
                    val attributeList = annotation.attributeValues
                    for (attribute in attributeList) {
                        if (isSuppressedExpression(issue, attribute.expression)) {
                            return true
                        }
                    }
                } else if (fqcn == null) {
                    // Work around type resolution problems
                    // Work around bugs in UAST type resolution for file annotations:
                    // parse the source string instead.
                    val psi = annotation.psi ?: continue
                    if (psi is PsiCompiledElement) {
                        continue
                    }
                    val text = psi.text
                    if (text.contains("SuppressLint(") ||
                        text.contains("SuppressWarnings(") ||
                        text.contains("Suppress(")
                    ) {
                        val start = text.indexOf('(')
                        val end = text.indexOf(')', start + 1)
                        if (end != -1) {
                            var value = text.substring(start + 1, end)

                            // Strip off attribute name, e.g.
                            //   @SuppressLint(id = "O") -> O
                            val index = value.indexOf('=')
                            if (index != -1) {
                                value = value.substring(index + 1).trim()
                            }

                            // We're looking at source, so get rid of extra syntax
                            // characters, e.g. from { "foo", "bar" } to just foo, bar
                            //
                            value = value.replace(Regex("[\"{}]"), "")

                            if (isSuppressed(issue, value)) {
                                return true
                            }
                        }
                    }
                }
            }

            return false
        }

        /**
         * Returns true if the annotation member value, assumed to be specified on a a SuppressWarnings
         * or SuppressLint annotation, specifies the given id (or "all").
         *
         * @param issue the issue to be checked
         *
         * @param value the member value to check
         *
         * @return true if the issue or all issues should be suppressed for this modifier
         */
        @JvmStatic
        fun isSuppressed(
            issue: Issue,
            value: PsiAnnotationMemberValue?
        ): Boolean {
            if (value is PsiLiteral) {
                val literalValue = value.value
                if (literalValue is String) {
                    if (isSuppressed(issue, literalValue)) {
                        return true
                    }
                }
            } else if (value is PsiArrayInitializerMemberValue) {
                for (mmv in value.initializers) {
                    if (isSuppressed(issue, mmv)) {
                        return true
                    }
                }
            } else if (value is PsiArrayInitializerExpression) {
                val initializers = value.initializers
                for (e in initializers) {
                    if (isSuppressed(issue, e)) {
                        return true
                    }
                }
            }

            return false
        }

        /**
         * Returns true if the annotation member value, assumed to be specified on a a S
         * uppressWarnings or SuppressLint annotation, specifies the given id (or "all").
         *
         * @param issue the issue to be checked
         *
         * @param value the member value to check
         *
         * @return true if the issue or all issues should be suppressed for this modifier
         */
        @JvmStatic
        private fun isSuppressedExpression(issue: Issue, value: UExpression?): Boolean {
            if (value is ULiteralExpression) {
                val literalValue = value.value
                if (literalValue is String) {
                    if (isSuppressed(issue, literalValue)) {
                        return true
                    }
                }
            } else if (value is UCallExpression) {
                for (mmv in value.valueArguments) {
                    if (isSuppressedExpression(issue, mmv)) {
                        return true
                    }
                }
            }

            return false
        }

        /** Pattern for version qualifiers  */
        private val VERSION_PATTERN = Pattern.compile("^v(\\d+)$")
    }
}
