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
import com.android.tools.lint.client.api.JavaParser.ResolvedClass
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod
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
import com.intellij.psi.PsiTypeParameter
import lombok.ast.AstVisitor
import lombok.ast.ClassDeclaration
import lombok.ast.ConstructorInvocation
import lombok.ast.MethodInvocation
import lombok.ast.Node
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
     * Specialized interface for detectors that scan Java source file parse trees
     */
    @Deprecated("Use {@link UastScanner} instead", level = DeprecationLevel.WARNING,
            replaceWith = ReplaceWith("com.android.tools.lint.detector.api.SourceCodeScanner"))
    // Use UastScanner instead. Still here for third-party rules
    interface JavaScanner {

        /**
         * Return the types of AST nodes that the visitor returned from
         * [.createJavaVisitor] should visit. See the
         * documentation for [.createJavaVisitor] for details
         * on how the shared visitor is used.
         *
         *
         * If you return null from this method, then the visitor will process
         * the full tree instead.
         *
         *
         * Note that for the shared visitor, the return codes from the visit
         * methods are ignored: returning true will **not** prune iteration
         * of the subtree, since there may be other node types interested in the
         * children. If you need to ensure that your visitor only processes a
         * part of the tree, use a full visitor instead. See the
         * OverdrawDetector implementation for an example of this.
         *
         * @return the list of applicable node types (AST node classes), or null
         */
        fun getApplicableNodeTypes(): List<Class<out Node>>?

        /**
         * Return the list of method names this detector is interested in, or
         * null. If this method returns non-null, then any AST nodes that match
         * a method call in the list will be passed to the
         * [.visitMethod]
         * method for processing. The visitor created by
         * [.createJavaVisitor] is also passed to that
         * method, although it can be null.
         *
         *
         * This makes it easy to write detectors that focus on some fixed calls.
         * For example, the StringFormatDetector uses this mechanism to look for
         * "format" calls, and when found it looks around (using the AST's
         * [Node.getParent] method) to see if it's called on
         * a String class instance, and if so do its normal processing. Note
         * that since it doesn't need to do any other AST processing, that
         * detector does not actually supply a visitor.
         *
         * @return a set of applicable method names, or null.
         */
        fun getApplicableMethodNames(): List<String>?

        /**
         * Return the list of constructor types this detector is interested in, or
         * null. If this method returns non-null, then any AST nodes that match
         * a constructor call in the list will be passed to the
         * [.visitConstructor]
         * method for processing. The visitor created by
         * [.createJavaVisitor] is also passed to that
         * method, although it can be null.
         *
         *
         * This makes it easy to write detectors that focus on some fixed constructors.
         *
         * @return a set of applicable fully qualified types, or null.
         */
        fun getApplicableConstructorTypes(): List<String>?

        /**
         * Create a parse tree visitor to process the parse tree. All
         * [JavaScanner] detectors must provide a visitor, unless they
         * either return true from [.appliesToResourceRefs] or return
         * non null from [.getApplicableMethodNames].
         *
         *
         * If you return specific AST node types from
         * [.getApplicableNodeTypes], then the visitor will **only**
         * be called for the specific requested node types. This is more
         * efficient, since it allows many detectors that apply to only a small
         * part of the AST (such as method call nodes) to share iteration of the
         * majority of the parse tree.
         *
         *
         * If you return null from [.getApplicableNodeTypes], then your
         * visitor will be called from the top and all node types visited.
         *
         *
         * Note that a new visitor is created for each separate compilation
         * unit, so you can store per file state in the visitor.
         *
         * @param context the [Context] for the file being analyzed
         * @return a visitor, or null.
         */
        fun createJavaVisitor(context: JavaContext): AstVisitor?

        /**
         * Method invoked for any method calls found that matches any names
         * returned by [.getApplicableMethodNames]. This also passes
         * back the visitor that was created by
         * [.createJavaVisitor], but a visitor is not
         * required. It is intended for detectors that need to do additional AST
         * processing, but also want the convenience of not having to look for
         * method names on their own.
         *
         * @param context the context of the lint request
         * @param visitor the visitor created from
         * [.createJavaVisitor], or null
         * @param node the [MethodInvocation] node for the invoked method
         */
        fun visitMethod(
                context: JavaContext,
                visitor: AstVisitor?,
                node: MethodInvocation)

        /**
         * Method invoked for any constructor calls found that matches any names
         * returned by [.getApplicableConstructorTypes]. This also passes
         * back the visitor that was created by
         * [.createJavaVisitor], but a visitor is not
         * required. It is intended for detectors that need to do additional AST
         * processing, but also want the convenience of not having to look for
         * method names on their own.
         *
         * @param context the context of the lint request
         * @param visitor the visitor created from
         * [.createJavaVisitor], or null
         * @param node the [ConstructorInvocation] node for the invoked method
         * @param constructor the resolved constructor method with type information
         */
        fun visitConstructor(
                context: JavaContext,
                visitor: AstVisitor?,
                node: ConstructorInvocation,
                constructor: ResolvedMethod)

        /**
         * Returns whether this detector cares about Android resource references
         * (such as `R.layout.main` or `R.string.app_name`). If it
         * does, then the visitor will look for these patterns, and if found, it
         * will invoke [.visitResourceReference] passing the resource type
         * and resource name. It also passes the visitor, if any, that was
         * created by [.createJavaVisitor], such that a
         * detector can do more than just look for resources.
         *
         * @return true if this detector wants to be notified of R resource
         * identifiers found in the code.
         */
        fun appliesToResourceRefs(): Boolean

        /**
         * Called for any resource references (such as `R.layout.main`
         * found in Java code, provided this detector returned `true` from
         * [.appliesToResourceRefs].
         *
         * @param context the lint scanning context
         * @param visitor the visitor created from
         * [.createJavaVisitor], or null
         * @param node the variable reference for the resource
         * @param type the resource type, such as "layout" or "string"
         * @param name the resource name, such as "main" from
         * `R.layout.main`
         * @param isFramework whether the resource is a framework resource
         * (android.R) or a local project resource (R)
         */
        fun visitResourceReference(
                context: JavaContext,
                visitor: AstVisitor?,
                node: Node,
                type: String,
                name: String,
                isFramework: Boolean)

        /**
         * Returns a list of fully qualified names for super classes that this
         * detector cares about. If not null, this detector will be called if
         * the current class is a subclass of one of the specified
         * superclasses.
         *
         * @return a list of fully qualified names
         */
        fun applicableSuperClasses(): List<String>?

        /**
         * Called for each class that extends one of the super classes specified with
         * [.applicableSuperClasses]
         *
         * @param context the lint scanning context
         * @param declaration the class declaration node, or null for anonymous classes
         * @param node the class declaration node or the anonymous class construction node
         * @param resolvedClass the resolved class
         */
        // TODO: Change signature to pass in the NormalTypeBody instead of the plain Node?
        fun checkClass(context: JavaContext, declaration: ClassDeclaration?,
                node: Node, resolvedClass: ResolvedClass)
    }

    /**
    Interface to be implemented by lint detectors that want to analyze
    Java source files.
    <p>
    The Lint Java API sits on top of IntelliJ IDEA's "PSI" API, an API
    which exposes the underlying abstract syntax tree as well as providing
    core services like resolving symbols.
    <p>
    This new API replaces the older Lombok AST API that was used for Java
    source checks. Migrating a check from the Lombok APIs to the new PSI
    based APIs is relatively straightforward.
    <p>
    First, replace "implements JavaScanner" with "implements
    JavaPsiScanner" in your detector signature, and then locate all the
    JavaScanner methods your detector was overriding and replace them with
    the new corresponding signatures.
    <p>
    For example, replace
    <pre>
    {@code List<Class<? extends Node>> getApplicableNodeTypes();}
    </pre>
    with
    <pre>
    {@code List<Class<? extends PsiElement>> getApplicablePsiTypes();}
    </pre>
    and replace
    <pre>
    void visitMethod(
    &#064;NonNull JavaContext context,
    &#064;Nullable AstVisitor visitor,
    &#064;NonNull MethodInvocation node);
    </pre>
    with
    <pre>
    void visitMethod(
    &#064;NonNull JavaContext context,
    &#064;Nullable JavaElementVisitor visitor,
    &#064;NonNull PsiMethodCallExpression call,
    &#064;NonNull PsiMethod method);
    </pre>
    and so on.
    <p>
    Finally, replace the various Lombok iteration code with PSI based
    code. Both Lombok and PSI used class names that closely resemble the
    Java language specification, so guessing the corresponding names is
    straightforward; here are some examples:
    <pre>
    ClassDeclaration ⇒ PsiClass
    MethodDeclaration ⇒ PsiMethod
    MethodInvocation ⇒ PsiMethodCallExpression
    ConstructorInvocation ⇒ PsiNewExpression
    If ⇒ PsiIfStatement
    For ⇒ PsiForStatement
    Continue ⇒ PsiContinueStatement
    StringLiteral ⇒ PsiLiteral
    IntegralLiteral ⇒ PsiLiteral
    ... etc
    </pre>
    Lombok AST had no support for symbol and type resolution, so lint
    added its own separate API to support (the "ResolvedNode"
    hierarchy). This is no longer needed since PSI supports it directly
    (for example, on a PsiMethodCallExpression you just call
    "resolveMethod" to get the PsiMethod the method calls, and on a
    PsiExpression you just call getType() to get the corresponding
    <p>
    The old ResolvedNode interface was written for lint so it made certain
    kinds of common checks very easy. To help make porting lint rules from
    the old API easier, and to make writing future lint checks easier
    too), there is a new helper class, "JavaEvaluator" (which you can
    obtain from JavaContext). This lets you for example quickly check
    whether a given method is a member of a subclass of a given class, or
    whether a method has a certain set of parameters, etc. It also makes
    it easy to check whether a given method is private, abstract or
    static, and so on. (And most of its parameters are nullable which
    makes it simpler to use; you don't have to null check resolve results
    before calling into it.)
    <p>
    Some further porting tips:
    <ul>
    <li> Make sure you don't call toString() on nodes to get their
    contents. In Lombok, toString returned the underlying source
    text. In PSI, call getText() instead, since toString() is meant for
    debugging and includes node types etc.

    <li> ResolvedClass#getName() used to return *qualified* name. In PSI,
    PsiClass#getName() returns just the simple name, so call
    #getQualifiedName() instead if that's what your code needs! Node
    also that PsiClassType#getClassName() returns the simple name; if
    you want the fully qualified name, call PsiType#getCanonicalText().

    <li> Lombok didn't distinguish between a local variable declaration, a
    parameter and a field declaration. These are all different in PSI,
    so when writing visitors, make sure you replace a single
    visitVariableDeclaration with visitField, visitLocalVariable and
    visitParameter methods as applicable.

    <li> Note that when lint runs in the IDE, there may be extra PSI nodes in
    the hierarchy representing whitespace as well as parentheses. Watch
    out for this when calling getParent, getPrevSibling or
    getNextSibling - don't just go one level up and check instanceof
    {@code <something>}; instead, use LintUtils.skipParentheses (or the
    corresponding methods to skip whitespace left and right.)  Note that
    when you write lint unit tests, the infrastructure will run your
    tests twice, one with a normal AST and once where it has inserted
    whitespace and parentheses everywhere, and it asserts that you come
    up with the same analysis results. (This caught 16 failing tests
    across 7 different detectors.)

    <li> Annotation handling is a bit different. In ResolvedAnnotations I had
    (for convenience) inlined things like annotations on the class; you
    now have to resolve the annotation name reference to the
    corresponding annotation class and look there.
    </ul>

    Some additional conversion examples: replace
    <pre>
    &#064;Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
    &#064;NonNull MethodInvocation node) {
    ResolvedNode resolved = context.resolve(node);
    if (resolved instanceof ResolvedMethod) {
    ResolvedMethod method = (ResolvedMethod) resolved;
    if (method.getContainingClass().matches("android.os.Parcel")) {
    ...
    </pre>
    with
    <pre>
    &#064;Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
    &#064;NonNull PsiCall node) {
    if (method != null &amp;&amp; method.getContainingClass() != null &amp;&amp;
    "android.os.Parcel".equals(method.getContainingClass().getQualifiedName())) {
    ....
    </pre>

    Similarly:
    <pre>
    if (method.getArgumentCount() != 2
    || !method.getArgumentType(0).matchesName(TYPE_OBJECT)
    || !method.getArgumentType(1).matchesName(TYPE_STRING)) {
    return;
    }
    </pre>
    can now be written as
    <pre>
    JavaEvaluator resolver = context.getEvaluator();
    if (!resolver.methodMatches(method, WEB_VIEW, true, TYPE_OBJECT, TYPE_STRING)) {
    return;
    }
    </pre>
    Finally, note that many deprecated methods in lint itself point to the replacement
    methods, see for example {@link JavaContext#findSurroundingMethod(Node)}.

    <p>
    Misc notes:
    If you have a constructor call to an implicit constructor, with the new PSI
    backend it will resolve to null. It doesn't return synthetic methods the way we
    did with the ECJ backend.
    </p>

    @deprecated Use {@link UastScanner} instead
     */
    @Deprecated("Use {@link UastScanner} instead", level = DeprecationLevel.WARNING,
            replaceWith = ReplaceWith("com.android.tools.lint.detector.api.SourceCodeScanner"))
    // Use UastScanner instead. Still here for third-party rules
    interface JavaPsiScanner {

        /**
         * Return the types of AST nodes that the visitor returned from
         * [.createJavaVisitor] should visit. See the
         * documentation for [.createJavaVisitor] for details
         * on how the shared visitor is used.
         *
         *
         * If you return null from this method, then the visitor will process
         * the full tree instead.
         *
         *
         * Note that for the shared visitor, the return codes from the visit
         * methods are ignored: returning true will **not** prune iteration
         * of the subtree, since there may be other node types interested in the
         * children. If you need to ensure that your visitor only processes a
         * part of the tree, use a full visitor instead. See the
         * OverdrawDetector implementation for an example of this.
         *
         * @return the list of applicable node types (AST node classes), or null
         */
        fun getApplicablePsiTypes(): List<Class<out PsiElement>>?

        /**
         * Return the list of method names this detector is interested in, or
         * null. If this method returns non-null, then any AST nodes that match
         * a method call in the list will be passed to the
         * [.visitMethod]
         * method for processing. The visitor created by
         * [.createPsiVisitor] is also passed to that
         * method, although it can be null.
         *
         *
         * This makes it easy to write detectors that focus on some fixed calls.
         * For example, the StringFormatDetector uses this mechanism to look for
         * "format" calls, and when found it looks around (using the AST's
         * [PsiElement.getParent] method) to see if it's called on
         * a String class instance, and if so do its normal processing. Note
         * that since it doesn't need to do any other AST processing, that
         * detector does not actually supply a visitor.
         *
         * @return a set of applicable method names, or null.
         */
        fun getApplicableMethodNames(): List<String>?

        /**
         * Return the list of constructor types this detector is interested in, or
         * null. If this method returns non-null, then any AST nodes that match
         * a constructor call in the list will be passed to the
         * [.visitConstructor]
         * method for processing. The visitor created by
         * [.createJavaVisitor] is also passed to that
         * method, although it can be null.
         *
         *
         * This makes it easy to write detectors that focus on some fixed constructors.
         *
         * @return a set of applicable fully qualified types, or null.
         */
        fun getApplicableConstructorTypes(): List<String>?

        /**
         * Return the list of reference names types this detector is interested in, or null. If this
         * method returns non-null, then any AST elements that match a reference in the list will be
         * passed to the [.visitReference] method for processing. The visitor created by
         * [.createJavaVisitor] is also passed to that method, although it can be
         * null.
         *
         * This makes it easy to write detectors that focus on some fixed references.
         *
         * @return a set of applicable reference names, or null.
         */
        fun getApplicableReferenceNames(): List<String>?

        /**
         * Create a parse tree visitor to process the parse tree. All
         * [JavaPsiScanner] detectors must provide a visitor, unless they
         * either return true from [.appliesToResourceRefs] or return
         * non null from [.getApplicableMethodNames].
         *
         *
         * If you return specific AST node types from
         * [.getApplicablePsiTypes], then the visitor will **only**
         * be called for the specific requested node types. This is more
         * efficient, since it allows many detectors that apply to only a small
         * part of the AST (such as method call nodes) to share iteration of the
         * majority of the parse tree.
         *
         *
         * If you return null from [.getApplicablePsiTypes], then your
         * visitor will be called from the top and all node types visited.
         *
         *
         * Note that a new visitor is created for each separate compilation
         * unit, so you can store per file state in the visitor.
         *
         *
         * **
         * NOTE: Your visitor should **NOT** extend JavaRecursiveElementVisitor.
         * Your visitor should only visit the current node type; the infrastructure
         * will do the recursion. (Lint's unit test infrastructure will check and
         * enforce this restriction.)
         ** *
         *
         * @param context the [Context] for the file being analyzed
         * @return a visitor, or null.
         */
        fun createPsiVisitor(context: JavaContext): JavaElementVisitor?

        /**
         * Method invoked for any method calls found that matches any names
         * returned by [.getApplicableMethodNames]. This also passes
         * back the visitor that was created by
         * [.createJavaVisitor], but a visitor is not
         * required. It is intended for detectors that need to do additional AST
         * processing, but also want the convenience of not having to look for
         * method names on their own.
         *
         * @param context the context of the lint request
         * @param visitor the visitor created from
         * [.createPsiVisitor], or null
         * @param call the [PsiMethodCallExpression] node for the invoked method
         * @param method the [PsiMethod] being called
         */
        fun visitMethod(
                context: JavaContext,
                visitor: JavaElementVisitor?,
                call: PsiMethodCallExpression,
                method: PsiMethod)

        /**
         * Method invoked for any constructor calls found that matches any names
         * returned by [.getApplicableConstructorTypes]. This also passes
         * back the visitor that was created by
         * [.createPsiVisitor], but a visitor is not
         * required. It is intended for detectors that need to do additional AST
         * processing, but also want the convenience of not having to look for
         * method names on their own.
         *
         * @param context the context of the lint request
         * @param visitor the visitor created from
         * [.createPsiVisitor], or null
         * @param node the [PsiNewExpression] node for the invoked method
         * @param constructor the called constructor method
         */
        fun visitConstructor(
                context: JavaContext,
                visitor: JavaElementVisitor?,
                node: PsiNewExpression,
                constructor: PsiMethod)

        /**
         * Method invoked for any references found that matches any names returned by [ ][.getApplicableReferenceNames]. This also passes back the visitor that was created by
         * [.createPsiVisitor], but a visitor is not required. It is intended for
         * detectors that need to do additional AST processing, but also want the convenience of not
         * having to look for method names on their own.
         *
         * @param context    the context of the lint request
         * @param visitor    the visitor created from [.createPsiVisitor], or
         * null
         * @param reference  the [PsiJavaCodeReferenceElement] element
         * @param referenced the referenced element
         */
        fun visitReference(
                context: JavaContext,
                visitor: JavaElementVisitor?,
                reference: PsiJavaCodeReferenceElement,
                referenced: PsiElement)

        /**
         * Returns whether this detector cares about Android resource references
         * (such as `R.layout.main` or `R.string.app_name`). If it
         * does, then the visitor will look for these patterns, and if found, it
         * will invoke [.visitResourceReference] passing the resource type
         * and resource name. It also passes the visitor, if any, that was
         * created by [.createJavaVisitor], such that a
         * detector can do more than just look for resources.
         *
         * @return true if this detector wants to be notified of R resource
         * identifiers found in the code.
         */
        fun appliesToResourceRefs(): Boolean

        /**
         * Called for any resource references (such as `R.layout.main`
         * found in Java code, provided this detector returned `true` from
         * [.appliesToResourceRefs].
         *
         * @param context the lint scanning context
         * @param visitor the visitor created from
         * [.createPsiVisitor], or null
         * @param node the variable reference for the resource
         * @param type the resource type, such as "layout" or "string"
         * @param name the resource name, such as "main" from
         * `R.layout.main`
         * @param isFramework whether the resource is a framework resource
         * (android.R) or a local project resource (R)
         */
        fun visitResourceReference(
                context: JavaContext,
                visitor: JavaElementVisitor?,
                node: PsiElement,
                type: ResourceType,
                name: String,
                isFramework: Boolean)

        /**
         * Returns a list of fully qualified names for super classes that this
         * detector cares about. If not null, this detector will **only** be called
         * if the current class is a subclass of one of the specified superclasses.
         *
         * @return a list of fully qualified names
         */
        fun applicableSuperClasses(): List<String>?

        /**
         * Called for each class that extends one of the super classes specified with
         * [.applicableSuperClasses].
         *
         *
         * Note: This method will not be called for [PsiTypeParameter] classes. These
         * aren't really classes in the sense most lint detectors think of them, so these
         * are excluded to avoid having lint checks that don't defensively code for these
         * accidentally report errors on type parameters. If you really need to check these,
         * use [.getApplicablePsiTypes] with `PsiTypeParameter.class` instead.
         *
         * @param context the lint scanning context
         * @param declaration the class declaration node, or null for anonymous classes
         */
        fun checkClass(context: JavaContext, declaration: PsiClass)
    }

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
     * detectors, such as [XmlScanner] and [JavaScanner], where
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
    @Deprecated("Slated for removal") // Slated for removal in lint 2.0 - this method isn't used
    fun appliesTo(context: Context, file: File): Boolean {
        return false
    }

    /**
     * Analysis is about to begin, perform any setup steps.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun beforeCheckProject(context: Context) {}

    /**
     * Analysis has just been finished for the whole project, perform any
     * cleanup or report issues that require project-wide analysis.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun afterCheckProject(context: Context) {}

    /**
     * Analysis is about to begin for the given library project, perform any setup steps.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun beforeCheckLibraryProject(context: Context) {}

    /**
     * Analysis has just been finished for the given library project, perform any
     * cleanup or report issues that require library-project-wide analysis.
     *
     * @param context the context for the check referencing the project, lint
     * client, etc
     */
    open fun afterCheckLibraryProject(context: Context) {}

    /**
     * Analysis is about to be performed on a specific file, perform any setup
     * steps.
     *
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
    @Deprecated("Slated for removal") // Slated for removal in Lint 2.0
    open fun getSpeed(issue: Issue): Speed = Speed.NORMAL

    // ---- Dummy implementations to make implementing XmlScanner easier: ----

    open fun visitDocument(context: XmlContext, document: Document) {
        // This method must be overridden if your detector does
        // not return something from getApplicableElements or
        // getApplicableAttributes
        assert(false)
    }

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

    // ---- Dummy implementations to make implementing JavaScanner easier: ----

    @Deprecated("")
    open fun createJavaVisitor(context: JavaContext): AstVisitor? {
        return null
    }

    @Deprecated("")
    open fun visitMethod(context: JavaContext, visitor: AstVisitor?,
            node: MethodInvocation) {
    }

    @Deprecated("")
    open fun visitResourceReference(context: JavaContext, visitor: AstVisitor?,
            node: Node, type: String, name: String,
            isFramework: Boolean) {
    }

    @Deprecated("")
    open fun checkClass(context: JavaContext, declaration: ClassDeclaration?,
            node: Node, resolvedClass: ResolvedClass) {
    }

    @Deprecated("")
    open fun visitConstructor(
            context: JavaContext,
            visitor: AstVisitor?,
            node: ConstructorInvocation,
            constructor: ResolvedMethod) {
    }

    // ---- Dummy implementations to make implementing a ClassScanner easier: ----

    open fun checkClass(context: ClassContext, classNode: ClassNode) {}

    open fun checkCall(context: ClassContext, classNode: ClassNode,
            method: MethodNode, call: MethodInsnNode) {
    }

    open fun checkInstruction(context: ClassContext, classNode: ClassNode,
            method: MethodNode, instruction: AbstractInsnNode) {
    }

    // ---- Dummy implementations to make implementing an GradleScanner easier: ----

    open fun visitBuildScript(context: Context) {}

    // ---- Dummy implementations to make implementing a resource folder scanner easier: ----

    open fun checkFolder(context: ResourceContext, folderName: String) {}

    // ---- Dummy implementations to make implementing a binary resource scanner easier: ----

    open fun checkBinaryResource(context: ResourceContext) {}

    open fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    open fun appliesToResourceRefs(): Boolean {
        return false
    }

    open fun applicableSuperClasses(): List<String>? {
        return null
    }

    open fun visitMethod(context: JavaContext, visitor: JavaElementVisitor?,
            call: PsiMethodCallExpression, method: PsiMethod) {
    }

    open fun visitConstructor(
            context: JavaContext,
            visitor: JavaElementVisitor?,
            node: PsiNewExpression,
            constructor: PsiMethod) {
    }

    open fun visitResourceReference(context: JavaContext,
            visitor: JavaElementVisitor?, node: PsiElement,
            type: ResourceType, name: String, isFramework: Boolean) {
    }

    open fun checkClass(context: JavaContext, declaration: PsiClass) {}

    open fun createPsiVisitor(context: JavaContext): JavaElementVisitor? {
        return null
    }

    open fun visitReference(
            context: JavaContext,
            visitor: JavaElementVisitor?,
            reference: PsiJavaCodeReferenceElement,
            referenced: PsiElement) {
    }

    // ---- Dummy implementation to make implementing UastScanner easier: ----

    open fun visitClass(context: JavaContext, declaration: UClass) {}

    open fun visitClass(context: JavaContext, declaration: ULambdaExpression) {}

    open fun visitReference(
            context: JavaContext,
            reference: UReferenceExpression,
            referenced: PsiElement) {
    }

    open fun visitConstructor(
            context: JavaContext,
            node: UCallExpression,
            constructor: PsiMethod) {
    }

    open fun visitMethod(
            context: JavaContext,
            node: UCallExpression,
            method: PsiMethod) {
    }

    open fun createUastHandler(context: JavaContext): UElementHandler? {
        return null
    }

    open fun visitResourceReference(
            context: JavaContext,
            node: UElement,
            type: ResourceType,
            name: String,
            isFramework: Boolean) {
    }

    open fun visitAnnotationUsage(
            context: JavaContext,
            argument: UElement,
            annotation: UAnnotation,
            qualifiedName: String,
            method: PsiMethod?,
            annotations: List<UAnnotation>,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            allPackageAnnotations: List<UAnnotation>) {
    }

    open fun applicableAnnotations(): List<String>? {
        return null
    }

    open fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {}

    open fun getApplicableElements(): Collection<String>? = null

    open fun getApplicableAttributes(): Collection<String>? = null

    open fun getApplicableNodeTypes(): List<Class<out Node>>? = null

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

    /** Creates a lint fix builder. Just a convenience wrapper around [LintFix.create].  */
    protected open fun fix(): LintFix.Builder {
        return LintFix.create()
    }
}
