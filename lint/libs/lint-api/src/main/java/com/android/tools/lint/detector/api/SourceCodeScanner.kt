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
package com.android.tools.lint.detector.api

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression

/**
 * Interface to be implemented by lint detectors that want to analyze
 * Java source files (or other similar source files, such as Kotlin files.)
 * <p>
 * There are several different common patterns for detecting issues:
 * <ul>
 * <li> Checking calls to a given method. For this see
 * {@link #getApplicableMethodNames()} and
 * {@link #visitMethodCall(JavaContext, UCallExpression, PsiMethod)}</li>
 * <li> Instantiating a given class. For this, see
 * {@link #getApplicableConstructorTypes()} and
 * {@link #visitConstructor(JavaContext, UCallExpression, PsiMethod)}</li>
 * <li> Referencing a given constant. For this, see
 * {@link #getApplicableReferenceNames()} and
 * {@link #visitReference(JavaContext, UReferenceExpression, PsiElement)}</li>
 * <li> Extending a given class or implementing a given interface.
 * For this, see {@link #applicableSuperClasses()} and
 * {@link #visitClass(JavaContext, UClass)}</li>
 * <li> More complicated scenarios: perform a general AST
 * traversal with a visitor. In this case, first tell lint which
 * AST node types you're interested in with the
 * {@link #getApplicableUastTypes()} method, and then provide a
 * {@link UElementHandler} from the {@link #createUastHandler(JavaContext)}
 * where you override the various applicable handler methods. This is
 * done rather than a general visitor from the root node to avoid
 * having to have every single lint detector (there are hundreds) do a full
 * tree traversal on its own.</li>
 * </ul>
 * <p>
 * {@linkplain SourceCodeScanner} exposes the UAST API to lint checks.
 * UAST is short for "Universal AST" and is an abstract syntax tree library
 * which abstracts away details about Java versus Kotlin versus other similar languages
 * and lets the client of the library access the AST in a unified way.
 * <p>
 * UAST isn't actually a full replacement for PSI; it <b>augments</b> PSI.
 * Essentially, UAST is used for the <b>inside</b> of methods (e.g. method bodies),
 * and things like field initializers. PSI continues to be used at the outer
 * level: for packages, classes, and methods (declarations and signatures).
 * There are also wrappers around some of these for convenience.
 * <p>
 * The {@linkplain SourceCodeScanner} interface reflects this fact. For example,
 * when you indicate that you want to check calls to a method named {@code foo},
 * the call site node is a UAST node (in this case, {@link UCallExpression},
 * but the called method itself is a {@link PsiMethod}, since that method
 * might be anywhere (including in a library that we don't have source for,
 * so UAST doesn't make sense.)
 * <p>
 * <h2>Migrating JavaPsiScanner to SourceCodeScanner</h2>
 * As described above, PSI is still used, so a lot of code will remain the
 * same. For example, all resolve methods, including those in UAST, will
 * continue to return PsiElement, not necessarily a UElement. For example,
 * if you resolve a method call or field reference, you'll get a
 * {@link PsiMethod} or {@link PsiField} back.
 * <p>
 * However, the visitor methods have all changed, generally to change
 * to UAST types. For example, the signature
 * {@link JavaPsiScanner#visitMethodCall(JavaContext, JavaElementVisitor, PsiMethodCallExpression, PsiMethod)}
 * should be changed to {@link SourceCodeScanner#visitMethodCall(JavaContext, UCallExpression, PsiMethod)}.
 * <p>
 * Similarly, replace {@link JavaPsiScanner#createPsiVisitor} with {@link SourceCodeScanner#createUastHandler},
 * {@link JavaPsiScanner#getApplicablePsiTypes()} with {@link SourceCodeScanner#getApplicableUastTypes()}, etc.
 * <p>
 * There are a bunch of new methods on classes like {@link JavaContext} which lets
 * you pass in a {@link UElement} to match the existing {@link PsiElement} methods.
 * <p>
 * If you have code which does something specific with PSI classes,
 * the following mapping table in alphabetical order might be helpful, since it lists the
 * corresponding UAST classes.
 * <table>
 *     <caption>Mapping between PSI and UAST classes</caption>
 *     <tr><th>PSI</th><th>UAST</th></tr>
 *     <tr><th>com.intellij.psi.</th><th>org.jetbrains.uast.</th></tr>
 *     <tr><td>IElementType</td><td>UastBinaryOperator</td></tr>
 *     <tr><td>PsiAnnotation</td><td>UAnnotation</td></tr>
 *     <tr><td>PsiAnonymousClass</td><td>UAnonymousClass</td></tr>
 *     <tr><td>PsiArrayAccessExpression</td><td>UArrayAccessExpression</td></tr>
 *     <tr><td>PsiBinaryExpression</td><td>UBinaryExpression</td></tr>
 *     <tr><td>PsiCallExpression</td><td>UCallExpression</td></tr>
 *     <tr><td>PsiCatchSection</td><td>UCatchClause</td></tr>
 *     <tr><td>PsiClass</td><td>UClass</td></tr>
 *     <tr><td>PsiClassObjectAccessExpression</td><td>UClassLiteralExpression</td></tr>
 *     <tr><td>PsiConditionalExpression</td><td>UIfExpression</td></tr>
 *     <tr><td>PsiDeclarationStatement</td><td>UDeclarationsExpression</td></tr>
 *     <tr><td>PsiDoWhileStatement</td><td>UDoWhileExpression</td></tr>
 *     <tr><td>PsiElement</td><td>UElement</td></tr>
 *     <tr><td>PsiExpression</td><td>UExpression</td></tr>
 *     <tr><td>PsiForeachStatement</td><td>UForEachExpression</td></tr>
 *     <tr><td>PsiIdentifier</td><td>USimpleNameReferenceExpression</td></tr>
 *     <tr><td>PsiIfStatement</td><td>UIfExpression</td></tr>
 *     <tr><td>PsiImportStatement</td><td>UImportStatement</td></tr>
 *     <tr><td>PsiImportStaticStatement</td><td>UImportStatement</td></tr>
 *     <tr><td>PsiJavaCodeReferenceElement</td><td>UReferenceExpression</td></tr>
 *     <tr><td>PsiLiteral</td><td>ULiteralExpression</td></tr>
 *     <tr><td>PsiLocalVariable</td><td>ULocalVariable</td></tr>
 *     <tr><td>PsiMethod</td><td>UMethod</td></tr>
 *     <tr><td>PsiMethodCallExpression</td><td>UCallExpression</td></tr>
 *     <tr><td>PsiNameValuePair</td><td>UNamedExpression</td></tr>
 *     <tr><td>PsiNewExpression</td><td>UCallExpression</td></tr>
 *     <tr><td>PsiParameter</td><td>UParameter</td></tr>
 *     <tr><td>PsiParenthesizedExpression</td><td>UParenthesizedExpression</td></tr>
 *     <tr><td>PsiPolyadicExpression</td><td>UPolyadicExpression</td></tr>
 *     <tr><td>PsiPostfixExpression</td><td>UPostfixExpression or UUnaryExpression</td></tr>
 *     <tr><td>PsiPrefixExpression</td><td>UPrefixExpression or UUnaryExpression</td></tr>
 *     <tr><td>PsiReference</td><td>UReferenceExpression</td></tr>
 *     <tr><td>PsiReference</td><td>UResolvable</td></tr>
 *     <tr><td>PsiReferenceExpression</td><td>UReferenceExpression</td></tr>
 *     <tr><td>PsiReturnStatement</td><td>UReturnExpression</td></tr>
 *     <tr><td>PsiSuperExpression</td><td>USuperExpression</td></tr>
 *     <tr><td>PsiSwitchLabelStatement</td><td>USwitchClauseExpression</td></tr>
 *     <tr><td>PsiSwitchStatement</td><td>USwitchExpression</td></tr>
 *     <tr><td>PsiThisExpression</td><td>UThisExpression</td></tr>
 *     <tr><td>PsiThrowStatement</td><td>UThrowExpression</td></tr>
 *     <tr><td>PsiTryStatement</td><td>UTryExpression</td></tr>
 *     <tr><td>PsiTypeCastExpression</td><td>UBinaryExpressionWithType</td></tr>
 *     <tr><td>PsiWhileStatement</td><td>UWhileExpression</td></tr>
 * </table>
 * Note however that UAST isn't just a "renaming of classes"; there are
 * some changes to the structure of the AST as well. Particularly around
 * calls.
 *
 * <h3>Parents</h3>
 * In UAST, you get your parent {@linkplain UElement} by calling
 * {@code getUastParent} instead of {@code getParent}. This is to avoid
 * method name clashes on some elements which are both UAST elements
 * and PSI elements at the same time - such as {@link UMethod}.
 * <h3>Children</h3>
 * When you're going in the opposite direction (e.g. you have a {@linkplain PsiMethod}
 * and you want to look at its content, you should <b>not</b> use
 * {@link PsiMethod#getBody()}. This will only give you the PSI child content,
 * which won't work for example when dealing with Kotlin methods.
 * Normally lint passes you the {@linkplain UMethod} which you should be procesing
 * instead. But if for some reason you need to look up the UAST method
 * body from a {@linkplain PsiMethod}, use this:
 * <pre>
 *     UastContext context = UastUtils.getUastContext(element);
 *     UExpression body = context.getMethodBody(method);
 * </pre>
 * Similarly if you have a {@link PsiField} and you want to look up its field
 * initializer, use this:
 * <pre>
 *     UastContext context = UastUtils.getUastContext(element);
 *     UExpression initializer = context.getInitializerBody(field);
 * </pre>
 *
 * <h3>Call names</h3>
 * In PSI, a call was represented by a PsiCallExpression, and to get to things
 * like the method called or to the operand/qualifier, you'd first need to get
 * the "method expression". In UAST there is no method expression and this
 * information is available directly on the {@linkplain UCallExpression} element.
 * Therefore, here's how you'd change the code:
 * <pre>
 * &lt;    call.getMethodExpression().getReferenceName();
 * ---
 * &gt;    call.getMethodName()
 * </pre>
 * <h3>Call qualifiers</h3>
 * Similarly,
 * <pre>
 * &lt;    call.getMethodExpression().getQualifierExpression();
 * ---
 * &gt;    call.getReceiver()
 * </pre>
 * <h3>Call arguments</h3>
 * PSI had a separate PsiArgumentList element you had to look up before you could
 * get to the actual arguments, as an array. In UAST these are available directly on
 * the call, and are represented as a list instead of an array.
 * <pre>
 * &lt;    PsiExpression[] args = call.getArgumentList().getExpressions();
 * ---
 * &gt;    List<UExpression> args = call.getValueArguments();
 * </pre>
 * Typically you also need to go through your code and replace array access,
 * arg\[i], with list access, {@code arg.get(i)}. Or in Kotlin, just arg\[i]...
 *
 * <h3>Instanceof</h3>
 * You may have code which does something like "parent instanceof PsiAssignmentExpression"
 * to see if something is an assignment. Instead, use one of the many utilities
 * in {@link UastExpressionUtils} - such as {@link UastExpressionUtils#isAssignment(UElement)}.
 * Take a look at all the methods there now - there are methods for checking whether
 * a call is a constructor, whether an expression is an array initializer, etc etc.
 *
 * <h3>Android Resources</h3>
 * Don't do your own AST lookup to figure out if something is a reference to
 * an Android resource (e.g. see if the class refers to an inner class of a class
 * named "R" etc.)  There is now a new utility class which handles this:
 * {@link ResourceReference}. Here's an example of code which has a {@link UExpression}
 * and wants to know it's referencing a R.styleable resource:
 * <pre>
 *        ResourceReference reference = ResourceReference.get(expression);
 *        if (reference == null || reference.getType() != ResourceType.STYLEABLE) {
 *            return;
 *        }
 *        ...
 * </pre>
 *
 * <h3>Binary Expressions</h3>
 * If you had been using {@link PsiBinaryExpression} for things like checking comparator
 * operators or arithmetic combination of operands, you can replace this with
 * {@link UBinaryExpression}. <b>But you normally shouldn't; you should use
 * {@link UPolyadicExpression} instead</b>. A polyadic expression is just like a binary
 * expression, but possibly with more than two terms. With the old parser backend,
 * an expression like "A + B + C" would be represented by nested binary expressions
 * (first A + B, then a parent element which combined that binary expression with C).
 * However, this will now be provided as a {@link UPolyadicExpression} instead. And
 * the binary case is handled trivially without the need to special case it.
 * <h3>Method name changes</h3>
 * The following table maps some common method names and what their corresponding
 * names are in UAST.
 * <table>
 *     <caption>Mapping between PSI and UAST method names</caption></caption>
 *     <tr><th>PSI</th><th>UAST</th></tr>
 *     <tr><td>getArgumentList</td><td>getValueArguments</td></tr>
 *     <tr><td>getCatchSections</td><td>getCatchClauses</td></tr>
 *     <tr><td>getDeclaredElements</td><td>getDeclarations</td></tr>
 *     <tr><td>getElseBranch</td><td>getElseExpression</td></tr>
 *     <tr><td>getInitializer</td><td>getUastInitializer</td></tr>
 *     <tr><td>getLExpression</td><td>getLeftOperand</td></tr>
 *     <tr><td>getOperationTokenType</td><td>getOperator</td></tr>
 *     <tr><td>getOwner</td><td>getUastParent</td></tr>
 *     <tr><td>getParent</td><td>getUastParent</td></tr>
 *     <tr><td>getRExpression</td><td>getRightOperand</td></tr>
 *     <tr><td>getReturnValue</td><td>getReturnExpression</td></tr>
 *     <tr><td>getText</td><td>asSourceString</td></tr>
 *     <tr><td>getThenBranch</td><td>getThenExpression</td></tr>
 *     <tr><td>getType</td><td>getExpressionType</td></tr>
 *     <tr><td>getTypeParameters</td><td>getTypeArguments</td></tr>
 *     <tr><td>resolveMethod</td><td>resolve</td></tr>
 * </table>
 * <h3>Handlers versus visitors</h3>
 * If you are processing a method on your own, or even a full class, you should switch
 * from JavaRecursiveElementVisitor to AbstractUastVisitor.
 * However, most lint checks don't do their own full AST traversal; they instead
 * participate in a shared traversal of the tree, registering element types they're
 * interested with using {@link #getApplicableUastTypes()} and then providing
 * a visitor where they implement the corresponding visit methods. However, from
 * these visitors you should <b>not</b> be calling super.visitX. To remove this
 * whole confusion, lint now provides a separate class, {@link UElementHandler}.
 * For the shared traversal, just provide this handler instead and implement the
 * appropriate visit methods. It will throw an error if you register element types
 * in {@linkplain #getApplicableUastTypes()} that you don't override.
 *
 * <p>
 * <h3>Migrating JavaScanner to SourceCodeScanner</h3>
 * First read the javadoc on how to convert from the older {@linkplain JavaScanner}
 * interface over to {@linkplain JavaPsiScanner}. While {@linkplain JavaPsiScanner} is itself
 * deprecated, it's a lot closer to {@link SourceCodeScanner} so a lot of the same concepts
 * apply; then follow the above section.
 * <p>
 */
