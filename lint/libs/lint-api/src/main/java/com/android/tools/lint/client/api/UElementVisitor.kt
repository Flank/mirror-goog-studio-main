/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PKG
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.android.tools.lint.detector.api.interprocedural.CallGraphVisitor
import com.android.tools.lint.detector.api.interprocedural.ClassHierarchyVisitor
import com.android.tools.lint.detector.api.interprocedural.IntraproceduralDispatchReceiverVisitor
import com.google.common.base.Joiner
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Sets
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.ArrayList
import java.util.HashMap

/**
 * Specialized visitor for running detectors on a Java AST.
 * It operates in three phases:
 *
 *  1.  First, it computes a set of maps where it generates a map from each
 * significant AST attribute (such as method call names) to a list
 * of detectors to consult whenever that attribute is encountered.
 * Examples of "attributes" are method names, Android resource identifiers,
 * and general AST node types such as "cast" nodes etc. These are
 * defined on the [SourceCodeScanner] interface.
 *  1.  Second, it iterates over the document a single time, delegating to
 * the detectors found at each relevant AST attribute.
 *  1.  Finally, it calls the remaining visitors (those that need to process a
 * whole document on their own).
 *
 * It also notifies all the detectors before and after the document is processed
 * such that they can do pre- and post-processing.
 */
