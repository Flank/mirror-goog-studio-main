/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.SupportAnnotationDetector.UI_THREAD_ANNOTATION
import com.android.tools.lint.checks.SupportAnnotationDetector.WORKER_THREAD_ANNOTATION
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.interprocedural.CallGraph
import com.android.tools.lint.detector.api.interprocedural.CallGraphVisitor
import com.android.tools.lint.detector.api.interprocedural.CallReceiverEvaluator
import com.android.tools.lint.detector.api.interprocedural.CallTarget
import com.android.tools.lint.detector.api.interprocedural.ClassHierarchyVisitor
import com.android.tools.lint.detector.api.interprocedural.IntraproceduralReceiverVisitor
import com.android.tools.lint.detector.api.interprocedural.ParamContext
import com.android.tools.lint.detector.api.interprocedural.Receiver
import com.android.tools.lint.detector.api.interprocedural.SearchNode
import com.android.tools.lint.detector.api.interprocedural.buildAllReachableSearchNodes
import com.android.tools.lint.detector.api.interprocedural.searchForPathsFromSearchNodes
import com.android.tools.lint.detector.api.interprocedural.shortName
import com.android.tools.lint.detector.api.interprocedural.toTarget
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getContainingFile
import java.util.EnumSet
import java.util.HashMap

data class AnnotatedCallPath(
        val searchNodes: List<SearchNode>,
        val sourceAnnotation: String,
        val sinkAnnotation: String)

/** Returns a collection of call paths that violate thread annotations found in source code. */
fun searchForInterproceduralThreadAnnotationViolations(
        callGraph: CallGraph,
        receiverEval: CallReceiverEvaluator): Collection<AnnotatedCallPath> {

    fun PsiModifierListOwner.isAnnotatedWith(annotation: String) =
            AnnotationUtil.isAnnotated(
                    this, annotation,
                    /*inHierarchy*/ true, /*skipExternal*/ false)

    fun CallTarget.isAnnotatedWith(annotation: String) = when (this) {
        is CallTarget.Method -> {
            element.isAnnotatedWith(annotation) ||
                    element.containingClass?.isAnnotatedWith(annotation) ?: false
        }
        is CallTarget.Lambda -> element.annotations.any { it.qualifiedName == annotation }
        is CallTarget.DefaultConstructor -> element.isAnnotatedWith(annotation)
    }

    val allSearchNodes = callGraph.buildAllReachableSearchNodes(receiverEval)
    val uiSearchNodes = allSearchNodes.filter {
        it.node.caller.isAnnotatedWith(SupportAnnotationDetector.UI_THREAD_ANNOTATION)
    }
    val workerSearchNodes = allSearchNodes.filter {
        it.node.caller.isAnnotatedWith(SupportAnnotationDetector.WORKER_THREAD_ANNOTATION)
    }

    // Some methods take in a lambda (say) and run it on a different thread.
    // By default our analysis would see that there is no direct call through the parameter,
    // and correctly end the corresponding call path.
    // But we would also like to check that the parameter is able to run on the new thread.
    // To do this we find each parameter with a thread annotation, and treat all contextual
    // receivers for that parameters as if they had the thread annotation directly.
    fun paramSearchNodes(annotation: String) = allSearchNodes
            .flatMap { searchNode ->
                searchNode.paramContext.params
                        .filter { (param, _) -> param.psi.isAnnotatedWith(annotation) }
                        .map { it.second } // Pulls out receivers.
            }
            .mapNotNull { receiver ->
                val target = when (receiver) {
                    is Receiver.Class -> null
                    is Receiver.Lambda -> receiver.toTarget()
                    is Receiver.CallableReference -> receiver.toTarget()
                }
                // We use an empty parameter context for the lambda (say) that will be invoked on
                // the new thread, as we don't know what arguments will be used when invoked later.
                target?.let {
                    SearchNode(
                            callGraph.getNode(it.element),
                            ParamContext.EMPTY,
                            cause = it.element)
                }
            }

    val allUiSearchNodes = uiSearchNodes + paramSearchNodes(UI_THREAD_ANNOTATION)
    val allWorkerSearchNodes = workerSearchNodes + paramSearchNodes(WORKER_THREAD_ANNOTATION)
    val uiPaths = callGraph.searchForPathsFromSearchNodes(
            allUiSearchNodes,
            allWorkerSearchNodes,
            receiverEval)
            .map { AnnotatedCallPath(it, UI_THREAD_ANNOTATION, WORKER_THREAD_ANNOTATION) }
    val workerPaths = callGraph.searchForPathsFromSearchNodes(
            allWorkerSearchNodes,
            allUiSearchNodes,
            receiverEval)
            .map { AnnotatedCallPath(it, WORKER_THREAD_ANNOTATION, UI_THREAD_ANNOTATION) }

    return uiPaths + workerPaths
}