interface SourceCodeScanner : FileScanner {

    /**
     * Return the list of method names this detector is interested in, or
     * null. If this method returns non-null, then any AST nodes that match
     * a method call in the list will be passed to the
     * [.visitMethodCall] method for processing. The visitor created by
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
     * Method invoked for any method calls found that matches any names
     * returned by [.getApplicableMethodNames]. This also passes
     * back the visitor that was created by
     * [.createJavaVisitor], but a visitor is not
     * required. It is intended for detectors that need to do additional AST
     * processing, but also want the convenience of not having to look for
     * method names on their own.
     * @param context the context of the lint request
     * @param node the [PsiMethodCallExpression] node for the invoked method
     * @param method the [PsiMethod] being called
     * @deprecated This is really visiting calls, not methods, so has been renamed to
     *    {@link #visitMethodCall} instead
     */
    @Deprecated("Rename to visitMethodCall instead when targeting 3.3+")
    fun visitMethod(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    )

    /**
     * Method invoked for any method calls found that matches any names
     * returned by [.getApplicableMethodNames]. This also passes
     * back the visitor that was created by
     * [.createJavaVisitor], but a visitor is not
     * required. It is intended for detectors that need to do additional AST
     * processing, but also want the convenience of not having to look for
     * method names on their own.
     * @param context the context of the lint request
     * @param node the [PsiMethodCallExpression] node for the invoked method
     * @param method the [PsiMethod] being called
     */
    fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    )

    /**
     * Return the list of constructor types this detector is interested in, or
     * null. If this method returns non-null, then any AST nodes that match
     * a constructor call in the list will be passed to the
     * [.visitConstructor]
     * method for processing. The visitor created by
     * [.createJavaVisitor] is also passed to that
     * method, although it can be null.
     *
     * This makes it easy to write detectors that focus on some fixed constructors.
     *
     * @return a set of applicable fully qualified types, or null.
     */
    fun getApplicableConstructorTypes(): List<String>?

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
     * @param node the [PsiNewExpression] node for the invoked method
     * @param constructor the called constructor method
     */
    fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    )

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
     * Method invoked for any references found that matches any names returned by
     * [.getApplicableReferenceNames]. This also passes back the visitor that was created by
     * [.createPsiVisitor], but a visitor is not required. It is intended for
     * detectors that need to do additional AST processing, but also want the convenience of not
     * having to look for method names on their own.
     *
     * @param context the context of the lint request
     * @param reference the [PsiJavaCodeReferenceElement] element
     * @param referenced the referenced element
     */
    fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    )

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
     * @param node the variable reference for the resource
     * @param type the resource type, such as "layout" or "string"
     * @param name the resource name, such as "main" from `R.layout.main`
     * @param isFramework whether the resource is a framework resource (android.R) or a local
     * project resource (R)
     */
    fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    )

    /**
     * Returns a list of fully qualified names for super classes that this
     * detector cares about. If not null, this detector will **only** be called
     * if the current class is a subclass of one of the specified superclasses.
     * Lint will invoke [.visitClass] (and sometimes
     * [.visitClass] when it encounters
     * subclasses and lambdas for these types.
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
    fun visitClass(context: JavaContext, declaration: UClass)

    /**
     * Like [.visitClass], but used for lambdas in
     * SAM (single abstract method) types. For example, if you have
     * have this method:
     * <pre>
     * void enqueue(Runnable runnable) { ... }
     * ...
     * enqueue({ something(); })
     </pre> *
     * then the lambda being passed to the call can be thought of as a class
     * implementing the Runnable interface.
     *
     *
     * The set of target types for the lambda are provided in [.applicableSuperClasses]
     *
     * @param context the lint scanning context
     * @param lambda the lambda
     */
    fun visitClass(context: JavaContext, lambda: ULambdaExpression)

    /**
     * Returns a list of fully qualified names for super classes that this
     * detector cares about. If not null, this detector will **only** be called
     * if the current class is a subclass of one of the specified superclasses.
     * Lint will invoke [.visitClass] (and sometimes
     * [.visitClass] when it encounters
     * subclasses and lambdas for these types.
     *
     * @return a list of fully qualified names
     */
    fun applicableAnnotations(): List<String>?

    /**
     * Returns whether this detector cares about an annotation usage of the given type.
     * Most detectors are interested in all types except for
     * [AnnotationUsageType.BINARY] and [AnnotationUsageType.EQUALITY], which only
     * apply for annotations that are expressing a "type", e.g. it would be suspicious
     * to combine (compare, add, etc) resources of different type.
     */
    fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean

    /**
     * Whether this lint detector wants to consider annotations on an
     * element that were inherited (e.g. defined on a super class or super method)
     */
    fun inheritAnnotation(annotation: String): Boolean

    /**
     * Called whenever the given element references an element that has been annotated with one
     * of the annotations returned from [.applicableAnnotations].
     *
     * The element itself may not be annotated; it can also be a member in a class which has
     * been annotated, or a package which has been annotated.
     *
     * The call is handed the annotations found at each level (member, class, package) so that
     * it can decide how to handle them.
     *
     * @param context the lint scanning context
     * @param usage the element to be checked
     * @param type the type of annotation usage lint has found
     * @param annotation the annotation this detector is interested in
     * @param qualifiedName the annotation's qualified name
     * @param method the method, if any
     * @param annotations the annotations to check. These are the annotations
     * you've registered an interest in with
     * [.applicableAnnotations], whether they were
     * specified as a parameter annotation, method annotation,
     * class annotation or package annotation. The various
     * annotations available in those contexts are also supplied.
     * This lets you not only see where an annotation was
     * specified, but you can check the relative priorities
     * of annotations. For example, let's say you have a
     * `@WorkerThread` annotation on a class. If you also
     * happen to have a `@UiThread` annotation on a member
     * you shouldn't enforce worker thread semantics on the
     * member.
     * @param allMemberAnnotations all member annotations (may include other annotations
     * than the ones you've registered an interest in with
     * [.applicableAnnotations])
     * @param allClassAnnotations all annotations in the target surrounding class
     * @param allPackageAnnotations all annotations in the target surrounding package
     * @deprecated This method is missing the resolved parameter; use the other method instead
     */
    @Deprecated(
        "There is a new version of this method which also takes a resolved " +
            "parameter; use that one instead"
    )
    fun visitAnnotationUsage(
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
    )

    @Suppress("DEPRECATION") // Delegating to deprecated API
    /**
     * Called whenever the given element references an element that has been annotated with one
     * of the annotations returned from [.applicableAnnotations].
     *
     * The element itself may not be annotated; it can also be a member in a class which has
     * been annotated, or a package which has been annotated.
     *
     * The call is handed the annotations found at each level (member, class, package) so that
     * it can decide how to handle them.
     *
     * @param context the lint scanning context
     * @param usage the element to be checked
     * @param type the type of annotation usage lint has found
     * @param annotation the annotation this detector is interested in
     * @param qualifiedName the annotation's qualified name
     * @param method the method, if any
     * @param referenced the referenced referenced element (method, field, etc), if any
     * @param annotations the annotations to check. These are the annotations
     * you've registered an interest in with
     * [.applicableAnnotations], whether they were
     * specified as a parameter annotation, method annotation,
     * class annotation or package annotation. The various
     * annotations available in those contexts are also supplied.
     * This lets you not only see where an annotation was
     * specified, but you can check the relative priorities
     * of annotations. For example, let's say you have a
     * `@WorkerThread` annotation on a class. If you also
     * happen to have a `@UiThread` annotation on a member
     * you shouldn't enforce worker thread semantics on the
     * member.
     * @param allMemberAnnotations all member annotations (may include other annotations
     * than the ones you've registered an interest in with
     * [.applicableAnnotations])
     * @param allClassAnnotations all annotations in the target surrounding class
     * @param allPackageAnnotations all annotations in the target surrounding package
     * @deprecated This method is missing the referenced parameter; use the other method instead
     */
    fun visitAnnotationUsage(
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
    )

    /**
     * Return the types of AST nodes that the visitor returned from
     * [.createJavaVisitor] should visit. See the
     * documentation for [.createJavaVisitor] for details
     * on how the shared visitor is used.
     *
     * If you return null from this method, then the visitor will process
     * the full tree instead.
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
    fun getApplicableUastTypes(): List<Class<out UElement>>?

    /**
     * Create a parse tree visitor to process the parse tree. All
     * [SourceCodeScanner] detectors must provide a visitor, unless they
     * either return true from [.appliesToResourceRefs] or return
     * non null from [.getApplicableMethodNames].
     *
     * If you return specific AST node types from
     * [.getApplicableUastTypes] ()}, then the visitor will **only**
     * be called for the specific requested node types. This is more
     * efficient, since it allows many detectors that apply to only a small
     * part of the AST (such as method call nodes) to share iteration of the
     * majority of the parse tree.
     *
     * If you return null from [.getApplicableUastTypes] ()}, then your
     * visitor will be called from the top and all node types visited.
     *
     * Note that a new visitor is created for each separate compilation
     * unit, so you can store per file state in the visitor.
     *
     *
     * @param context the [Context] for the file being analyzed
     * @return a visitor, or null.
     */
    fun createUastHandler(context: JavaContext): UElementHandler?

    /**
     * Whether this implementation wants to access the global call graph
     * with a call to [.analyzeCallGraph].
     *
     * **NOTE: Highly experimental as well as resource intensive!**
     */
    fun isCallGraphRequired(): Boolean

    /**
     * Analyze the call graph requested with [.isCallGraphRequired]
     *
     * **NOTE: Highly experimental as well as resource intensive!**
     */
    fun analyzeCallGraph(context: Context, callGraph: CallGraphResult)
}