internal class UElementVisitor constructor(
    private val parser: UastParser,
    detectors: List<Detector>
) {

    private val methodDetectors =
        Maps.newHashMapWithExpectedSize<String, MutableList<VisitingDetector>>(120)
    private val constructorDetectors =
        Maps.newHashMapWithExpectedSize<String, MutableList<VisitingDetector>>(16)
    private val referenceDetectors =
        Maps.newHashMapWithExpectedSize<String, MutableList<VisitingDetector>>(12)
    private val resourceFieldDetectors = ArrayList<VisitingDetector>()
    private val allDetectors: MutableList<VisitingDetector>
    private val nodePsiTypeDetectors =
        Maps.newHashMapWithExpectedSize<Class<out UElement>, MutableList<VisitingDetector>>(25)
    private val superClassDetectors = HashMap<String, MutableList<VisitingDetector>>(40)
    private val annotationHandler: AnnotationHandler?
    private val callGraphDetectors = ArrayList<SourceCodeScanner>()

    init {
        allDetectors = ArrayList(detectors.size)

        var annotationScanners: Multimap<String, SourceCodeScanner>? = null

        for (detector in detectors) {
            val uastScanner = detector as SourceCodeScanner
            val v = VisitingDetector(detector, uastScanner)
            allDetectors.add(v)

            val names = detector.getApplicableMethodNames()
            if (names != null) {
                // not supported in Java visitors; adding a method invocation node is trivial
                // for that case.
                assert(names !== XmlScannerConstants.ALL)

                for (name in names) {
                    val list = methodDetectors.computeIfAbsent(name) { ArrayList(SAME_TYPE_COUNT) }
                    list.add(v)
                }
            }

            val applicableSuperClasses = detector.applicableSuperClasses()
            if (applicableSuperClasses != null) {
                for (fqn in applicableSuperClasses) {
                    val list =
                        superClassDetectors.computeIfAbsent(fqn) { ArrayList(SAME_TYPE_COUNT) }
                    list.add(v)
                }
            }

            val nodePsiTypes = detector.getApplicableUastTypes()
            if (nodePsiTypes != null) {
                for (type in nodePsiTypes) {
                    val list =
                        nodePsiTypeDetectors.computeIfAbsent(type) { ArrayList(SAME_TYPE_COUNT) }
                    list.add(v)
                }
            }

            val types = detector.getApplicableConstructorTypes()
            if (types != null) {
                // not supported in Java visitors; adding a method invocation node is trivial
                // for that case.
                assert(types !== XmlScannerConstants.ALL)
                for (type in types) {
                    var list: MutableList<VisitingDetector>? = constructorDetectors[type]
                    if (list == null) {
                        list = ArrayList(SAME_TYPE_COUNT)
                        constructorDetectors[type] = list
                    }
                    list.add(v)
                }
            }

            val referenceNames = detector.getApplicableReferenceNames()
            if (referenceNames != null) {
                // not supported in Java visitors; adding a method invocation node is trivial
                // for that case.
                assert(referenceNames !== XmlScannerConstants.ALL)

                for (name in referenceNames) {
                    val list =
                        referenceDetectors.computeIfAbsent(name) { ArrayList(SAME_TYPE_COUNT) }
                    list.add(v)
                }
            }

            if (detector.appliesToResourceRefs()) {
                resourceFieldDetectors.add(v)
            }

            val annotations = detector.applicableAnnotations()
            if (annotations != null) {
                if (annotationScanners == null) {
                    annotationScanners = ArrayListMultimap.create()
                }
                for (annotation in annotations) {
                    annotationScanners!!.put(annotation, uastScanner)
                }
            }

            if (uastScanner.isCallGraphRequired()) {
                callGraphDetectors.add(uastScanner)
            }
        }

        val relevantAnnotations: Set<String>?
        if (annotationScanners != null) {
            annotationHandler = AnnotationHandler(annotationScanners)
            relevantAnnotations = annotationHandler.relevantAnnotations
        } else {
            annotationHandler = null
            relevantAnnotations = null
        }
        parser.evaluator.setRelevantAnnotations(relevantAnnotations)
    }

    fun visitFile(context: JavaContext) {
        try {
            val uastParser = context.uastParser

            val uFile = uastParser.parse(context) ?: run {
                context.client.log(
                    Severity.WARNING, null,
                    "Lint could not build AST for ${context.file}; ignoring file"
                )
                return
            }

            // (Immediate return if null: No need to log this; the parser should be reporting
            // a full warning (such as IssueRegistry#PARSER_ERROR) with details, location, etc.)

            val client = context.client
            try {
                context.setJavaFile(uFile.psi) // needed for getLocation
                context.uastFile = uFile

                client.runReadAction(
                    Runnable {
                        for (v in allDetectors) {
                            v.setContext(context)
                            v.detector.beforeCheckFile(context)
                        }
                    }
                )

                if (!superClassDetectors.isEmpty()) {
                    client.runReadAction(
                        Runnable {
                            val visitor = SuperclassPsiVisitor(context)
                            uFile.accept(visitor)
                        }
                    )
                }

                if (!methodDetectors.isEmpty() ||
                    !resourceFieldDetectors.isEmpty() ||
                    !constructorDetectors.isEmpty() ||
                    !referenceDetectors.isEmpty() ||
                    annotationHandler != null
                ) {
                    client.runReadAction(
                        Runnable {
                            // TODO: Do we need to break this one up into finer grain locking units
                            val visitor = DelegatingPsiVisitor(context)
                            uFile.accept(visitor)
                        }
                    )
                } else {
                    // Note that the DelegatingPsiVisitor is a subclass of DispatchPsiVisitor
                    // so the above includes the below as well (through super classes)
                    if (!nodePsiTypeDetectors.isEmpty()) {
                        client.runReadAction(
                            Runnable {
                                // TODO: Do we need to break this one up into finer grain locking units
                                val visitor = DispatchPsiVisitor()
                                uFile.accept(visitor)
                            }
                        )
                    }
                }

                client.runReadAction(
                    Runnable {
                        for (v in allDetectors) {
                            ProgressManager.checkCanceled()
                            v.detector.afterCheckFile(context)
                        }
                    }
                )
            } finally {
                context.setJavaFile(null)
                context.uastFile = null
            }
        } catch (e: ProcessCanceledException) {
            // Cancelling inspections in the IDE
            throw e
        } catch (e: Throwable) {
            // Don't allow lint bugs to take down the whole build. TRY to log this as a
            // lint error instead!
            LintDriver.handleDetectorError(context, context.driver, e)
        }
    }

    fun visitGroups(
        projectContext: Context,
        allContexts: List<JavaContext>
    ) {
        if (!allContexts.isEmpty() && allDetectors.stream()
            .anyMatch { it.uastScanner.isCallGraphRequired() }
        ) {
            val callGraph = projectContext.client.runReadAction(
                Computable {
                    generateCallGraph(projectContext, parser, allContexts)
                }
            )
            if (callGraph != null && !callGraphDetectors.isEmpty()) {
                for (scanner in callGraphDetectors) {
                    projectContext.client.runReadAction(
                        Runnable {
                            ProgressManager.checkCanceled()
                            scanner.analyzeCallGraph(projectContext, callGraph)
                        }
                    )
                }
            }
        }
    }

    private fun generateCallGraph(
        projectContext: Context,
        parser: UastParser,
        contexts: List<JavaContext>
    ): CallGraphResult? {
        if (contexts.isEmpty()) {
            return null
        }

        try {
            val chaVisitor = ClassHierarchyVisitor()
            val receiverEvalVisitor =
                IntraproceduralDispatchReceiverVisitor(chaVisitor.classHierarchy)
            val callGraphVisitor = CallGraphVisitor(
                receiverEvalVisitor.receiverEval,
                chaVisitor.classHierarchy, false
            )

            for (context in contexts) {
                val uFile = parser.parse(context)
                uFile?.accept(chaVisitor)
            }
            for (context in contexts) {
                val uFile = parser.parse(context)
                uFile?.accept(receiverEvalVisitor)
            }
            for (context in contexts) {
                val uFile = parser.parse(context)
                uFile?.accept(callGraphVisitor)
            }

            val callGraph = callGraphVisitor.callGraph
            val receiverEval = receiverEvalVisitor.receiverEval
            return CallGraphResult(callGraph, receiverEval)
        } catch (oom: OutOfMemoryError) {
            val detectors = Lists.newArrayList<String>()
            for (detector in callGraphDetectors) {
                detectors.add(detector.javaClass.simpleName)
            }
            val detectorNames = "[" + Joiner.on(", ").join(detectors) + "]"
            var message = "Lint ran out of memory while building a callgraph (requested by " +
                "these detectors: " + detectorNames + "). You can either disable these " +
                "checks, or give lint more heap space."
            if (LintClient.isGradle) {
                message += " For example, to set the Gradle daemon to use 4 GB, edit " +
                    "`gradle.properties` to contains `org.gradle.jvmargs=-Xmx4g`"
            }
            projectContext.report(
                IssueRegistry.LINT_ERROR,
                Location.create(projectContext.project.dir), message
            )
            return null
        }
    }

    private class VisitingDetector(val detector: Detector, val uastScanner: SourceCodeScanner) {
        private var mVisitor: UElementHandler? = null
        private var mContext: JavaContext? = null

        val visitor: UElementHandler
            get() {
                if (mVisitor == null) {
                    mVisitor = detector.createUastHandler(mContext!!)
                    if (mVisitor == null) {
                        mVisitor = UElementHandler.NONE
                    }
                }
                return mVisitor!!
            }

        fun setContext(context: JavaContext) {
            mContext = context

            // The visitors are one-per-context, so clear them out here and construct
            // lazily only if needed
            mVisitor = null
        }
    }

    private inner class SuperclassPsiVisitor(private val context: JavaContext) :
        AbstractUastVisitor() {

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            val type = node.functionalInterfaceType
            if (type is PsiClassType) {
                val resolved = type.resolve()
                if (resolved != null) {
                    checkClass(node, null, resolved)
                }
            }

            return super.visitLambdaExpression(node)
        }

        override fun visitClass(node: UClass): Boolean {
            val result = super.visitClass(node)
            checkClass(null, node, node)
            return result
        }

        private fun checkClass(
            lambda: ULambdaExpression?,
            uClass: UClass?,
            node: PsiClass
        ) {
            ProgressManager.checkCanceled()

            if (node is PsiTypeParameter) {
                // Not included: explained in javadoc for JavaPsiScanner#checkClass
                return
            }

            var cls: PsiClass? = node
            var depth = 0
            while (cls != null) {
                var list: List<VisitingDetector>? = superClassDetectors[cls.qualifiedName]
                if (list != null) {
                    for (v in list) {
                        val uastScanner = v.uastScanner
                        if (uClass != null) {
                            uastScanner.visitClass(context, uClass)
                        } else {
                            assert(lambda != null)
                            uastScanner.visitClass(context, lambda!!)
                        }
                    }
                }

                // Check interfaces too
                val interfaceNames = getInterfaceNames(null, cls)
                if (interfaceNames != null) {
                    for (name in interfaceNames) {
                        list = superClassDetectors[name]
                        if (list != null) {
                            for (v in list) {
                                val uastScanner = v.uastScanner
                                if (uClass != null) {
                                    uastScanner.visitClass(context, uClass)
                                } else {
                                    assert(lambda != null)
                                    uastScanner.visitClass(context, lambda!!)
                                }
                            }
                        }
                    }
                }

                cls = cls.superClass
                depth++
                if (depth == 500) {
                    // Shouldn't happen in practice; this prevents the IDE from
                    // hanging if the user has accidentally typed in an incorrect
                    // super class which creates a cycle.
                    break
                }
            }
        }
    }

    private open inner class DispatchPsiVisitor : AbstractUastVisitor() {

        override fun visitAnnotation(node: UAnnotation): Boolean {
            val list = nodePsiTypeDetectors[UAnnotation::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitAnnotation(node)
                }
            }
            return super.visitAnnotation(node)
        }

        override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
            val list = nodePsiTypeDetectors[UArrayAccessExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitArrayAccessExpression(node)
                }
            }
            return super.visitArrayAccessExpression(node)
        }

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            val list = nodePsiTypeDetectors[UBinaryExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitBinaryExpression(node)
                }
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
            val list = nodePsiTypeDetectors[UBinaryExpressionWithType::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitBinaryExpressionWithType(node)
                }
            }
            return super.visitBinaryExpressionWithType(node)
        }

        override fun visitBlockExpression(node: UBlockExpression): Boolean {
            val list = nodePsiTypeDetectors[UBlockExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitBlockExpression(node)
                }
            }
            return super.visitBlockExpression(node)
        }

        override fun visitBreakExpression(node: UBreakExpression): Boolean {
            val list = nodePsiTypeDetectors[UBreakExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitBreakExpression(node)
                }
            }
            return super.visitBreakExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val list = nodePsiTypeDetectors[UCallExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitCallExpression(node)
                }
            }
            return super.visitCallExpression(node)
        }

        override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
            val list = nodePsiTypeDetectors[UCallableReferenceExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitCallableReferenceExpression(node)
                }
            }
            return super.visitCallableReferenceExpression(node)
        }

        override fun visitCatchClause(node: UCatchClause): Boolean {
            val list = nodePsiTypeDetectors[UCatchClause::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitCatchClause(node)
                }
            }
            return super.visitCatchClause(node)
        }

        override fun visitClass(node: UClass): Boolean {
            val list = nodePsiTypeDetectors[UClass::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitClass(node)
                }
            }
            return super.visitClass(node)
        }

        override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
            val list = nodePsiTypeDetectors[UClassLiteralExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitClassLiteralExpression(node)
                }
            }
            return super.visitClassLiteralExpression(node)
        }

        override fun visitContinueExpression(node: UContinueExpression): Boolean {
            val list = nodePsiTypeDetectors[UContinueExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitContinueExpression(node)
                }
            }
            return super.visitContinueExpression(node)
        }

        override fun visitDeclaration(node: UDeclaration): Boolean {
            val list = nodePsiTypeDetectors[UDeclaration::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitDeclaration(node)
                }
            }
            return super.visitDeclaration(node)
        }

        override fun visitDeclarationsExpression(node: UDeclarationsExpression): Boolean {
            val list = nodePsiTypeDetectors[UDeclarationsExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitDeclarationsExpression(node)
                }
            }
            return super.visitDeclarationsExpression(node)
        }

        override fun visitDoWhileExpression(node: UDoWhileExpression): Boolean {
            val list = nodePsiTypeDetectors[UDoWhileExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitDoWhileExpression(node)
                }
            }
            return super.visitDoWhileExpression(node)
        }

        override fun visitElement(node: UElement): Boolean {
            val list = nodePsiTypeDetectors[UElement::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitElement(node)
                }
            }
            return super.visitElement(node)
        }

        override fun visitEnumConstant(node: UEnumConstant): Boolean {
            val list = nodePsiTypeDetectors[UEnumConstant::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitEnumConstant(node)
                }
            }
            return super.visitEnumConstant(node)
        }

        override fun visitExpression(node: UExpression): Boolean {
            val list = nodePsiTypeDetectors[UExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitExpression(node)
                }
            }
            return super.visitExpression(node)
        }

        override fun visitExpressionList(node: UExpressionList): Boolean {
            val list = nodePsiTypeDetectors[UExpressionList::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitExpressionList(node)
                }
            }
            return super.visitExpressionList(node)
        }

        override fun visitField(node: UField): Boolean {
            val list = nodePsiTypeDetectors[UField::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitField(node)
                }
            }
            return super.visitField(node)
        }

        override fun visitFile(node: UFile): Boolean {
            val list = nodePsiTypeDetectors[UFile::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitFile(node)
                }
            }
            return super.visitFile(node)
        }

        override fun visitForEachExpression(node: UForEachExpression): Boolean {
            val list = nodePsiTypeDetectors[UForEachExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitForEachExpression(node)
                }
            }
            return super.visitForEachExpression(node)
        }

        override fun visitForExpression(node: UForExpression): Boolean {
            val list = nodePsiTypeDetectors[UForExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitForExpression(node)
                }
            }
            return super.visitForExpression(node)
        }

        override fun visitIfExpression(node: UIfExpression): Boolean {
            val list = nodePsiTypeDetectors[UIfExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitIfExpression(node)
                }
            }
            return super.visitIfExpression(node)
        }

        override fun visitImportStatement(node: UImportStatement): Boolean {
            val list = nodePsiTypeDetectors[UImportStatement::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitImportStatement(node)
                }
            }
            return super.visitImportStatement(node)
        }

        override fun visitInitializer(node: UClassInitializer): Boolean {
            val list = nodePsiTypeDetectors[UClassInitializer::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitInitializer(node)
                }
            }
            return super.visitInitializer(node)
        }

        override fun visitLabeledExpression(node: ULabeledExpression): Boolean {
            val list = nodePsiTypeDetectors[ULabeledExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitLabeledExpression(node)
                }
            }
            return super.visitLabeledExpression(node)
        }

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            val list = nodePsiTypeDetectors[ULambdaExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitLambdaExpression(node)
                }
            }
            return super.visitLambdaExpression(node)
        }

        override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
            val list = nodePsiTypeDetectors[ULiteralExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitLiteralExpression(node)
                }
            }
            return super.visitLiteralExpression(node)
        }

        override fun visitLocalVariable(node: ULocalVariable): Boolean {
            val list = nodePsiTypeDetectors[ULocalVariable::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitLocalVariable(node)
                }
            }
            return super.visitLocalVariable(node)
        }

        override fun visitMethod(node: UMethod): Boolean {
            val list = nodePsiTypeDetectors[UMethod::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitMethod(node)
                }
            }
            return super.visitMethod(node)
        }

        override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
            val list = nodePsiTypeDetectors[UObjectLiteralExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitObjectLiteralExpression(node)
                }
            }
            return super.visitObjectLiteralExpression(node)
        }

        override fun visitParameter(node: UParameter): Boolean {
            val list = nodePsiTypeDetectors[UParameter::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitParameter(node)
                }
            }
            return super.visitParameter(node)
        }

        override fun visitParenthesizedExpression(node: UParenthesizedExpression): Boolean {
            val list = nodePsiTypeDetectors[UParenthesizedExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitParenthesizedExpression(node)
                }
            }
            return super.visitParenthesizedExpression(node)
        }

        override fun visitPolyadicExpression(node: UPolyadicExpression): Boolean {
            val list = nodePsiTypeDetectors[UPolyadicExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitPolyadicExpression(node)
                }
            }
            return super.visitPolyadicExpression(node)
        }

        override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
            val list = nodePsiTypeDetectors[UPostfixExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitPostfixExpression(node)
                }
            }
            return super.visitPostfixExpression(node)
        }

        override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
            val list = nodePsiTypeDetectors[UPrefixExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitPrefixExpression(node)
                }
            }
            return super.visitPrefixExpression(node)
        }

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
            val list = nodePsiTypeDetectors[UQualifiedReferenceExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitQualifiedReferenceExpression(node)
                }
            }
            return super.visitQualifiedReferenceExpression(node)
        }

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val list = nodePsiTypeDetectors[UReturnExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitReturnExpression(node)
                }
            }
            return super.visitReturnExpression(node)
        }

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            val list = nodePsiTypeDetectors[USimpleNameReferenceExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitSimpleNameReferenceExpression(node)
                }
            }
            return super.visitSimpleNameReferenceExpression(node)
        }

        override fun visitSuperExpression(node: USuperExpression): Boolean {
            val list = nodePsiTypeDetectors[USuperExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitSuperExpression(node)
                }
            }
            return super.visitSuperExpression(node)
        }

        override fun visitSwitchClauseExpression(node: USwitchClauseExpression): Boolean {
            val list = nodePsiTypeDetectors[USwitchClauseExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitSwitchClauseExpression(node)
                }
            }
            return super.visitSwitchClauseExpression(node)
        }

        override fun visitSwitchExpression(node: USwitchExpression): Boolean {
            val list = nodePsiTypeDetectors[USwitchExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitSwitchExpression(node)
                }
            }
            return super.visitSwitchExpression(node)
        }

        override fun visitThisExpression(node: UThisExpression): Boolean {
            val list = nodePsiTypeDetectors[UThisExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitThisExpression(node)
                }
            }
            return super.visitThisExpression(node)
        }

        override fun visitThrowExpression(node: UThrowExpression): Boolean {
            val list = nodePsiTypeDetectors[UThrowExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitThrowExpression(node)
                }
            }
            return super.visitThrowExpression(node)
        }

        override fun visitTryExpression(node: UTryExpression): Boolean {
            val list = nodePsiTypeDetectors[UTryExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitTryExpression(node)
                }
            }
            return super.visitTryExpression(node)
        }

        override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
            val list = nodePsiTypeDetectors[UTypeReferenceExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitTypeReferenceExpression(node)
                }
            }
            return super.visitTypeReferenceExpression(node)
        }

        override fun visitUnaryExpression(node: UUnaryExpression): Boolean {
            val list = nodePsiTypeDetectors[UUnaryExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitUnaryExpression(node)
                }
            }
            return super.visitUnaryExpression(node)
        }

        override fun visitVariable(node: UVariable): Boolean {
            val list = nodePsiTypeDetectors[UVariable::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitVariable(node)
                }
            }
            return super.visitVariable(node)
        }

        override fun visitWhileExpression(node: UWhileExpression): Boolean {
            val list = nodePsiTypeDetectors[UWhileExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitWhileExpression(node)
                }
            }
            return super.visitWhileExpression(node)
        }

        override fun visitYieldExpression(node: UYieldExpression): Boolean {
            val list = nodePsiTypeDetectors[UYieldExpression::class.java]
            if (list != null) {
                for (v in list) {
                    v.visitor.visitYieldExpression(node)
                }
            }
            return super.visitYieldExpression(node)
        }
    }

    /** Performs common AST searches for method calls and R-type-field references.
     * Note that this is a specialized form of the [DispatchPsiVisitor].  */
    private inner class DelegatingPsiVisitor constructor(private val mContext: JavaContext) :
        DispatchPsiVisitor() {
        private val mVisitResources: Boolean = !resourceFieldDetectors.isEmpty()
        private val mVisitMethods: Boolean = !methodDetectors.isEmpty()
        private val mVisitConstructors: Boolean = !constructorDetectors.isEmpty()
        private val mVisitReferences: Boolean = !referenceDetectors.isEmpty()

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            if (mVisitReferences || mVisitResources) {
                ProgressManager.checkCanceled()
            }

            if (mVisitReferences) {
                val list = referenceDetectors[node.identifier]
                if (list != null) {
                    val referenced = node.resolve()
                    if (referenced != null) {
                        for (v in list) {
                            val uastScanner = v.uastScanner
                            uastScanner.visitReference(mContext, node, referenced)
                        }
                    }
                }
            }

            if (mVisitResources) {
                val reference = ResourceReference.get(node)
                if (reference != null) {
                    for (v in resourceFieldDetectors) {
                        val uastScanner = v.uastScanner
                        uastScanner.visitResourceReference(
                            mContext,
                            reference.node,
                            reference.type,
                            reference.name,
                            reference.`package` == ANDROID_PKG
                        )
                    }
                }
            }

            annotationHandler?.visitSimpleNameReferenceExpression(mContext, node)

            return super.visitSimpleNameReferenceExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val result = super.visitCallExpression(node)

            ProgressManager.checkCanceled()

            if (node.isMethodCall()) {
                visitMethodCallExpression(node)
            } else if (node.isConstructorCall()) {
                visitNewExpression(node)
            }

            annotationHandler?.visitCallExpression(mContext, node)

            return result
        }

        private fun visitMethodCallExpression(node: UCallExpression) {
            if (mVisitMethods) {
                val methodName = node.methodName ?: node.methodIdentifier?.name
                if (methodName != null) {
                    val list = methodDetectors[methodName]
                    if (list != null) {
                        val function = node.resolve()
                        if (function != null) {
                            for (v in list) {
                                val scanner = v.uastScanner
                                scanner.visitMethodCall(mContext, node, function)
                            }
                        }
                    }
                }
            }
        }

        private fun visitNewExpression(node: UCallExpression) {
            if (mVisitConstructors) {
                val method = node.resolve() ?: return

                val resolvedClass = method.containingClass
                if (resolvedClass != null) {
                    val list = constructorDetectors[resolvedClass.qualifiedName]
                    if (list != null) {
                        for (v in list) {
                            val javaPsiScanner = v.uastScanner
                            javaPsiScanner.visitConstructor(mContext, node, method)
                        }
                    }
                }
            }
        }

        // Annotations

        // (visitCallExpression handled above)

        override fun visitMethod(node: UMethod): Boolean {
            annotationHandler?.visitMethod(mContext, node)
            return super.visitMethod(node)
        }

        override fun visitAnnotation(node: UAnnotation): Boolean {
            annotationHandler?.visitAnnotation(mContext, node)

            return super.visitAnnotation(node)
        }

        override fun visitEnumConstant(node: UEnumConstant): Boolean {
            annotationHandler?.visitEnumConstant(mContext, node)
            return super.visitEnumConstant(node)
        }

        override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
            annotationHandler?.visitArrayAccessExpression(mContext, node)

            return super.visitArrayAccessExpression(node)
        }

        override fun visitVariable(node: UVariable): Boolean {
            annotationHandler?.visitVariable(mContext, node)

            return super.visitVariable(node)
        }

        override fun visitClass(node: UClass): Boolean {
            annotationHandler?.visitClass(mContext, node)

            return super.visitClass(node)
        }
    }

    companion object {
        /** Default size of lists holding detectors of the same type for a given node type  */
        private const val SAME_TYPE_COUNT = 8

        private fun getInterfaceNames(
            addTo: MutableSet<String>?,
            cls: PsiClass
        ): Set<String>? {
            var target = addTo
            for (resolvedInterface in cls.interfaces) {
                val name = resolvedInterface.qualifiedName ?: continue
                if (target == null) {
                    target = Sets.newHashSet()
                } else if (target.contains(name)) {
                    // Superclasses can explicitly implement the same interface,
                    // so keep track of visited interfaces as we traverse up the
                    // super class chain to avoid checking the same interface
                    // more than once.
                    continue
                }
                target!!.add(name)
                getInterfaceNames(target, resolvedInterface)
            }

            return target
        }
    }
}
