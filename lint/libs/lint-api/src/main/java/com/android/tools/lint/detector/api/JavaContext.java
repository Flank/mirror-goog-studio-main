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

package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import static com.android.tools.lint.client.api.JavaParser.TypeDescriptor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Detector.UastScanner;
import com.android.utils.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import lombok.ast.AnnotationElement;
import lombok.ast.AnnotationMethodDeclaration;
import lombok.ast.ClassDeclaration;
import lombok.ast.ConstructorDeclaration;
import lombok.ast.ConstructorInvocation;
import lombok.ast.EnumConstant;
import lombok.ast.Expression;
import lombok.ast.LabelledStatement;
import lombok.ast.MethodDeclaration;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.Position;
import lombok.ast.TypeDeclaration;
import lombok.ast.VariableReference;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UDeclaration;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UEnumConstant;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USwitchExpression;
import org.jetbrains.uast.UastContext;
import org.jetbrains.uast.UastUtils;

/**
 * A {@link Context} used when checking Java files.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class JavaContext extends Context {
    static final String SUPPRESS_COMMENT_PREFIX = "//noinspection ";

    /**
     * The parse tree
     *
     * @deprecated Use {@link #uastFile} instead (see {@link UastScanner})
     */
    @Deprecated
    private Node compilationUnit;

    /** The parse tree, when using PSI */
    private PsiFile psiFile;

    /** The parse tree, when using UAST */
    private UFile uastFile;

    /** The UAST parser which produced the parse tree */
    private UastParser uastParser;

    /** The parser which produced the parse tree */
    private JavaParser parser;

    /** Whether this context is in a test source folder */
    private boolean testSource;

    /**
     * Constructs a {@link JavaContext} for running lint on the given file, with
     * the given scope, in the given project reporting errors to the given
     * client.
     *
     * @param driver the driver running through the checks
     * @param project the project to run lint on which contains the given file
     * @param main the main project if this project is a library project, or
     *            null if this is not a library project. The main project is
     *            the root project of all library projects, not necessarily the
     *            directly including project.
     * @param file the file to be analyzed
     */
    public JavaContext(
            @NonNull LintDriver driver,
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File file) {
        super(driver, project, main, file);
    }

    /**
     * Returns a location for the given node
     *
     * @param node the AST node to get a location for
     * @return a location for the given node
     */
    @NonNull
    public Location getLocation(@NonNull Node node) {
        return parser.getLocation(this, node);
    }

    /**
     * Returns a location for the given node range (from the starting offset of the first node to
     * the ending offset of the second node).
     *
     * @param from      the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param to        the AST node to get a ending location from
     * @param toDelta   Offset delta to apply to the ending offset
     * @return a location for the given node
     */
    @NonNull
    public Location getRangeLocation(
            @NonNull Node from,
            int fromDelta,
            @NonNull Node to,
            int toDelta) {
        return parser.getRangeLocation(this, from, fromDelta, to, toDelta);
    }

    /**
     * Returns a location for the given node range (from the starting offset of the first node to
     * the ending offset of the second node).
     *
     * @param from      the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param to        the AST node to get a ending location from
     * @param toDelta   Offset delta to apply to the ending offset
     * @return a location for the given node
     */
    @NonNull
    public Location getRangeLocation(
            @NonNull PsiElement from,
            int fromDelta,
            @NonNull PsiElement to,
            int toDelta) {
        if (uastParser != null) {
            return uastParser.getRangeLocation(this, from, fromDelta, to, toDelta);
        } else {
            return parser.getRangeLocation(this, from, fromDelta, to, toDelta);
        }
    }

    @NonNull
    public Location getRangeLocation(
            @NonNull UElement from,
            int fromDelta,
            @NonNull UElement to,
            int toDelta) {
        return uastParser.getRangeLocation(this, from, fromDelta, to, toDelta);
    }

    // Disambiguate since UDeclarations implement both PsiElement and UElement
    @NonNull
    public Location getRangeLocation(
            @NonNull UDeclaration from,
            int fromDelta,
            @NonNull UDeclaration to,
            int toDelta) {
        return uastParser.getRangeLocation(this, (PsiElement)from, fromDelta, to, toDelta);
    }

    /**
     * Returns a location for the given node range (from the starting offset of the first node to
     * the ending offset of the second node).
     *
     * @param from      the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param length    The number of characters to add from the delta
     * @return a location for the given node
     */
    @NonNull
    public Location getRangeLocation(
            @NonNull PsiElement from,
            int fromDelta,
            int length) {
        if (uastParser != null) {
            return uastParser.getRangeLocation(this, from, fromDelta, fromDelta + length);
        } else {
            return parser.getRangeLocation(this, from, fromDelta, fromDelta + length);
        }
    }

    @NonNull
    public Location getRangeLocation(
            @NonNull UElement from,
            int fromDelta,
            int length) {
        return uastParser.getRangeLocation(this, from, fromDelta, fromDelta + length);
    }

    /**
     * Returns a {@link Location} for the given node. This attempts to pick a shorter
     * location range than the entire node; for a class or method for example, it picks
     * the name node (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * @param node the AST node to create a location for
     * @return a location for the given node
     */
    @NonNull
    public Location getNameLocation(@NonNull Node node) {
        return parser.getNameLocation(this, node);
    }

    /**
     * Returns a {@link Location} for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * @param element the AST element to create a location for
     * @return a location for the given element
     */
    @NonNull
    public Location getNameLocation(@NonNull PsiElement element) {
        if (uastParser != null) {
            if (element instanceof PsiSwitchStatement) {
                // Just use keyword
                return uastParser.getRangeLocation(this, element, 0, 6); // 6: "switch".length()
            }
            return uastParser.getNameLocation(this, element);
        } else {
            if (element instanceof PsiSwitchStatement) {
                // Just use keyword
                return parser.getRangeLocation(this, element, 0, 6); // 6: "switch".length()
            }
            return parser.getNameLocation(this, element);
        }
    }

    /**
     * Returns a {@link Location} for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * @param element the AST element to create a location for
     * @return a location for the given element
     */
    @NonNull
    public Location getNameLocation(@NonNull UElement element) {
        if (element instanceof USwitchExpression) {
            // Just use keyword
            return uastParser.getRangeLocation(this, element, 0, 6); // 6: "switch".length()
        }
        return uastParser.getNameLocation(this, element);
    }

    /**
     * Returns a {@link Location} for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * <p>
     * {@link UClass} is both a {@link PsiElement} and a {@link UElement} so this method
     * is here to make calling getNameLocation(UClass) easier without having to make an
     * explicit cast.
     *
     * @param cls the AST class element to create a location for
     * @return a location for the given element
     */
    @NonNull
    public Location getNameLocation(@NonNull UClass cls) {
        return getNameLocation((UElement)cls);
    }

    /**
     * Returns a {@link Location} for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a {@code switch} statement
     * it will highlight the keyword, etc.
     *
     * <p>
     * {@link UMethod} is both a {@link PsiElement} and a {@link UElement} so this method
     * is here to make calling getNameLocation(UMethod) easier without having to make an
     * explicit cast.
     *
     * @param cls the AST class element to create a location for
     * @return a location for the given element
     */
    @NonNull
    public Location getNameLocation(@NonNull UMethod cls) {
        return getNameLocation((UElement)cls);
    }

    @NonNull
    public Location getLocation(@NonNull PsiElement node) {
        if (uastParser != null) {
            return uastParser.getLocation(this, node);
        } else {
            return parser.getLocation(this, node);
        }
    }

    @NonNull
    public Location getLocation(@NonNull UElement element) {
        if (element instanceof UCallExpression) {
            return uastParser.getCallLocation(this, (UCallExpression) element, true, true);
        }
        return uastParser.getLocation(this, element);
    }

    @NonNull
    public Location getLocation(@NonNull UMethod element) {
        return uastParser.getLocation(this, (PsiMethod)element);
    }

    @NonNull
    public Location getLocation(@NonNull UField element) {
        return uastParser.getLocation(this, (PsiField)element);
    }

    /**
     * Creates a location for the given call.
     *
     * @param call             the call to create a location range for
     * @param includeReceiver  whether we should include the receiver of the method call if
     *                         applicable
     * @param includeArguments whether we should include the arguments to the call
     * @return a location
     */
    @NonNull
    public Location getCallLocation(
            @NonNull UCallExpression call,
            boolean includeReceiver,
            boolean includeArguments) {
        return uastParser.getCallLocation(this, call, includeReceiver, includeArguments);
    }

    /**
     * Returns the parser which created the {@link #getPsiFile()}
     *
     * @return the parser
     */
    @NonNull
    public JavaParser getParser() {
        return parser;
    }

    /**
     * Returns the parser which created the {@link #getUastFile()}
     *
     * @return the parser
     */
    @NonNull
    public UastParser getUastParser() {
        return uastParser;
    }

    @NonNull
    public JavaEvaluator getEvaluator() {
        return uastParser != null ? uastParser.getEvaluator() : parser.getEvaluator();
    }

    @Nullable
    public Node getCompilationUnit() {
        return compilationUnit;
    }

    /**
     * Sets the compilation result. Not intended for client usage; the lint infrastructure
     * will set this when a context has been processed
     *
     * @param compilationUnit the parse tree
     */
    public void setCompilationUnit(@Nullable Node compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    /**
     * Returns the {@link PsiJavaFile}.
     *
     * @deprecated Use {@link #getPsiFile()} instead
     * @return the parsed Java source file
     */
    @Nullable
    @Deprecated
    public PsiJavaFile getJavaFile() {
        if (psiFile instanceof PsiJavaFile) {
            return (PsiJavaFile) psiFile;
        } else {
            return null;
        }
    }

    /**
     * Returns the {@link PsiJavaFile}.
     *
     * @return the parsed Java source file
     */
    @Nullable
    public PsiFile getPsiFile() {
        return psiFile;
    }

    /**
     * Sets the compilation result. Not intended for client usage; the lint infrastructure
     * will set this when a context has been processed
     *
     * @param javaFile the parse tree
     */
    public void setJavaFile(@Nullable PsiFile javaFile) {
        this.psiFile = javaFile;
    }

    /** Sets the UAST parser to use */
    public void setUastParser(@Nullable UastParser uastParser) {
        this.uastParser = uastParser;
    }

    /** Sets the Lombok/PSI parser to use */
    public void setParser(@Nullable JavaParser parser) {
        this.parser = parser;
    }

    /**
     * Returns the {@link UFile}.
     *
     * @return the parsed UFile
     */
    @Nullable
    public UFile getUastFile() {
        return uastFile;
    }

    /**
     * Sets the compilation result. Not intended for client usage; the lint infrastructure
     * will set this when a context has been processed
     *
     * @param uastFile the parse tree
     */
    public void setUastFile(@Nullable UFile uastFile) {
        this.uastFile = uastFile;
    }

    @Override
    public void report(@NonNull Issue issue, @NonNull Location location,
            @NonNull String message) {
        if (driver.isSuppressed(this, issue, psiFile)) {
            return;
        }
        super.report(issue, location, message);
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue the issue to report
     * @param scope the AST node scope the error applies to. The lint infrastructure
     *    will check whether there are suppress annotations on this node (or its enclosing
     *    nodes) and if so suppress the warning without involving the client.
     * @param location the location of the issue, or null if not known
     * @param message the message for this warning
     * @deprecated use {@link #report(Issue, PsiElement, Location, String)} instead
     */
    @Deprecated
    public void report(
            @NonNull Issue issue,
            @Nullable Node scope,
            @NonNull Location location,
            @NonNull String message) {
        if (scope != null && driver.isSuppressed(this, issue, scope)) {
            return;
        }
        super.report(issue, location, message);
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue    the issue to report
     * @param scope    the AST node scope the error applies to. The lint infrastructure will check
     *                 whether there are suppress annotations on this node (or its enclosing nodes)
     *                 and if so suppress the warning without involving the client.
     * @param location the location of the issue, or null if not known
     * @param message  the message for this warning
     */
    public void report(
            @NonNull Issue issue,
            @Nullable PsiElement scope,
            @NonNull Location location,
            @NonNull String message) {
        report(issue, scope, location, message, null);
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue        the issue to report
     * @param scope        the AST node scope the error applies to. The lint infrastructure will
     *                     check whether there are suppress annotations on this node (or its
     *                     enclosing nodes) and if so suppress the warning without involving the
     *                     client.
     * @param location     the location of the issue, or null if not known
     * @param message      the message for this warning
     * @param quickfixData optional data to pass to the IDE for use by a quickfix.  If you're
     *                     passing in multiple parameters, consider using {@link QuickfixData}
     *                     instead of using for example {@link Pair} or {@link Map}
     */
    public void report(
            @NonNull Issue issue,
            @Nullable PsiElement scope,
            @NonNull Location location,
            @NonNull String message,
            @Nullable Object quickfixData) {
        if (scope != null && driver.isSuppressed(this, issue, scope)) {
            return;
        }
        super.doReport(issue, location, message, quickfixData);
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue        the issue to report
     * @param scope        the AST node scope the error applies to. The lint infrastructure will
     *                     check whether there are suppress annotations on this node (or its
     *                     enclosing nodes) and if so suppress the warning without involving the
     *                     client.
     * @param location     the location of the issue, or null if not known
     * @param message      the message for this warning
     */
    public void report(
            @NonNull Issue issue,
            @Nullable UElement scope,
            @NonNull Location location,
            @NonNull String message) {
        report(issue, scope, location, message, null);
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue        the issue to report
     * @param scope        the AST node scope the error applies to. The lint infrastructure will
     *                     check whether there are suppress annotations on this node (or its
     *                     enclosing nodes) and if so suppress the warning without involving the
     *                     client.
     * @param location     the location of the issue, or null if not known
     * @param message      the message for this warning
     * @param quickfixData optional data to pass to the IDE for use by a quickfix.  If you're
     *                     passing in multiple parameters, consider using {@link QuickfixData}
     *                     instead of using for example {@link Pair} or {@link Map}
     */
    public void report(
            @NonNull Issue issue,
            @Nullable UElement scope,
            @NonNull Location location,
            @NonNull String message,
            @Nullable Object quickfixData) {
        if (scope != null && driver.isSuppressed(this, issue, scope)) {
            return;
        }
        super.doReport(issue, location, message, quickfixData);
    }

    /**
     * {@link UClass} is both a {@link PsiElement} and a {@link UElement} so this method
     * is here to make calling report(..., UClass, ...) easier without having to make
     * an explicit cast.
     */
    public void report(
            @NonNull Issue issue,
            @Nullable UClass scopeClass,
            @NonNull Location location,
            @NonNull String message) {
        report(issue, (UElement)scopeClass, location, message);
    }

    /**
     * {@link UMethod} is both a {@link PsiElement} and a {@link UElement} so this method
     * is here to make calling report(..., UFieUMethodld, ...) easier without having to make
     * an explicit cast.
     */
    public void report(
            @NonNull Issue issue,
            @Nullable UMethod scopeClass,
            @NonNull Location location,
            @NonNull String message) {
        report(issue, (UElement)scopeClass, location, message);
    }

    /**
     * {@link UField} is both a {@link PsiElement} and a {@link UElement} so this method
     * is here to make calling report(..., UField, ...) easier without having to make
     * an explicit cast.
     */
    public void report(
            @NonNull Issue issue,
            @Nullable UField scopeClass,
            @NonNull Location location,
            @NonNull String message) {
        report(issue, (UElement)scopeClass, location, message);
    }

    /**
     * Report an error.
     * Like {@link #report(Issue, Node, Location, String)} but with
     * a now-unused data parameter at the end.
     *
     * @deprecated Use {@link #report(Issue, Node, Location, String)} instead;
     *    this method is here for custom rule compatibility
     */
    @SuppressWarnings("UnusedDeclaration") // Potentially used by external existing custom rules
    @Deprecated
    public void report(
            @NonNull Issue issue,
            @Nullable Node scope,
            @NonNull Location location,
            @NonNull String message,
            @SuppressWarnings("UnusedParameters") @Nullable Object data) {
        report(issue, scope, location, message);
    }

    /**
     * @deprecated Use {@link PsiTreeUtil#getParentOfType(PsiElement, Class[])}
     * with PsiMethod.class instead
     */
    @Deprecated
    @Nullable
    public static Node findSurroundingMethod(Node scope) {
        while (scope != null) {
            Class<? extends Node> type = scope.getClass();
            // The Lombok AST uses a flat hierarchy of node type implementation classes
            // so no need to do instanceof stuff here.
            if (type == MethodDeclaration.class || type == ConstructorDeclaration.class) {
                return scope;
            }

            scope = scope.getParent();
        }

        return null;
    }

    /**
     * @deprecated Use {@link PsiTreeUtil#getParentOfType(PsiElement, Class[])}
     * with PsiMethod.class instead
     */
    @Deprecated
    @Nullable
    public static ClassDeclaration findSurroundingClass(@Nullable Node scope) {
        while (scope != null) {
            Class<? extends Node> type = scope.getClass();
            // The Lombok AST uses a flat hierarchy of node type implementation classes
            // so no need to do instanceof stuff here.
            if (type == ClassDeclaration.class) {
                return (ClassDeclaration) scope;
            }

            scope = scope.getParent();
        }

        return null;
    }

    @Override
    @Nullable
    protected String getSuppressCommentPrefix() {
        return SUPPRESS_COMMENT_PREFIX;
    }

    /**
     * @deprecated Use {@link #isSuppressedWithComment(PsiElement, Issue)} instead
     */
    @Deprecated
    public boolean isSuppressedWithComment(@NonNull Node scope, @NonNull Issue issue) {
        // Check whether there is a comment marker
        CharSequence contents = getContents();
        assert contents != null; // otherwise we wouldn't be here
        Position position = scope.getPosition();
        if (position == null) {
            return false;
        }

        int start = position.getStart();
        return isSuppressedWithComment(start, issue);
    }

    public boolean isSuppressedWithComment(@NonNull PsiElement scope, @NonNull Issue issue) {
        if (scope instanceof PsiCompiledElement) {
            return false;
        }

        // Check whether there is a comment marker
        CharSequence contents = getContents();
        assert contents != null; // otherwise we wouldn't be here
        TextRange textRange = scope.getTextRange();
        if (textRange == null) {
            return false;
        }
        int start = textRange.getStartOffset();
        return isSuppressedWithComment(start, issue);
    }

    public boolean isSuppressedWithComment(@NonNull UElement scope, @NonNull Issue issue) {
        PsiElement psi = scope.getPsi();
        return psi != null && isSuppressedWithComment(psi, issue);
    }

    /**
     * @deprecated Location handles aren't needed for AST nodes anymore; just use the
     * {@link PsiElement} from the AST
     */
    @Deprecated
    @NonNull
    public Location.Handle createLocationHandle(@NonNull Node node) {
        return parser.createLocationHandle(this, node);
    }

    /**
     * @deprecated Use PsiElement resolve methods (varies by AST node type, e.g.
     * {@link PsiMethodCallExpression#resolveMethod()}
     */
    @Deprecated
    @Nullable
    public ResolvedNode resolve(@NonNull Node node) {
        return parser.resolve(this, node);
    }

    /**
     * @deprecated Use {@link JavaEvaluator#findClass(String)} instead
     */
    @Deprecated
    @Nullable
    public ResolvedClass findClass(@NonNull String fullyQualifiedName) {
        return parser.findClass(this, fullyQualifiedName);
    }

    /**
     * @deprecated Use {@link PsiExpression#getType()} )} instead
     */
    @Deprecated
    @Nullable
    public TypeDescriptor getType(@NonNull Node node) {
        return parser.getType(this, node);
    }

    /**
     * @deprecated Use {@link #getMethodName(PsiElement)} instead
     */
    @Deprecated
    @Nullable
    public static String getMethodName(@NonNull Node call) {
        if (call instanceof MethodInvocation) {
            return ((MethodInvocation)call).astName().astValue();
        } else if (call instanceof ConstructorInvocation) {
            return ((ConstructorInvocation)call).astTypeReference().getTypeName();
        } else if (call instanceof EnumConstant) {
            return ((EnumConstant)call).astName().astValue();
        } else {
            return null;
        }
    }

    // Not necessary in UAST
    @Nullable
    public static String getMethodName(@NonNull PsiElement call) {
        if (call instanceof PsiMethodCallExpression) {
            return ((PsiMethodCallExpression)call).getMethodExpression().getReferenceName();
        } else if (call instanceof PsiNewExpression) {
            PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) call).getClassReference();
            if (classReference != null) {
                return classReference.getReferenceName();
            } else {
                return null;
            }
        } else if (call instanceof PsiEnumConstant) {
            return ((PsiEnumConstant)call).getName();
        } else {
            return null;
        }
    }

    // TODO: Move to LintUtils etc
    @Nullable
    public static String getMethodName(@NonNull UElement call) {
        if (call instanceof UEnumConstant) {
            return ((UEnumConstant)call).getName();
        } else if (call instanceof UCallExpression) {
            String methodName = ((UCallExpression) call).getMethodName();
            if (methodName != null) {
                return methodName;
            } else {
                return UastUtils.getQualifiedName(((UCallExpression) call).getClassReference());
            }
        } else {
            return null;
        }
    }

    /**
     * Searches for a name node corresponding to the given node
     * @return the name node to use, if applicable
     * @deprecated Use {@link #findNameElement(PsiElement)} instead
     */
    @Deprecated
    @Nullable
    public static Node findNameNode(@NonNull Node node) {
        if (node instanceof TypeDeclaration) {
            // ClassDeclaration, AnnotationDeclaration, EnumDeclaration, InterfaceDeclaration
            return ((TypeDeclaration) node).astName();
        } else if (node instanceof MethodDeclaration) {
            return ((MethodDeclaration)node).astMethodName();
        } else if (node instanceof ConstructorDeclaration) {
            return ((ConstructorDeclaration)node).astTypeName();
        } else if (node instanceof MethodInvocation) {
            return ((MethodInvocation)node).astName();
        } else if (node instanceof ConstructorInvocation) {
            return ((ConstructorInvocation)node).astTypeReference();
        } else if (node instanceof EnumConstant) {
            return ((EnumConstant)node).astName();
        } else if (node instanceof AnnotationElement) {
            return ((AnnotationElement)node).astName();
        } else if (node instanceof AnnotationMethodDeclaration) {
            return ((AnnotationMethodDeclaration)node).astMethodName();
        } else if (node instanceof VariableReference) {
            return ((VariableReference)node).astIdentifier();
        } else if (node instanceof LabelledStatement) {
            return ((LabelledStatement)node).astLabel();
        }

        return null;
    }

    /**
     * Searches for a name node corresponding to the given node
     * @return the name node to use, if applicable
     */
    @Nullable
    public static PsiElement findNameElement(@NonNull PsiElement element) {
        if (element instanceof PsiClass) {
            if (element instanceof PsiAnonymousClass) {
                return ((PsiAnonymousClass)element).getBaseClassReference();
            }
            return ((PsiClass) element).getNameIdentifier();
        } else if (element instanceof PsiMethod) {
            return ((PsiMethod) element).getNameIdentifier();
        } else if (element instanceof PsiMethodCallExpression) {
            return ((PsiMethodCallExpression) element).getMethodExpression().
                    getReferenceNameElement();
        } else if (element instanceof PsiNewExpression) {
            return ((PsiNewExpression) element).getClassReference();
        } else if (element instanceof PsiField) {
            return ((PsiField)element).getNameIdentifier();
        } else if (element instanceof PsiAnnotation) {
            return ((PsiAnnotation)element).getNameReferenceElement();
        } else if (element instanceof PsiReferenceExpression) {
            return ((PsiReferenceExpression) element).getReferenceNameElement();
        } else if (element instanceof PsiLabeledStatement) {
            return ((PsiLabeledStatement)element).getLabelIdentifier();
        }

        return null;
    }

    @Nullable
    public static UElement findNameElement(@NonNull UElement element) {
        if (element instanceof UDeclaration) {
            return ((UDeclaration) element).getUastAnchor();
        //} else if (element instanceof PsiNameIdentifierOwner) {
        //    return ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof UCallExpression) {
            return ((UCallExpression) element).getMethodIdentifier();
        }

        return null;
    }

    @Deprecated
    @NonNull
    public static Iterator<Expression> getParameters(@NonNull Node call) {
        if (call instanceof MethodInvocation) {
            return ((MethodInvocation) call).astArguments().iterator();
        } else if (call instanceof ConstructorInvocation) {
            return ((ConstructorInvocation) call).astArguments().iterator();
        } else if (call instanceof EnumConstant) {
            return ((EnumConstant) call).astArguments().iterator();
        } else {
            return Collections.emptyIterator();
        }
    }

    @Deprecated
    @Nullable
    public static Node getParameter(@NonNull Node call, int parameter) {
        Iterator<Expression> iterator = getParameters(call);

        for (int i = 0; i < parameter - 1; i++) {
            if (!iterator.hasNext()) {
                return null;
            }
            iterator.next();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * Returns true if the given method invocation node corresponds to a call on a
     * {@code android.content.Context}
     *
     * @param node the method call node
     * @return true iff the method call is on a class extending context
     * @deprecated use {@link JavaEvaluator#isMemberInSubClassOf(PsiMember, String, boolean)} instead
     */
    @Deprecated
    public boolean isContextMethod(@NonNull MethodInvocation node) {
        // Method name used in many other contexts where it doesn't have the
        // same semantics; only use this one if we can resolve types
        // and we're certain this is the Context method
        ResolvedNode resolved = resolve(node);
        if (resolved instanceof JavaParser.ResolvedMethod) {
            JavaParser.ResolvedMethod method = (JavaParser.ResolvedMethod) resolved;
            ResolvedClass containingClass = method.getContainingClass();
            if (containingClass.isSubclassOf(CLASS_CONTEXT, false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first ancestor node of the given type
     *
     * @param element the element to search from
     * @param clz     the target node type
     * @param <T>     the target node type
     * @return the nearest ancestor node in the parent chain, or null if not found
     * @deprecated Use {@link PsiTreeUtil#getParentOfType} instead
     */
    @Deprecated
    @Nullable
    public static <T extends Node> T getParentOfType(
            @Nullable Node element,
            @NonNull Class<T> clz) {
        return getParentOfType(element, clz, true);
    }

    /**
     * Returns the first ancestor node of the given type
     *
     * @param element the element to search from
     * @param clz     the target node type
     * @param strict  if true, do not consider the element itself, only its parents
     * @param <T>     the target node type
     * @return the nearest ancestor node in the parent chain, or null if not found
     * @deprecated Use {@link PsiTreeUtil#getParentOfType} instead
     */
    @Deprecated
    @Nullable
    public static <T extends Node> T getParentOfType(
            @Nullable Node element,
            @NonNull Class<T> clz,
            boolean strict) {
        if (element == null) {
            return null;
        }

        if (strict) {
            element = element.getParent();
        }

        while (element != null) {
            if (clz.isInstance(element)) {
                //noinspection unchecked
                return (T) element;
            }
            element = element.getParent();
        }

        return null;
    }

    /**
     * Returns the first ancestor node of the given type, stopping at the given type
     *
     * @param element     the element to search from
     * @param clz         the target node type
     * @param strict      if true, do not consider the element itself, only its parents
     * @param terminators optional node types to terminate the search at
     * @param <T>         the target node type
     * @return the nearest ancestor node in the parent chain, or null if not found
     * @deprecated Use {@link PsiTreeUtil#getParentOfType} instead
     */
    @Deprecated
    @Nullable
    public static <T extends Node> T getParentOfType(@Nullable Node element,
            @NonNull Class<T> clz,
            boolean strict,
            @NonNull Class<? extends Node>... terminators) {
        if (element == null) {
            return null;
        }
        if (strict) {
            element = element.getParent();
        }

        while (element != null && !clz.isInstance(element)) {
            for (Class<?> terminator : terminators) {
                if (terminator.isInstance(element)) {
                    return null;
                }
            }
            element = element.getParent();
        }

        //noinspection unchecked
        return (T) element;
    }

    /**
     * Returns the first sibling of the given node that is of the given class
     *
     * @param sibling the sibling to search from
     * @param clz     the type to look for
     * @param <T>     the type
     * @return the first sibling of the given type, or null
     * @deprecated Use {@link PsiTreeUtil#getNextSiblingOfType(PsiElement, Class)} instead
     */
    @Deprecated
    @Nullable
    public static <T extends Node> T getNextSiblingOfType(@Nullable Node sibling,
            @NonNull Class<T> clz) {
        if (sibling == null) {
            return null;
        }
        Node parent = sibling.getParent();
        if (parent == null) {
            return null;
        }

        Iterator<Node> iterator = parent.getChildren().iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == sibling) {
                break;
            }
        }

        while (iterator.hasNext()) {
            Node child = iterator.next();
            if (clz.isInstance(child)) {
                //noinspection unchecked
                return (T) child;
            }

        }

        return null;
    }


    /**
     * Returns the given argument of the given call
     *
     * @param call the call containing arguments
     * @param index the index of the target argument
     * @return the argument at the given index
     * @throws IllegalArgumentException if index is outside the valid range
     */
    @Deprecated
    @NonNull
    public static Node getArgumentNode(@NonNull MethodInvocation call, int index) {
        int i = 0;
        for (Expression parameter : call.astArguments()) {
            if (i == index) {
                return parameter;
            }
            i++;
        }
        throw new IllegalArgumentException(Integer.toString(index));
    }

    /** Whether this compilation unit is in a test folder */
    public boolean isTestSource() {
        return testSource;
    }

    /** Sets whether this compilation unit is in a test folder */
    public void setTestSource(boolean testSource) {
        this.testSource = testSource;
    }

    /** This method is marked as {@link NonNull} but that will only be the case from
     * UastScanners. It should never be called from old PSI or Lombok scanners. */
    @NonNull
    public UastContext getUastContext() {
        //noinspection ConstantConditions
        return uastParser.getUastContext();
    }
}
