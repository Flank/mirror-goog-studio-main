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

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.google.common.annotations.Beta
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.EnumSet

/**
 * A detector is able to find a particular problem (or a set of related problems).
 * Each problem type is uniquely identified as an [Issue].
 *
 * Detectors will be called in a predefined order:
 *
 *  1.  Manifest file
 *  2.  Resource files, in alphabetical order by resource type
 *      (therefore, "layout" is checked before "values", "values-de" is checked before
 *      "values-en" but after "values", and so on.
 *  3.  Java sources
 *  4.  Java classes
 *  5.  Gradle files
 *  6.  Generic files
 *  7.  Proguard files
 *  8.  Property files
 *
 * If a detector needs information when processing a file type that comes from a type of
 * file later in the order above, they can request a second phase; see
 * [LintDriver.requestRepeat].

 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
abstract class Detector {
    /**
     * See [com.android.tools.lint.detector.api.SourceCodeScanner]; this class is
     * (temporarily) here for backwards compatibility
     */
    interface UastScanner : com.android.tools.lint.detector.api.SourceCodeScanner

    /**
     * See [com.android.tools.lint.detector.api.ClassScanner]; this class is
     * (temporarily) here for backwards compatibility
     */
    interface ClassScanner : com.android.tools.lint.detector.api.ClassScanner

    /**
     * See [com.android.tools.lint.detector.api.BinaryResourceScanner]; this class is
     * (temporarily) here for backwards compatibility
     */
    interface BinaryResourceScanner : com.android.tools.lint.detector.api.BinaryResourceScanner

    /**
     * See [com.android.tools.lint.detector.api.ResourceFolderScanner]; this class is
     * (temporarily) here for backwards compatibility
     */
    interface ResourceFolderScanner : com.android.tools.lint.detector.api.ResourceFolderScanner

    /**
     * See [com.android.tools.lint.detector.api.XmlScanner]; this class is (temporarily) here
     * for backwards compatibility
     */
    interface XmlScanner : com.android.tools.lint.detector.api.XmlScanner

    /**
     * See [com.android.tools.lint.detector.api.GradleScanner]; this class is (temporarily)
     * here for backwards compatibility
     */
    interface GradleScanner : com.android.tools.lint.detector.api.GradleScanner

    /**
     * See [com.android.tools.lint.detector.api.OtherFileScanner]; this class is
     * (temporarily) here for backwards compatibility
     */
    interface OtherFileScanner : com.android.tools.lint.detector.api.OtherFileScanner

    /**
     * Runs the detector. This method will not be called for certain specialized
     * detectors, such as [XmlScanner] and [SourceCodeScanner], where
     * there are specialized analysis methods instead such as
     * [XmlScanner.visitElement].
     *
     * @param context the context describing the work to be done
     */
    open fun run(context: Context) {}

    /**
     * Returns true if this detector applies to the given file
     *
     * @param context the context to check
     * @param file the file in the context to check
     * @return true if this detector applies to the given context and file
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Slated for removal") // Slated for removal in lint 2.0 - this method isn't used
    fun appliesTo(context: Context, file: File): Boolean = false

    /**
     * Analysis is about to begin for the given root project; perform any setup steps.
     *
     * A root project that is not being depended on by any other project.
     * For example, in a Gradle tree that has two app modules, and five libraries,
     * where each of the apps depend on one ore more libraries, all seven Gradle
     * modules are lint projects, and the two app modules are lint root projects.
     *
     * You typically place your analysis where you want to consult not just data
     * from a given module, but data gathered during analysis of all the dependent
     * library modules as well, in [afterCheckRootProject]. For analysis that
     * is local to a given module, just place it in [afterCheckEachProject].
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun beforeCheckRootProject(context: Context) {
        // Backwards compatibility for a while
        @Suppress("DEPRECATION")
        beforeCheckProject(context)
    }

    /**
     * Analysis is about to begin for the given project (which may be a root
     * project or a library project). Perform any setup steps.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun beforeCheckEachProject(context: Context) {
        // preserve old semantics of afterCheckLibraryProject
        if (context.project != context.mainProject) {
            @Suppress("DEPRECATION")
            beforeCheckLibraryProject(context)
        }
    }

    /**
     * Analysis has just been finished for the given root project; perform any
     * cleanup or report issues that require project-wide analysis (including
     * its dependencies).
     *
     * A root project that is not being depended on by any other project.
     * For example, in a Gradle tree that has two app modules, and five libraries,
     * where each of the apps depend on one ore more libraries, all seven Gradle
     * modules are lint projects, and the two app modules are lint root projects.
     *
     * You typically place your analysis where you want to consult not just data
     * from a given module, but data gathered during analysis of all the dependent
     * library modules as well, in [afterCheckRootProject]. For analysis that
     * is local to a given module, just place it in [afterCheckEachProject].
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun afterCheckRootProject(context: Context) {
        // Backwards compatibility for a while
        @Suppress("DEPRECATION")
        afterCheckProject(context)
    }

    /**
     * Analysis has just been finished for the given project (which may
     * be a root project or a library project); perform any
     * cleanup or report issues that require library-project-wide analysis.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun afterCheckEachProject(context: Context) {
        // preserve old semantics of afterCheckLibraryProject
        if (context.project != context.mainProject) {
            @Suppress("DEPRECATION")
            afterCheckLibraryProject(context)
        }
    }

    /**
     * Analysis is about to begin, perform any setup steps.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     * @deprecated This method is deprecated because the semantics of
     *  [beforeCheckLibraryProject] was unfortunate (it included all libraries
     *  *except* the root project, and typically you want to either act
     *  on each and every project, or just the root projects. Therefore,
     *  there is a new method, [beforeCheckEachProject], which applies to each
     *  project and [beforeCheckRootProject] which applies to just the root
     *  projects; [beforeCheckProject] has a name that sounds like
     *  [beforeCheckEachProject] but just reusing that name would have been
     *  an incompatible change.
     */
    @Deprecated(
        "If you want to override the event that each root project is about " +
            "to be analyzed, override beforeCheckRootProject; if you want to override the event " +
            "that each project (both root projects and their dependencies, override " +
            "beforeCheckEachProject",
        replaceWith = ReplaceWith("beforeCheckRootProject(context)")
    )
    open fun beforeCheckProject(context: Context) {
    }

    /**
     * Analysis has just been finished for the whole project, perform any
     * cleanup or report issues that require project-wide analysis.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     * @deprecated This method is deprecated because the semantics of
     *  [afterCheckLibraryProject] was unfortunate (it included all libraries
     *  *except* the root project, and typically you want to either act
     *  on each and every project, or just the root projects. Therefore,
     *  there is a new method, [afterCheckEachProject], which applies to each
     *  project and [afterCheckRootProject] which applies to just the root
     *  projects; [afterCheckProject] has a name that sounds like
     *  [afterCheckEachProject] but just reusing that name would have been
     *  an incompatible change.
     */
    @Deprecated(
        "If you want to override the event that each root project is about " +
            "to be analyzed, override afterCheckRootProject; if you want to override the event " +
            "that each project (both root projects and their dependencies, override " +
            "afterCheckEachProject",
        replaceWith = ReplaceWith("afterCheckRootProject(context)")
    )
    open fun afterCheckProject(context: Context) {
    }

    /**
     * Analysis is about to begin for the given library project, perform any setup steps.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     * @deprecated This method is deprecated because the semantics of
     *  [beforeCheckLibraryProject] was unfortunate (it included all libraries
     *  *except* the root project, and typically you want to either act
     *  on each and every project, or just the root projects. Therefore,
     *  there is a new method, [beforeCheckEachProject], which applies to each
     *  project and [beforeCheckRootProject] which applies to just the root
     *  projects; [beforeCheckProject] has a name that sounds like
     *  [beforeCheckEachProject] but just reusing that name would have been
     *  an incompatible change.
     */
    @Deprecated(
        "Use beforeCheckEachProject instead (which now includes the root projects too)",
        replaceWith = ReplaceWith("beforeCheckEachProject(context)")
    )
    open fun beforeCheckLibraryProject(context: Context) {
    }

    /**
     * Analysis has just been finished for the given library project, perform any
     * cleanup or report issues that require library-project-wide analysis.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     * @deprecated This method is deprecated because the semantics of
     *  [afterCheckLibraryProject] was unfortunate (it included all libraries
     *  *except* the root project, and typically you want to either act
     *  on each and every project, or just the root projects. Therefore,
     *  there is a new method, [afterCheckEachProject], which applies to each
     *  project and [afterCheckRootProject] which applies to just the root
     *  projects; [afterCheckProject] has a name that sounds like
     *  [afterCheckEachProject] but just reusing that name would have been
     *  an incompatible change.
     */
    @Deprecated(
        "Use afterCheckEachProject instead (which now includes the root projects too)",
        replaceWith = ReplaceWith("afterCheckEachProject(context)")
    )
    open fun afterCheckLibraryProject(context: Context) {
    }

    /**
     * Analysis is about to be performed on a specific file, perform any setup
     * steps.
     *
     * Note: When this method is called at the beginning of checking an XML
     * file, the context is guaranteed to be an instance of [XmlContext],
     * and similarly for a Java source file, the context will be a
     * [JavaContext] and so on.
     *
     * @param context the context for the check referencing the file to be
     * checked, the project, etc.
     */
    open fun beforeCheckFile(context: Context) {}

    /**
     * Analysis has just been finished for a specific file, perform any cleanup
     * or report issues found
     *
     *
     * Note: When this method is called at the end of checking an XML
     * file, the context is guaranteed to be an instance of [XmlContext],
     * and similarly for a Java source file, the context will be a
     * [JavaContext] and so on.
     *
     * @param context the context for the check referencing the file to be
     * checked, the project, etc.
     */
    open fun afterCheckFile(context: Context) {}

    /**
     * Returns the expected speed of this detector.
     * The issue parameter is made available for subclasses which analyze multiple issues
     * and which need to distinguish implementation cost by issue. If the detector does
     * not analyze multiple issues or does not vary in speed by issue type, just override
     * [.getSpeed] instead.
     *
     * @param issue the issue to look up the analysis speed for
     * @return the expected speed of this detector
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Slated for removal") // Slated for removal in Lint 2.0
    open fun getSpeed(issue: Issue): Speed = Speed.NORMAL

    // ---- Empty implementations to make implementing XmlScanner easier: ----

    open fun visitDocument(context: XmlContext, document: Document) {}

    open fun visitElement(context: XmlContext, element: Element) {
        // This method must be overridden if your detector returns
        // tag names from getApplicableElements
        assert(false)
    }

    open fun visitElementAfter(context: XmlContext, element: Element) {}

    open fun visitAttribute(context: XmlContext, attribute: Attr) {
        // This method must be overridden if your detector returns
        // attribute names from getApplicableAttributes
        assert(false)
    }

    // ---- Empty implementations to make implementing a ClassScanner easier: ----

    open fun checkClass(context: ClassContext, classNode: ClassNode) {}

    open fun checkCall(
        context: ClassContext,
        classNode: ClassNode,
        method: MethodNode,
        call: MethodInsnNode
    ) {
    }

    open fun checkInstruction(
        context: ClassContext,
        classNode: ClassNode,
        method: MethodNode,
        instruction: AbstractInsnNode
    ) {
    }

    // ---- Empty implementations to make implementing an GradleScanner easier: ----

    open val customVisitor: Boolean = false

    open fun visitBuildScript(context: Context) {}

    open fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any
    ) {
    }

    open fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        // Backward compatibility
        checkDslPropertyAssignment(
            context,
            property,
            value,
            parent,
            parentParent,
            valueCookie,
            statementCookie
        )
    }

    open fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    ) {
    }

    open fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    ) {
        // Backward compatibility
        checkMethodCall(context, statement, parent, namedArguments, unnamedArguments, cookie)
    }

    // ---- Empty implementations to make implementing a resource folder scanner easier: ----

    open fun checkFolder(context: ResourceContext, folderName: String) {}

    // ---- Empty implementations to make implementing a binary resource scanner easier: ----

    open fun checkBinaryResource(context: ResourceContext) {}

    open fun appliesTo(folderType: ResourceFolderType): Boolean = true

    open fun appliesToResourceRefs(): Boolean = false

    open fun applicableSuperClasses(): List<String>? = null

    open fun visitMethod(
        context: JavaContext,
        visitor: JavaElementVisitor?,
        call: PsiMethodCallExpression,
        method: PsiMethod
    ) {
    }

    open fun visitConstructor(
        context: JavaContext,
        visitor: JavaElementVisitor?,
        node: PsiNewExpression,
        constructor: PsiMethod
    ) {
    }

    open fun visitResourceReference(
        context: JavaContext,
        visitor: JavaElementVisitor?,
        node: PsiElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
    }

    open fun checkClass(context: JavaContext, declaration: PsiClass) {}

    open fun createPsiVisitor(context: JavaContext): JavaElementVisitor? = null

    open fun visitReference(
        context: JavaContext,
        visitor: JavaElementVisitor?,
        reference: PsiJavaCodeReferenceElement,
        referenced: PsiElement
    ) {
    }

    // ---- Empty implementations to make implementing UastScanner easier: ----

    open fun visitClass(context: JavaContext, declaration: UClass) {}

    open fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}

    open fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
    }

    open fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
    }

    @Deprecated("Rename to visitMethodCall instead when targeting 3.3+")
    open fun visitMethod(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
    }

    open fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Backwards compatibility
        @Suppress("DEPRECATION")
        visitMethod(context, node, method)
    }

    open fun createUastHandler(context: JavaContext): UElementHandler? = null

    open fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
    }

    open fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
    }

    open fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        // Backwards compatibility
        visitAnnotationUsage(
            context, usage, type, annotation, qualifiedName, method,
            annotations, allMemberAnnotations, allClassAnnotations, allPackageAnnotations
        )
    }

    open fun applicableAnnotations(): List<String>? = null

    open fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        // Only some annotations apply to [AnnotationUsage.COMBINED] so default to
        // turning this off to avoid false positives when this has not been explicitly
        // requested
        @Suppress("UseExpressionBody")
        return type != AnnotationUsageType.BINARY && type != AnnotationUsageType.EQUALITY
    }

    open fun inheritAnnotation(annotation: String): Boolean = true

    open fun getApplicableElements(): Collection<String>? = null

    open fun getApplicableAttributes(): Collection<String>? = null

    open fun getApplicableCallNames(): List<String>? = null

    open fun getApplicableCallOwners(): List<String>? = null

    open fun getApplicableAsmNodeTypes(): IntArray? = null

    open fun getApplicableFiles(): EnumSet<Scope> = Scope.OTHER_SCOPE

    open fun getApplicableMethodNames(): List<String>? = null

    open fun getApplicableConstructorTypes(): List<String>? = null

    open fun getApplicablePsiTypes(): List<Class<out PsiElement>>? = null

    open fun getApplicableReferenceNames(): List<String>? = null

    open fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    open fun isCallGraphRequired(): Boolean = false

    open fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {}

    /** Creates a lint fix builder. Just a convenience wrapper around [LintFix.create].  */
    protected open fun fix(): LintFix.Builder = LintFix.create()
}