class WrongThreadInterproceduralDetector : Detector(), Detector.UastScanner {
    private val chaVisitor = ClassHierarchyVisitor()
    private val receiverEval = IntraproceduralReceiverVisitor(chaVisitor.classHierarchy)
    private val callGraphVisitor = CallGraphVisitor(receiverEval, chaVisitor.classHierarchy)
    private val fileContexts = HashMap<UFile, JavaContext>()
    private var phase = State.BuildingClassHierarchy

    enum class State { BuildingClassHierarchy, EvaluatingReceivers, BuildingCallGraph }

    override fun getApplicableUastTypes() = listOf(UFile::class.java)

    override fun beforeCheckFile(context: Context) {
        if (context is JavaContext) {
            context.uastFile?.let { fileContexts[it] = context }
        }
        super.beforeCheckFile(context)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitFile(uFile: UFile) {
                    when (phase) {
                        State.BuildingClassHierarchy -> uFile.accept(chaVisitor)
                        State.EvaluatingReceivers -> uFile.accept(receiverEval)
                        State.BuildingCallGraph -> uFile.accept(callGraphVisitor)
                    }
                }
            }

    /** Advance the analysis phase, returning false when there are no more phase changes left. */
    private fun advanceState(): Boolean {
        when (phase) {
            State.BuildingClassHierarchy -> phase = State.EvaluatingReceivers
            State.EvaluatingReceivers -> phase = State.BuildingCallGraph
            State.BuildingCallGraph -> return false
        }
        return true
    }

    override fun afterCheckProject(context: Context) {
        if (advanceState()) {
            context.driver.requestRepeat(this, SCOPE)
            return
        }
        val badPaths = searchForInterproceduralThreadAnnotationViolations(
                callGraphVisitor.callGraph,
                receiverEval)
        for ((searchNodes, sourceAnnotation, sinkAnnotation) in badPaths) {
            if (searchNodes.size == 1) {
                // This means that a node in the graph was annotated with both UiThread and
                // WorkerThread. This can happen if an overriding method changes the annotation.
                continue
            }
            val (_, second) = searchNodes
            val pathBeginning = second.cause
            val containingFile = pathBeginning.getContainingFile() ?: continue
            val javaContext = fileContexts[containingFile] ?: continue
            javaContext.setJavaFile(containingFile.psi) // Needed for getLocation.
            val location = javaContext.getLocation(pathBeginning)
            val pathStr = searchNodes.joinToString(separator = " -> ") { it.node.shortName }
            val sourceStr = sourceAnnotation.substringAfterLast('.')
            val sinkStr = sinkAnnotation.substringAfterLast('.')
            val message = "Interprocedural thread annotation violation " +
                    "($sourceStr to $sinkStr):\n$pathStr"
            context.report(ISSUE, location, message, null)
        }
    }

    companion object {
        val SCOPE: EnumSet<Scope> = EnumSet.of(Scope.ALL_JAVA_FILES)
        val ISSUE = Issue.create(
                "WrongThreadInterprocedural",
                "Wrong Thread (Interprocedural)",
                "Searches for interprocedural call paths that violate thread annotations in the " +
                        "program. Tracks the flow of instantiated types and lambda expressions " +
                        "to increase accuracy across method boundaries.",
                Category.CORRECTNESS,
                /*priority*/ 6,
                Severity.ERROR,
                Implementation(WrongThreadInterproceduralDetector::class.java, SCOPE))
                .addMoreInfo("http://developer.android.com/guide/components/" +
                        "processes-and-threads.html#Threads")
                .setEnabledByDefault(false)
    }
}