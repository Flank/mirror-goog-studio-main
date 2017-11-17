/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.CLASS_CONTENTPROVIDER;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;
import static org.jetbrains.uast.UastUtils.getOutermostQualified;
import static org.jetbrains.uast.UastUtils.getParentOfType;
import static org.jetbrains.uast.UastUtils.getQualifiedChain;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UDoWhileExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UUnaryExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UWhileExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

/**
 * Checks for missing {@code recycle} calls on resources that encourage it, and
 * for missing {@code commit} calls on FragmentTransactions, etc.
 */
public class CleanupDetector extends Detector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            CleanupDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Problems with missing recycle calls */
    public static final Issue RECYCLE_RESOURCE = Issue.create(
        "Recycle",
        "Missing `recycle()` calls",

        "Many resources, such as TypedArrays, VelocityTrackers, etc., " +
        "should be recycled (with a `recycle()` call) after use. This lint check looks " +
        "for missing `recycle()` calls.",

        Category.PERFORMANCE,
        7,
        Severity.WARNING,
            IMPLEMENTATION);

    /** Problems with missing commit calls. */
    public static final Issue COMMIT_FRAGMENT = Issue.create(
            "CommitTransaction",
            "Missing `commit()` calls",

            "After creating a `FragmentTransaction`, you typically need to commit it as well",

            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Failing to commit a shared preference */
    public static final Issue SHARED_PREF = Issue.create(
            "CommitPrefEdits",
            "Missing `commit()` on `SharedPreference` editor",

            "After calling `edit()` on a `SharedPreference`, you must call `commit()` " +
            "or `apply()` on the editor to save the results.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    CleanupDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    /** Using commit instead of apply on a shared preference */
    public static final Issue APPLY_SHARED_PREF = Issue.create(
            "ApplySharedPref",
            "Use `apply()` on `SharedPreferences`",

            "Consider using `apply()` instead of `commit` on shared preferences. Whereas "
                    + "`commit` blocks and writes its data to persistent storage immediately, "
                    + "`apply` will handle it in the background.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    CleanupDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    // Target method names
    private static final String RECYCLE = "recycle";
    private static final String RELEASE = "release";
    private static final String OBTAIN = "obtain";
    private static final String SHOW = "show";
    private static final String ACQUIRE_CPC = "acquireContentProviderClient";
    private static final String OBTAIN_NO_HISTORY = "obtainNoHistory";
    private static final String OBTAIN_ATTRIBUTES = "obtainAttributes";
    private static final String OBTAIN_TYPED_ARRAY = "obtainTypedArray";
    private static final String OBTAIN_STYLED_ATTRIBUTES = "obtainStyledAttributes";
    private static final String BEGIN_TRANSACTION = "beginTransaction";
    private static final String COMMIT = "commit";
    private static final String COMMIT_NOW = "commitNow";
    private static final String APPLY = "apply";
    private static final String COMMIT_ALLOWING_LOSS = "commitAllowingStateLoss";
    private static final String COMMIT_NOW_ALLOWING_LOSS = "commitNowAllowingStateLoss";
    private static final String QUERY = "query";
    private static final String RAW_QUERY = "rawQuery";
    private static final String QUERY_WITH_FACTORY = "queryWithFactory";
    private static final String RAW_QUERY_WITH_FACTORY = "rawQueryWithFactory";
    private static final String CLOSE = "close";
    private static final String EDIT = "edit";

    public static final String MOTION_EVENT_CLS = "android.view.MotionEvent";
    private static final String PARCEL_CLS = "android.os.Parcel";
    private static final String VELOCITY_TRACKER_CLS = "android.view.VelocityTracker";
    private static final String DIALOG_FRAGMENT = "android.app.DialogFragment";
    private static final String DIALOG_V4_FRAGMENT =
            "android.support.v4.app.DialogFragment";
    private static final String FRAGMENT_MANAGER_CLS = "android.app.FragmentManager";
    private static final String FRAGMENT_MANAGER_V4_CLS =
            "android.support.v4.app.FragmentManager";
    private static final String FRAGMENT_TRANSACTION_CLS =
            "android.app.FragmentTransaction";
    private static final String FRAGMENT_TRANSACTION_V4_CLS =
            "android.support.v4.app.FragmentTransaction";

    public static final String SURFACE_CLS = "android.view.Surface";
    public static final String SURFACE_TEXTURE_CLS = "android.graphics.SurfaceTexture";

    public static final String CONTENT_PROVIDER_CLIENT_CLS
            = "android.content.ContentProviderClient";

    public static final String CONTENT_RESOLVER_CLS = "android.content.ContentResolver";

    @SuppressWarnings("SpellCheckingInspection")
    public static final String SQLITE_DATABASE_CLS = "android.database.sqlite.SQLiteDatabase";
    public static final String CURSOR_CLS = "android.database.Cursor";

    public static final String ANDROID_CONTENT_SHARED_PREFERENCES =
            "android.content.SharedPreferences";
    private static final String ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR =
            "android.content.SharedPreferences.Editor";

    /** Constructs a new {@link CleanupDetector} */
    public CleanupDetector() {
    }

    // ---- Implements UastScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                // FragmentManager commit check
                BEGIN_TRANSACTION,

                // Recycle check
                OBTAIN, OBTAIN_NO_HISTORY,
                OBTAIN_STYLED_ATTRIBUTES,
                OBTAIN_ATTRIBUTES,
                OBTAIN_TYPED_ARRAY,

                // Release check
                ACQUIRE_CPC,

                // Cursor close check
                QUERY, RAW_QUERY, QUERY_WITH_FACTORY, RAW_QUERY_WITH_FACTORY,

                // SharedPreferences check
                EDIT
        );
    }

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(SURFACE_TEXTURE_CLS, SURFACE_CLS);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        String name = method.getName();
        if (BEGIN_TRANSACTION.equals(name)) {
            checkTransactionCommits(context, call, method);
        } else if (EDIT.equals(name)) {
            checkEditorApplied(context, call, method);
        } else {
            checkResourceRecycled(context, call, method);
        }
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass != null) {
            String type = containingClass.getQualifiedName();
            if (type != null) {
                checkRecycled(context, node, type, RELEASE);
            }
        }
    }

    private static void checkResourceRecycled(@NonNull JavaContext context,
            @NonNull UCallExpression node, @NonNull PsiMethod method) {
        String name = method.getName();
        // Recycle detector
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        JavaEvaluator evaluator = context.getEvaluator();
        if ((OBTAIN.equals(name) || OBTAIN_NO_HISTORY.equals(name)) &&
                evaluator.extendsClass(containingClass, MOTION_EVENT_CLS, false)) {
            checkRecycled(context, node, MOTION_EVENT_CLS, RECYCLE);
        } else if (OBTAIN.equals(name) && evaluator.extendsClass(containingClass, PARCEL_CLS, false)) {
            checkRecycled(context, node, PARCEL_CLS, RECYCLE);
        } else if (OBTAIN.equals(name) &&
                evaluator.extendsClass(containingClass, VELOCITY_TRACKER_CLS, false)) {
            checkRecycled(context, node, VELOCITY_TRACKER_CLS, RECYCLE);
        } else if ((OBTAIN_STYLED_ATTRIBUTES.equals(name)
                || OBTAIN_ATTRIBUTES.equals(name)
                || OBTAIN_TYPED_ARRAY.equals(name)) &&
                (evaluator.extendsClass(containingClass, CLASS_CONTEXT, false) ||
                        evaluator.extendsClass(containingClass, SdkConstants.CLASS_RESOURCES, false))) {
            PsiType returnType = method.getReturnType();
            if (returnType instanceof PsiClassType) {
                PsiClass cls = ((PsiClassType)returnType).resolve();
                if (cls != null && SdkConstants.CLS_TYPED_ARRAY.equals(cls.getQualifiedName())) {
                    checkRecycled(context, node, SdkConstants.CLS_TYPED_ARRAY, RECYCLE);
                }
            }
        } else if (ACQUIRE_CPC.equals(name) && evaluator.extendsClass(containingClass,
                CONTENT_RESOLVER_CLS, false)) {
            checkRecycled(context, node, CONTENT_PROVIDER_CLIENT_CLS, RELEASE);
        } else if ((QUERY.equals(name)
                || RAW_QUERY.equals(name)
                || QUERY_WITH_FACTORY.equals(name)
                || RAW_QUERY_WITH_FACTORY.equals(name))
                && (evaluator.extendsClass(containingClass, SQLITE_DATABASE_CLS, false) ||
                    evaluator.extendsClass(containingClass, CONTENT_RESOLVER_CLS, false) ||
                    evaluator.extendsClass(containingClass, CLASS_CONTENTPROVIDER, false) ||
                    evaluator.extendsClass(containingClass, CONTENT_PROVIDER_CLIENT_CLS, false))) {
            // Other potential cursors-returning methods that should be tracked:
            //    android.app.DownloadManager#query
            //    android.content.ContentProviderClient#query
            //    android.content.ContentResolver#query
            //    android.database.sqlite.SQLiteQueryBuilder#query
            //    android.provider.Browser#getAllBookmarks
            //    android.provider.Browser#getAllVisitedUrls
            //    android.provider.DocumentsProvider#queryChildDocuments
            //    android.provider.DocumentsProvider#qqueryDocument
            //    android.provider.DocumentsProvider#queryRecentDocuments
            //    android.provider.DocumentsProvider#queryRoots
            //    android.provider.DocumentsProvider#querySearchDocuments
            //    android.provider.MediaStore$Images$Media#query
            //    android.widget.FilterQueryProvider#runQuery

            // If it's in a try-with-resources clause, don't flag it: these
            // will be cleaned up automatically
            UElement curr = node;
            while (curr != null) {
                PsiElement psi = curr.getPsi();
                if (psi != null) {
                    if (PsiTreeUtil.getParentOfType(psi, PsiResourceVariable.class) != null) {
                        return;
                    }
                    break;
                }
                curr = curr.getUastParent();
            }

            checkRecycled(context, node, CURSOR_CLS, CLOSE);
        }
    }

    private static void checkRecycled(@NonNull final JavaContext context,
            @NonNull UCallExpression node, @NonNull final String recycleType,
            @NonNull final String recycleName) {
        PsiVariable boundVariable = getVariableElement(node);
        if (boundVariable == null) {
            return;
        }

        UMethod method = getParentOfType(node, UMethod.class, true);
        if (method == null) {
            return;
        }
        FinishVisitor visitor = new FinishVisitor(context, boundVariable) {
            @Override
            protected boolean isCleanupCall(@NonNull UCallExpression call) {
                String methodName = call.getMethodName();

                if ("use".equals(methodName) && CLOSE.equals(recycleName)) {
                    // Kotlin: "use" calls close; see issue 62377185
                    // Can't call call.resolve() to check it's the runtime because
                    // resolve returns null on these usages.
                    // Now make sure we're calling it on the right variable
                    UExpression operand = call.getReceiver();
                    if (operand instanceof UReferenceExpression) {
                        PsiElement resolved = ((UReferenceExpression) operand).resolve();
                        //noinspection SuspiciousMethodCalls
                        if (resolved != null && mVariables.contains(resolved)) {
                            return true;
                        }
                    }
                }

                if (!recycleName.equals(methodName)) {
                    return false;
                }
                PsiMethod method = call.resolve();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (mContext.getEvaluator().extendsClass(containingClass, recycleType, false)) {
                        // Yes, called the right recycle() method; now make sure
                        // we're calling it on the right variable
                        UExpression operand = call.getReceiver();
                        if (operand instanceof UReferenceExpression) {
                            PsiElement resolved = ((UReferenceExpression) operand).resolve();
                            //noinspection SuspiciousMethodCalls
                            if (resolved != null && mVariables.contains(resolved)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        };

        method.accept(visitor);
        if (visitor.isCleanedUp() || visitor.variableEscapes()) {
            return;
        }

        String className = recycleType.substring(recycleType.lastIndexOf('.') + 1);
        String message;
        if (RECYCLE.equals(recycleName)) {
            message = String.format(
                    "This `%1$s` should be recycled after use with `#recycle()`", className);
        } else {
            message = String.format(
                    "This `%1$s` should be freed up after use with `#%2$s()`", className,
                    recycleName);
        }

        UElement locationNode = node.getMethodIdentifier();
        if (locationNode == null) {
            locationNode = node;
        }
        Location location = context.getLocation(locationNode);
        context.report(RECYCLE_RESOURCE, node, location, message);
    }

    private static void checkTransactionCommits(@NonNull JavaContext context,
            @NonNull UCallExpression node, @NonNull PsiMethod calledMethod) {
        if (isBeginTransaction(context, calledMethod)) {
            PsiVariable boundVariable = getVariableElement(node, true, true);
            if (isCommittedInChainedCalls(context, node)) {
                return;
            }

            if (boundVariable != null) {
                UMethod method = getParentOfType(node, UMethod.class, true);
                if (method == null) {
                    return;
                }

                FinishVisitor commitVisitor = new FinishVisitor(context, boundVariable) {
                    @Override
                    protected boolean isCleanupCall(@NonNull UCallExpression call) {
                        if (isTransactionCommitMethodCall(mContext, call)) {
                            List<UExpression> chain = getQualifiedChain(getOutermostQualified(call));
                            if (chain.isEmpty()) {
                                return false;
                            }

                            UExpression operand = chain.get(0);
                            if (operand != null) {
                                PsiElement resolved = UastUtils.tryResolve(operand);
                                //noinspection SuspiciousMethodCalls
                                if (resolved != null && mVariables.contains(resolved)) {
                                    return true;
                                } else if (resolved instanceof PsiMethod
                                        && operand instanceof UCallExpression
                                        && isCommittedInChainedCalls(mContext,
                                        (UCallExpression) operand)) {
                                    // Check that the target of the committed chains is the
                                    // right variable!
                                    while (operand instanceof UCallExpression) {
                                        operand = ((UCallExpression) operand).getReceiver();
                                    }
                                    if (operand instanceof UReferenceExpression) {
                                        resolved = ((UReferenceExpression) operand).resolve();
                                        //noinspection SuspiciousMethodCalls
                                        if (resolved != null && mVariables.contains(resolved)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        } else if (isShowFragmentMethodCall(mContext, call)) {
                            List<UExpression> arguments = call.getValueArguments();
                            if (arguments.size() == 2) {
                                UExpression first = arguments.get(0);
                                PsiElement resolved = UastUtils.tryResolve(first);
                                //noinspection SuspiciousMethodCalls
                                if (resolved != null && mVariables.contains(resolved)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                };

                method.accept(commitVisitor);
                if (commitVisitor.isCleanedUp() || commitVisitor.variableEscapes()) {
                    return;
                }
            }

            String message = "This transaction should be completed with a `commit()` call";
            context.report(COMMIT_FRAGMENT, node, context.getNameLocation(node), message);
        }
    }

    private static boolean isCommittedInChainedCalls(@NonNull JavaContext context,
            @NonNull UCallExpression node) {
        // Look for chained calls since the FragmentManager methods all return "this"
        // to allow constructor chaining, e.g.
        //    getFragmentManager().beginTransaction().addToBackStack("test")
        //            .disallowAddToBackStack().hide(mFragment2).setBreadCrumbShortTitle("test")
        //            .show(mFragment2).setCustomAnimations(0, 0).commit();
        List<UExpression> chain = getQualifiedChain(getOutermostQualified(node));
        if (!chain.isEmpty()) {
            UExpression lastExpression = chain.get(chain.size() - 1);
            if (lastExpression instanceof UCallExpression) {
                UCallExpression methodInvocation = (UCallExpression) lastExpression;
                if (isTransactionCommitMethodCall(context, methodInvocation)
                        || isShowFragmentMethodCall(context, methodInvocation)) {
                    return true;
                }
            }
        }

        UCallExpression parentCall = UastUtils.getParentOfType(node, UCallExpression.class, true);
        if (parentCall != null) {
            String methodName = parentCall.getMethodName();
            if (methodName == null) {
                UIdentifier methodIdentifier = parentCall.getMethodIdentifier();
                if (methodIdentifier != null && "with".equals(methodIdentifier.getName())) {
                    List<UExpression> args = parentCall.getValueArguments();
                    if (args.size() == 2 && args.get(1) instanceof ULambdaExpression) {
                        UExpression body = ((ULambdaExpression) args.get(1)).getBody();
                        // Can't use FinishVisitor since inside a with-block we don't have
                        // a variable binding
                        AtomicBoolean ref = new AtomicBoolean(false);
                        body.accept(new AbstractUastVisitor() {
                            @Override
                            public boolean visitCallExpression(UCallExpression node) {
                                /* There's a bug in UAST where none of these lambda nodes
                                   are valid, so for now, treat all "with" statements
                                   as implicitly clearing; this isn't right, but avoids
                                   likely false positives. Tracked in issue 69407565.

                                if (isTransactionCommitMethodCall(context, node)) {
                                    ref.set(true);
                                }
                                */
                                ref.set(true);

                                return super.visitCallExpression(node);

                            }
                        });
                        //noinspection RedundantIfStatement
                        if (ref.get()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isTransactionCommitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call) {

        String methodName = call.getMethodName();
        return (COMMIT.equals(methodName)
                    || COMMIT_ALLOWING_LOSS.equals(methodName)
                    || COMMIT_NOW_ALLOWING_LOSS.equals(methodName)
                    || COMMIT_NOW.equals(methodName)) &&
                isMethodOnFragmentClass(context, call,
                        FRAGMENT_TRANSACTION_CLS,
                        FRAGMENT_TRANSACTION_V4_CLS,
                        true);
    }

    private static boolean isShowFragmentMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        return SHOW.equals(methodName)
                && isMethodOnFragmentClass(context, call,
                DIALOG_FRAGMENT, DIALOG_V4_FRAGMENT, true);
    }

    private static boolean isMethodOnFragmentClass(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String fragmentClass,
            @NonNull String v4FragmentClass,
            boolean returnForUnresolved) {
        PsiMethod method = call.resolve();
        if (method != null) {
            PsiClass containingClass = method.getContainingClass();
            JavaEvaluator evaluator = context.getEvaluator();
            return evaluator.extendsClass(containingClass, fragmentClass, false) ||
                    evaluator.extendsClass(containingClass, v4FragmentClass, false);
        } else {
            // If we *can't* resolve the method call, caller can decide
            // whether to consider the method called or not
            return returnForUnresolved;
        }
    }

    private void checkEditorApplied(@NonNull JavaContext context,
            @NonNull UCallExpression node, @NonNull PsiMethod calledMethod) {
        if (isSharedEditorCreation(context, calledMethod)) {
            PsiVariable boundVariable = getVariableElement(node, true, true);
            if (isEditorCommittedInChainedCalls(context, node)) {
                return;
            }

            if (boundVariable != null) {
                UMethod method = getParentOfType(node, UMethod.class, true);
                if (method == null) {
                    return;
                }

                FinishVisitor commitVisitor = new FinishVisitor(context, boundVariable) {
                    @Override
                    protected boolean isCleanupCall(@NonNull UCallExpression call) {
                        if (isEditorApplyMethodCall(mContext, call)
                                || isEditorCommitMethodCall(mContext, call)) {
                            List<UExpression> chain = getQualifiedChain(getOutermostQualified(call));
                            if (chain.isEmpty()) {
                                return false;
                            }

                            UExpression operand = chain.get(0);
                            if (operand != null) {
                                PsiElement resolved = UastUtils.tryResolve(operand);
                                //noinspection SuspiciousMethodCalls
                                if (resolved != null && mVariables.contains(resolved)) {
                                    return true;
                                } else if (resolved instanceof PsiMethod
                                        && operand instanceof UCallExpression
                                        && isEditorCommittedInChainedCalls(mContext,
                                        (UCallExpression) operand)) {
                                    // Check that the target of the committed chains is the
                                    // right variable!
                                    while (operand instanceof UCallExpression) {
                                        operand = ((UCallExpression)operand).getReceiver();
                                    }
                                    if (operand instanceof UReferenceExpression) {
                                        resolved = ((UReferenceExpression) operand).resolve();
                                        //noinspection SuspiciousMethodCalls
                                        if (resolved != null && mVariables.contains(resolved)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                        return false;
                    }
                };

                method.accept(commitVisitor);
                if (commitVisitor.isCleanedUp() || commitVisitor.variableEscapes()) {
                    return;
                }
            } else if (UastUtils.getParentOfType(node, UReturnExpression.class) != null) {
                // Allocation is in a return statement
                return;
            }

            String message = "`SharedPreferences.edit()` without a corresponding `commit()` or "
                    + "`apply()` call";
            context.report(SHARED_PREF, node, context.getLocation(node), message);
        }
    }

    private static boolean isSharedEditorCreation(@NonNull JavaContext context,
            @NonNull PsiMethod method) {
        String methodName = method.getName();
        if (EDIT.equals(methodName)) {
            PsiClass containingClass = method.getContainingClass();
            JavaEvaluator evaluator = context.getEvaluator();
            return containingClass != null &&
                    evaluator.implementsInterface(containingClass,
                            ANDROID_CONTENT_SHARED_PREFERENCES,false) &&
                    evaluator.typeMatches(method.getReturnType(),
                            ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR);
        }

        return false;
    }

    private boolean isEditorCommittedInChainedCalls(@NonNull JavaContext context,
            @NonNull UCallExpression node) {
        List<UExpression> chain = getQualifiedChain(getOutermostQualified(node));
        if (!chain.isEmpty()) {
            UExpression lastExpression = chain.get(chain.size() - 1);
            if (lastExpression instanceof UCallExpression) {
                UCallExpression methodInvocation = (UCallExpression) lastExpression;
                if (isEditorCommitMethodCall(context, methodInvocation)
                        || isEditorApplyMethodCall(context, methodInvocation)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isEditorCommitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (COMMIT.equals(methodName)) {
            PsiMethod method = call.resolve();
            if (method != null) {
                PsiClass containingClass = method.getContainingClass();
                JavaEvaluator evaluator = context.getEvaluator();
                if (evaluator.extendsClass(containingClass,
                        ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR, false)) {
                    suggestApplyIfApplicable(context, call);
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isEditorApplyMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (APPLY.equals(methodName)) {
            PsiMethod method = call.resolve();
            if (method != null) {
                PsiClass containingClass = method.getContainingClass();
                JavaEvaluator evaluator = context.getEvaluator();
                return evaluator.extendsClass(containingClass,
                        ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR, false);
            }
        }

        return false;
    }

    private void suggestApplyIfApplicable(@NonNull JavaContext context,
            @NonNull UCallExpression node) {
        if (context.getProject().getMinSdkVersion().getApiLevel() >= 9) {
            // See if the return value is read: can only replace commit with
            // apply if the return value is not considered

            UElement qualifiedNode = node;
            UElement parent = skipParentheses(node.getUastParent());
            while (parent instanceof UReferenceExpression) {
                qualifiedNode = parent;
                parent = skipParentheses(parent.getUastParent());
            }
            boolean returnValueIgnored = true;

            if (parent instanceof UCallExpression
                    || parent instanceof UVariable
                    || parent instanceof UPolyadicExpression
                    || parent instanceof UUnaryExpression
                    || parent instanceof UReturnExpression) {
                returnValueIgnored = false;
            } else if (parent instanceof UIfExpression) {
                UExpression condition = ((UIfExpression) parent).getCondition();
                returnValueIgnored = !condition.equals(qualifiedNode);
            } else if (parent instanceof UWhileExpression) {
                UExpression condition = ((UWhileExpression) parent).getCondition();
                returnValueIgnored = !condition.equals(qualifiedNode);
            } else if (parent instanceof UDoWhileExpression) {
                UExpression condition = ((UDoWhileExpression) parent).getCondition();
                returnValueIgnored = !condition.equals(qualifiedNode);
            }

            if (returnValueIgnored) {
                String message = "Consider using `apply()` instead; `commit` writes "
                        + "its data to persistent storage immediately, whereas "
                        + "`apply` will handle it in the background";
                Location location = context.getLocation(node);
                LintFix fix = fix().name("Replace commit() with apply()").replace()
                        .pattern("(commit)\\s*\\(").with("apply").build();
                context.report(APPLY_SHARED_PREF, node, location, message, fix);
            }
        }
    }

    /** Returns the variable the expression is assigned to, if any */
    @Nullable
    public static PsiVariable getVariableElement(@NonNull UCallExpression rhs) {
        return getVariableElement(rhs, false, false);
    }

    @Nullable
    public static PsiVariable getVariableElement(@NonNull UCallExpression rhs,
            boolean allowChainedCalls, boolean allowFields) {
        UElement parent = skipParentheses(
                UastUtils.getQualifiedParentOrThis(rhs).getUastParent());

        // Handle some types of chained calls; e.g. you might have
        //    var = prefs.edit().put(key,value)
        // and here we want to skip past the put call
        if (allowChainedCalls) {
            while (true) {
                if ((parent instanceof UQualifiedReferenceExpression)) {
                    UElement parentParent = skipParentheses(parent.getUastParent());
                    if ((parentParent instanceof UQualifiedReferenceExpression)) {
                        parent = skipParentheses(parentParent.getUastParent());
                    } else if (parentParent instanceof UVariable
                            || parentParent instanceof UPolyadicExpression) {
                        parent = parentParent;
                        break;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        if (UastExpressionUtils.isAssignment(parent)) {
            UBinaryExpression assignment = (UBinaryExpression) parent;
            assert assignment != null;
            UExpression lhs = assignment.getLeftOperand();
            if (lhs instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) lhs).resolve();
                if (resolved instanceof PsiVariable
                        && (allowFields || !(resolved instanceof PsiField))) {
                    // e.g. local variable, parameter - but not a field
                    return ((PsiVariable) resolved);
                }
            }
        } else if (parent instanceof UVariable
                && (allowFields || !(parent instanceof UField))) {
            return ((UVariable) parent).getPsi();
        }

        return null;
    }

    private static boolean isBeginTransaction(@NonNull JavaContext context, @NonNull PsiMethod method) {
        String methodName = method.getName();
        if (BEGIN_TRANSACTION.equals(methodName)) {
            PsiClass containingClass = method.getContainingClass();
            JavaEvaluator evaluator = context.getEvaluator();
            if (evaluator.extendsClass(containingClass, FRAGMENT_MANAGER_CLS, false)
                    || evaluator.extendsClass(containingClass, FRAGMENT_MANAGER_V4_CLS, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Visitor which checks whether an operation is "finished"; in the case
     * of a FragmentTransaction we're looking for a "commit" call; in the
     * case of a TypedArray we're looking for a "recycle", call, in the
     * case of a database cursor we're looking for a "close" call, etc.
     */
    private abstract static class FinishVisitor extends AbstractUastVisitor {
        protected final JavaContext mContext;
        protected final List<PsiVariable> mVariables;
        private final PsiVariable mOriginalVariableNode;

        private boolean mContainsCleanup;
        private boolean mEscapes;

        public FinishVisitor(JavaContext context, @NonNull PsiVariable variableNode) {
            mContext = context;
            mOriginalVariableNode = variableNode;
            mVariables = Lists.newArrayList(variableNode);
        }

        public boolean isCleanedUp() {
            return mContainsCleanup;
        }

        public boolean variableEscapes() {
            return mEscapes;
        }

        @Override
        public boolean visitElement(UElement node) {
            return mContainsCleanup || super.visitElement(node);
        }

        protected abstract boolean isCleanupCall(@NonNull UCallExpression call);

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            if (node.getKind() == UastCallKind.METHOD_CALL) {
                visitMethodCallExpression(node);
            }
            return super.visitCallExpression(node);
        }

        private void visitMethodCallExpression(UCallExpression call) {
            if (mContainsCleanup) {
                return;
            }

            // Look for escapes
            if (!mEscapes) {
                for (UExpression expression : call.getValueArguments()) {
                    if (expression instanceof UReferenceExpression) {
                        PsiElement resolved = ((UReferenceExpression) expression).resolve();
                        //noinspection SuspiciousMethodCalls
                        if (resolved != null && mVariables.contains(resolved)) {
                            boolean wasEscaped = mEscapes;
                            mEscapes = true;

                            // Special case: MotionEvent.obtain(MotionEvent): passing in an
                            // event here does not recycle the event, and we also know it
                            // doesn't escape
                            if (OBTAIN.equals(call.getMethodName())) {
                                PsiMethod method = call.resolve();
                                if (mContext.getEvaluator().
                                        isMemberInClass(method, MOTION_EVENT_CLS)) {
                                    mEscapes = wasEscaped;
                                }
                            }
                        }
                    }
                }
            }

            if (isCleanupCall(call)) {
                mContainsCleanup = true;
            }
        }

        @Override
        public boolean visitVariable(UVariable variable) {
            if (variable instanceof ULocalVariable) {
                UExpression initializer = variable.getUastInitializer();
                if (initializer instanceof UReferenceExpression) {
                    PsiElement resolved = ((UReferenceExpression) initializer).resolve();
                    //noinspection SuspiciousMethodCalls
                    if (resolved != null && mVariables.contains(resolved)) {
                        mVariables.add(variable.getPsi());
                    }
                }
            }

            return super.visitVariable(variable);
        }

        @Override
        public boolean visitBinaryExpression(UBinaryExpression expression) {
            if (!UastExpressionUtils.isAssignment(expression)) {
                return super.visitBinaryExpression(expression);
            }

            // TEMPORARILY DISABLED; see testDatabaseCursorReassignment
            // This can result in some false positives right now. Play it
            // safe instead.
            boolean clearLhs = false;

            UExpression rhs = expression.getRightOperand();
            if (rhs instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) rhs).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    clearLhs = false;
                    PsiElement lhs = UastUtils.tryResolve(expression.getLeftOperand());
                    if (lhs instanceof PsiLocalVariable) {
                        mVariables.add(((PsiLocalVariable) lhs));
                    } else if (lhs instanceof PsiField) {
                        mEscapes = true;
                    }
                }
            }

            //noinspection ConstantConditions
            if (clearLhs) {
                // If we reassign one of the variables, clear it out
                PsiElement lhs = UastUtils.tryResolve(expression.getLeftOperand());
                //noinspection SuspiciousMethodCalls
                if (lhs != null && !lhs.equals(mOriginalVariableNode)
                        && mVariables.contains(lhs)) {
                    //noinspection SuspiciousMethodCalls
                    mVariables.remove(lhs);
                }
            }

            return super.visitBinaryExpression(expression);
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) returnValue).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    mEscapes = true;
                }
            }

            return super.visitReturnExpression(node);
        }
    }
}
