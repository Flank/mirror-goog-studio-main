/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.LintStats
import com.android.tools.lint.Reporter
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.getErrorLines
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiImportStaticReferenceElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.kotlin.KotlinUImportStatement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.io.StringWriter

fun JavaContext.checkFile(root: UFile?, task: TestLintTask) {
    root ?: error("Failure processing source ${project.getRelativePath(file)}: No UAST AST created")
    val error = PsiTreeUtil.findChildOfType(
        root.sourcePsi,
        PsiErrorElement::class.java
    )
    if (error != null) {
        val line = getLocation(error).start?.line ?: -1

        val sb = StringBuilder()
        sb.append(TestLintClient.testModePrefix(task.runner.currentTestMode))
        val location = getLocation(error)
        val source = root.sourcePsi.text.toString()
        val lines = location.getErrorLines { source }
        sb.append(
            "Found error element $error in ${file.name}:${line + 1} with text " +
                "\"${error.text}\" inside \"${error.parent.text}\"\n" +
                "$lines\n" +
                listFile(file.name, source)
        )
        error(sb.toString())
    }

    val detectors = task.issues?.asSequence()
        ?.map { it.implementation.detectorClass }
        ?.distinct()
        ?.map { it.getDeclaredConstructor().newInstance() }
        ?: task.detector?.let { sequenceOf(it) }
        ?: emptySequence()

    val applicableCalls: Set<String> = detectors
        .mapNotNull { it.getApplicableMethodNames() }
        .flatten()
        .toSet()
    val applicableReferences: Set<String> = detectors
        .mapNotNull { it.getApplicableReferenceNames() }
        .flatten()
        .toSet()

    // Check resolve issues
    root.accept(object : AbstractUastVisitor() {
        private fun ignoredImport(s: String): Boolean {
            // Allow missing R classes, since they are well
            // understood and typically not relevant to fully
            // qualified names (lint has special support for
            // recognizing unresolved R classes, used during
            // interactive editing on broken sources in the IDE)
            if (s.endsWith(".R")) {
                return true
            }

            // A lot of unit tests will have nullness annotations
            // from copy paste but lint checks generally don't
            // do anything about these
            if ((s == "androidx.annotation.NonNull") || (s == "androidx.annotation.Nullable")) {
                return true
            }

            // Kotlin synthetic classes are computed on the fly by a compiler plugin
            if (s.startsWith("kotlinx.android.synthetic.")) {
                return true
            }

            // Ignore kotlin stdlib runtime classes? This one is less clear cut.
            if (s.startsWith("kotlin.")) {
                return true
            }

            return false
        }

        override fun visitImportStatement(
            node: UImportStatement
        ): Boolean {
            val sourcePsi = node.sourcePsi
            val importReference = node.importReference
            if (node.isOnDemand || node.resolve() != null || sourcePsi == null || importReference == null) {
                return super.visitImportStatement(node)
            }

            // Static import? UAST does not correctly resolve these, so do it manually
            val importReferencePsi = importReference.sourcePsi
            if (importReferencePsi is PsiImportStaticReferenceElement) {
                val cls = importReferencePsi.classReference
                val importedClass = cls.resolve()
                if (importedClass is PsiClass) {
                    val name = importReferencePsi.referenceName
                    if (importedClass.hasStaticMemberNamed(name)) {
                        return true
                    }
                }
            } else if (importReferencePsi is PsiJavaCodeReferenceElement) {
                val fqn = importReferencePsi.qualifiedName
                val index = fqn?.lastIndexOf('.')
                if (index != null && index != -1) {
                    val className = fqn.substring(0, index)
                    val name = fqn.substring(index + 1)
                    val facade = JavaPsiFacadeEx.getInstance(importReferencePsi.project)
                    val psiClass = facade.findClass(className, GlobalSearchScope.allScope(importReferencePsi.project))
                    if (psiClass != null && psiClass.hasStaticMemberNamed(name)) {
                        return true
                    }
                }
            } else if (node is KotlinUImportStatement) {
                // Static import?
                val qualifiedExpression = importReferencePsi as? KtDotQualifiedExpression
                val clsName = qualifiedExpression?.receiverExpression?.text
                if (clsName != null) {
                    val name = qualifiedExpression.selectorExpression?.text

                    val importedClass = evaluator.findClass(clsName)
                    if (importedClass != null && importedClass.hasStaticMemberNamed(name)) {
                        return super.visitImportStatement(node)
                    }

                    // If it's a top level function, the left hand side is the package, not the class name.
                    // There are a number of possible classes that can contain that package, so search through them:
                    val facade = JavaPsiFacadeEx.getInstance(importReferencePsi.project)
                    val pkg = facade.findPackage(clsName)
                    if (pkg != null) {
                        if (pkg.classes.any { it.hasStaticMemberNamed(name) }) {
                            return super.visitImportStatement(node)
                        }
                    }
                }
            }

            val s = importReferencePsi?.text ?: importReference.asSourceString()

            if (!ignoredImport(s)) {
                val context: JavaContext = this@checkFile
                reportResolveProblem(
                    context, importReference, name = "", symbolType = "import", nameMethod = "", visitMethod = ""
                )
            }
            return super.visitImportStatement(node)
        }

        fun PsiClass.hasStaticMemberNamed(name: String?): Boolean {
            return methods.any {
                it.name == name &&
                    (
                        it.modifierList.hasModifierProperty(PsiModifier.STATIC) ||
                            it.modifierList.hasModifierProperty(PsiModifier.DEFAULT)
                        )
            } || fields.any {
                it.name == name && it.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true
            }
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName ?: node.methodIdentifier?.name
            if (name != null && applicableCalls.contains(name) && node.resolve() == null) {
                val context: JavaContext = this@checkFile
                reportResolveProblem(context, node, name, "call", "getApplicableMethodNames", "visitMethodCall")
            }
            return super.visitCallExpression(node)
        }

        override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
        ): Boolean {
            val name = node.resolvedName ?: node.identifier
            if (applicableReferences.contains(name) && node.resolve() == null) {
                val context: JavaContext = this@checkFile
                reportResolveProblem(context, node, name, "reference", "getApplicableReferenceNames", "visitReference")
            }
            return super.visitSimpleNameReferenceExpression(node)
        }

        private fun reportResolveProblem(
            context: JavaContext,
            node: UElement,
            name: String,
            symbolType: String,
            nameMethod: String,
            visitMethod: String
        ): Nothing {
            val isImport = name.isEmpty()
            val message = StringBuilder(createErrorMessage(node, "Couldn't resolve this $symbolType"))

            if (!isImport) {
                message.append(
                    """
                    The tested detector returns `$name` from `$nameMethod()`,
                    which means this reference is probably relevant to the test, but when the
                    $symbolType cannot be resolved, lint won't invoke `$visitMethod` on it.

                    """.trimIndent()
                )
            }

            message.append(
                """
                    This usually means that the unit test needs to declare a stub file or
                    placeholder with the expected signature such that type resolving works.

                    If this $symbolType is immaterial to the test, either delete it, or mark
                    this unit test as allowing resolution errors by setting
                    `allowCompilationErrors()`.

                """.trimIndent()
            )

            if (isImport) {
                message.append(
                    """

                    (This check only enforces import references, not all references, so if
                    it doesn't matter to the detector, you can just remove the import but
                    leave references to the class in the code.)

                    """.trimIndent()
                )
            }

            message.append(
                """

                    For more information, see the "Library Dependencies and Stubs" section in
                    https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:lint/docs/api-guide/unit-testing.md.html
                """.trimIndent()
            )

            if (task.runner.currentTestMode is SourceTransformationTestMode) {
                val files = context.project.dir.walk()
                    .filter { it.isFile && (it.path.endsWith(DOT_KT) || it.path.endsWith(DOT_JAVA)) }
                    .sortedBy { it.path }
                val file = node.sourcePsi?.containingFile?.virtualFile?.let(VfsUtilCore::virtualToIoFile)
                if (file != null) {
                    message.append("\n\nThis occurred when running in test mode ${task.runner.currentTestMode.fieldName}.\n")
                    message.append("The modified source files are:\n\n")
                    val line = "${"//".repeat(35)}\n"
                    for (f in files) {
                        message.append(line)
                        message.append(f.path.removePrefix(context.project.dir.path)).append(":\n")
                        message.append(line)
                        message.append(f.readText())
                    }
                }
            }

            error(message.toString())
        }

        /**
         * Given a location node and an error message, uses the text
         * reporter to create an error-string with source content and
         * range underlines to pinpoint the problem
         */
        private fun createErrorMessage(locationNode: UElement, message: String): String {
            val writer = StringWriter()
            writer.write("\n")
            val flags = LintCliFlags()
            flags.isFullPath = false
            val reporter = Reporter.createTextReporter(TestLintClient(), flags, null, writer, false)
            reporter.setWriteStats(false)
            val location = getLocation(locationNode)
            val incidents = listOf(
                Incident(IssueRegistry.LINT_ERROR, "\n" + message, location)
            )
            reporter.write(LintStats(1, 0), incidents)
            var output: String = writer.toString()
            for ((dir, desc) in task.dirToProjectDescription) {
                output = output.replace(dir.path, desc.name)
            }
            task.tempDir?.let { tempDir ->
                output = output.replace(tempDir.path, "TEST_ROOT")
            }
            return output
        }
    })
}
