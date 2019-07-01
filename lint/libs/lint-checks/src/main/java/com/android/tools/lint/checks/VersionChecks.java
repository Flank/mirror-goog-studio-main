package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.Lint.getMethodName;
import static com.android.tools.lint.detector.api.Lint.skipParentheses;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Lint;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.USwitchClauseExpressionWithBody;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UUnaryExpression;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.UastContext;
import org.jetbrains.uast.UastPrefixOperator;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

/**
 * Utility methods for checking whether a given element is surrounded (or preceded!) by an API check
 * using SDK_INT (or other version checking utilities such as BuildCompat#isAtLeastN)
 */
public class VersionChecks {

    private interface ApiLevelLookup {
        int getApiLevel(@NonNull UElement element);
    }

    public static final String SDK_INT = "SDK_INT";
    private static final String ANDROID_OS_BUILD_VERSION = "android/os/Build$VERSION";
    /** SDK int method used by the data binding compiler */
    private static final String GET_BUILD_SDK_INT = "getBuildSdkInt";

    public static int codeNameToApi(@NonNull String text) {
        int dotIndex = text.lastIndexOf('.');
        if (dotIndex != -1) {
            text = text.substring(dotIndex + 1);
        }

        return SdkVersionInfo.getApiByBuildCode(text, true);
    }

    public static boolean isPrecededByVersionCheckExit(
            @NonNull UElement element, int api, boolean isLowerBound) {
        //noinspection unchecked
        UExpression currentExpression =
                UastUtils.getParentOfType(
                        element, UExpression.class, true, UMethod.class, UClass.class);

        while (currentExpression != null) {
            VersionCheckWithExitFinder visitor =
                    new VersionCheckWithExitFinder(element, api, isLowerBound);
            currentExpression.accept(visitor);

            if (visitor.found()) {
                return true;
            }

            element = currentExpression;
            //noinspection unchecked
            currentExpression =
                    UastUtils.getParentOfType(
                            currentExpression,
                            UExpression.class,
                            true,
                            UMethod.class,
                            UClass.class); // TODO: what about lambdas?
        }

        return false;
    }

    private static class VersionCheckWithExitFinder extends AbstractUastVisitor {

        private final UElement endElement;
        private final int api;
        private final boolean isLowerBound;

        private boolean found = false;
        private boolean done = false;

        public VersionCheckWithExitFinder(UElement endElement, int api, boolean isLowerBound) {
            this.endElement = endElement;
            this.api = api;
            this.isLowerBound = isLowerBound;
        }

        @Override
        public boolean visitElement(@NonNull UElement node) {
            if (done) {
                return true;
            }

            if (node.equals(endElement)) {
                done = true;
            }

            return done;
        }

        @Override
        public boolean visitIfExpression(@NonNull UIfExpression ifStatement) {

            if (done) {
                return true;
            }

            UExpression thenBranch = ifStatement.getThenExpression();
            UExpression elseBranch = ifStatement.getElseExpression();

            if (thenBranch != null) {
                Boolean level =
                        isVersionCheckConditional(
                                api, isLowerBound, ifStatement.getCondition(), false, null, null);

                if (level != null && level) {
                    // See if the body does an immediate return
                    if (isUnconditionalReturn(thenBranch)) {
                        found = true;
                        done = true;
                    }
                }
            }

            if (elseBranch != null) {
                Boolean level =
                        isVersionCheckConditional(
                                api, isLowerBound, ifStatement.getCondition(), true, null, null);

                if (level != null && level) {
                    if (isUnconditionalReturn(elseBranch)) {
                        found = true;
                        done = true;
                    }
                }
            }

            return true;
        }

        public boolean found() {
            return found;
        }
    }

    private static boolean isUnconditionalReturn(UExpression statement) {
        if (statement instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) statement).getExpressions();
            int statements = expressions.size();
            if (statements > 0) {
                UExpression last = expressions.get(statements - 1);
                if (last instanceof UReturnExpression || last instanceof UThrowExpression) {
                    return true;
                } else if (last instanceof UCallExpression) {
                    UCallExpression call = (UCallExpression) last;
                    String methodName = getMethodName(call);
                    // Look for Kotlin runtime library methods that unconditionally exit
                    if ("error".equals(methodName) || "TODO".equals(methodName)) {
                        return true;
                    }
                }
            }
        }
        return statement instanceof UReturnExpression;
    }

    public static boolean isWithinVersionCheckConditional(
            @NonNull JavaEvaluator evaluator,
            @NonNull UElement element,
            int api,
            boolean isLowerBound) {
        return isWithinVersionCheckConditional(evaluator, element, api, isLowerBound, null);
    }

    public static boolean isWithinVersionCheckConditional(
            @NonNull JavaEvaluator evaluator,
            @NonNull UElement element,
            int api,
            boolean isLowerBound,
            @Nullable ApiLevelLookup apiLookup) {
        UElement current = skipParentheses(element.getUastParent());
        UElement prev = element;
        while (current != null) {
            if (current instanceof UIfExpression) {
                UIfExpression ifStatement = (UIfExpression) current;
                UExpression condition = ifStatement.getCondition();
                if (prev != condition) {
                    boolean fromThen = prev.equals(ifStatement.getThenExpression());
                    Boolean ok =
                            isVersionCheckConditional(
                                    api, isLowerBound, condition, fromThen, prev, apiLookup);
                    if (ok != null && ok) {
                        return true;
                    }
                }
            } else if (current instanceof UPolyadicExpression
                    && (isAndedWithConditional(current, api, isLowerBound, prev)
                            || isOredWithConditional(current, api, isLowerBound, prev))) {
                return true;
            } else if (current instanceof USwitchClauseExpressionWithBody) {
                USwitchClauseExpressionWithBody body = (USwitchClauseExpressionWithBody) current;
                for (UExpression condition : body.getCaseValues()) {
                    Boolean ok =
                            isVersionCheckConditional(
                                    api, isLowerBound, condition, true, prev, apiLookup);
                    if (ok != null && ok) {
                        return true;
                    }
                }
            } else if (current instanceof UCallExpression && prev instanceof ULambdaExpression) {
                // If the API violation is in a lambda that is passed to a method,
                // see if the lambda parameter is invoked inside that method, wrapped within
                // a suitable version conditional.
                //
                // Optionally also see if we're passing in the API level as a parameter
                // to the function.
                //
                // Algorithm:
                //  (1) Figure out which parameter we're mapping the lambda argument to.
                //  (2) Find that parameter invoked within the function
                //  (3) From the invocation see if it's a suitable version conditional
                //

                UCallExpression call = (UCallExpression) current;
                PsiMethod method = call.resolve();
                if (method != null) {
                    Map<UExpression, PsiParameter> mapping =
                            evaluator.computeArgumentMapping(call, method);
                    PsiParameter parameter = mapping.get(prev);
                    if (parameter != null) {
                        UastContext context = UastUtils.getUastContext(element);
                        UMethod uMethod = context.getMethod(method);
                        Ref<UCallExpression> match = new Ref<>();
                        String parameterName = parameter.getName();
                        uMethod.accept(
                                new AbstractUastVisitor() {
                                    @Override
                                    public boolean visitCallExpression(
                                            @NonNull UCallExpression node) {
                                        String callName = Lint.getMethodName(node);
                                        if (Objects.equals(callName, parameterName)) {
                                            // Potentially not correct due to scopes, but these lambda
                                            // utility methods tend to be short and for lambda function
                                            // calls, resolve on call returns null
                                            match.set(node);
                                        }
                                        return super.visitCallExpression(node);
                                    }
                                });
                        UCallExpression lambdaInvocation = match.get();
                        ApiLevelLookup newApiLookup =
                                arg -> {
                                    if (arg instanceof UReferenceExpression) {
                                        PsiElement resolved =
                                                ((UReferenceExpression) arg).resolve();
                                        if (resolved instanceof PsiParameter) {
                                            PsiParameter parameter1 = (PsiParameter) resolved;
                                            PsiParameterList parameterList =
                                                    PsiTreeUtil.getParentOfType(
                                                            resolved, PsiParameterList.class);
                                            if (parameterList != null) {
                                                int index =
                                                        parameterList.getParameterIndex(parameter1);
                                                List<UExpression> arguments =
                                                        call.getValueArguments();
                                                if (index != -1 && index < arguments.size()) {
                                                    return getApiLevel(arguments.get(index), null);
                                                }
                                            }
                                        }
                                    }
                                    return -1;
                                };
                        if (lambdaInvocation != null
                                && isWithinVersionCheckConditional(
                                        evaluator,
                                        lambdaInvocation,
                                        api,
                                        isLowerBound,
                                        newApiLookup)) {
                            return true;
                        }
                    }
                }
            } else if (current instanceof UMethod) {
                UElement parent = current.getUastParent();
                if (!(parent instanceof UAnonymousClass)) {
                    return false;
                }
            } else if (current instanceof PsiFile) {
                return false;
            }
            prev = current;
            current = skipParentheses(current.getUastParent());
        }

        return false;
    }

    @Nullable
    private static Boolean isVersionCheckConditional(
            int api,
            boolean isLowerBound,
            @NonNull UElement element,
            boolean and,
            @Nullable UElement prev,
            @Nullable ApiLevelLookup apiLookup) {
        if (element instanceof UPolyadicExpression) {
            if (element instanceof UBinaryExpression) {
                UBinaryExpression binary = (UBinaryExpression) element;
                Boolean ok = isVersionCheckConditional(api, isLowerBound, and, binary, apiLookup);
                if (ok != null) {
                    return ok;
                }
            }
            UPolyadicExpression expression = (UPolyadicExpression) element;
            UastBinaryOperator tokenType = expression.getOperator();
            if (and && tokenType == UastBinaryOperator.LOGICAL_AND) {
                if (isAndedWithConditional(element, api, isLowerBound, prev)) {
                    return true;
                }

            } else if (!and && tokenType == UastBinaryOperator.LOGICAL_OR) {
                if (isOredWithConditional(element, api, isLowerBound, prev)) {
                    return true;
                }
            }
        } else if (element instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) element;
            return isValidVersionCall(api, isLowerBound, and, call);
        } else if (element instanceof UReferenceExpression) {
            // Constant expression for an SDK version check?
            UReferenceExpression refExpression = (UReferenceExpression) element;
            PsiElement resolved = refExpression.resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiModifierList modifierList = field.getModifierList();
                if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                    UastContext context = UastUtils.getUastContext(element);
                    UExpression initializer = context.getInitializerBody(field);
                    if (initializer != null) {
                        Boolean ok =
                                isVersionCheckConditional(
                                        api, isLowerBound, initializer, and, null, null);
                        if (ok != null) {
                            return ok;
                        }
                    }
                }
            } else if (resolved instanceof PsiMethod
                    && element instanceof UQualifiedReferenceExpression
                    && ((UQualifiedReferenceExpression) element).getSelector()
                            instanceof UCallExpression) {
                UCallExpression call =
                        (UCallExpression) ((UQualifiedReferenceExpression) element).getSelector();
                return isValidVersionCall(api, isLowerBound, and, call);
            } else if (resolved instanceof PsiMethod
                    && element instanceof UQualifiedReferenceExpression
                    && ((UQualifiedReferenceExpression) element).getReceiver()
                            instanceof UReferenceExpression) {
                // Method call via Kotlin property syntax
                return isValidVersionCall(api, isLowerBound, and, element, (PsiMethod) resolved);
            }
        } else if (element instanceof UUnaryExpression) {
            UUnaryExpression prefixExpression = (UUnaryExpression) element;
            if (prefixExpression.getOperator() == UastPrefixOperator.LOGICAL_NOT) {
                UExpression operand = prefixExpression.getOperand();
                Boolean ok =
                        isVersionCheckConditional(api, isLowerBound, operand, !and, null, null);
                if (ok != null) {
                    return ok;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Boolean isValidVersionCall(
            int api, boolean isLowerBound, boolean and, UCallExpression call) {
        PsiMethod method = call.resolve();
        if (method == null) {
            return null;
        }
        return isValidVersionCall(api, isLowerBound, and, call, method);
    }

    @Nullable
    private static Boolean isValidVersionCall(
            int api,
            boolean isLowerBound,
            boolean and,
            @NonNull UElement call,
            @NonNull PsiMethod method) {
        String name = method.getName();
        if (name.startsWith("isAtLeast") && isLowerBound) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null
                    // android.support.v4.os.BuildCompat,
                    // androidx.core.os.BuildCompat
                    && "BuildCompat".equals(containingClass.getName())) {
                if (name.equals("isAtLeastN")) {
                    return api <= 24;
                } else if (name.equals("isAtLeastNMR1")) {
                    return api <= 25;
                } else if (name.equals("isAtLeastO")) {
                    return api <= 26;
                } else if (name.startsWith("isAtLeastP")) {
                    return api <= 28;
                } else if (name.startsWith("isAtLeastQ")) {
                    return api <= 29;
                } else if (name.startsWith("isAtLeast")
                        && name.length() == 10
                        && Character.isUpperCase(name.charAt(9))
                        && name.charAt(9) > 'Q') {
                    // Try to guess future API levels before they're announced
                    return api <= SdkVersionInfo.HIGHEST_KNOWN_API + 1;
                }
            }
        }

        int version = getMinSdkVersionFromMethodName(name);
        if (version != -1 && isLowerBound) {
            return api <= version;
        }

        // Unconditional version utility method? If so just attempt to call it
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            UastContext context = UastUtils.getUastContext(call);
            UExpression body = context.getMethodBody(method);
            if (body == null) {
                return null;
            }
            List<UExpression> expressions;
            if (body instanceof UBlockExpression) {
                expressions = ((UBlockExpression) body).getExpressions();
            } else {
                expressions = Collections.singletonList(body);
            }

            if (expressions.size() == 1) {
                UExpression statement = expressions.get(0);
                UExpression returnValue = null;
                if (statement instanceof UReturnExpression) {
                    UReturnExpression returnStatement = (UReturnExpression) statement;
                    returnValue = returnStatement.getReturnExpression();
                } else if (statement != null) {
                    // Kotlin: may not have an explicit return statement
                    returnValue = statement;
                }
                if (returnValue != null) {
                    List<UExpression> arguments =
                            call instanceof UCallExpression
                                    ? ((UCallExpression) call).getValueArguments()
                                    :
                                    // Property syntax
                                    Collections.emptyList();
                    if (arguments.isEmpty()) {
                        if (returnValue instanceof UPolyadicExpression
                                || returnValue instanceof UCallExpression
                                || returnValue instanceof UQualifiedReferenceExpression) {
                            Boolean isConditional =
                                    isVersionCheckConditional(
                                            api, isLowerBound, returnValue, and, null, null);
                            if (isConditional != null) {
                                return isConditional;
                            }
                        }
                    } else if (arguments.size() == 1) {
                        // See if we're passing in a value to the version utility method
                        ApiLevelLookup lookup =
                                arg -> {
                                    if (arg instanceof UReferenceExpression) {
                                        PsiElement resolved =
                                                ((UReferenceExpression) arg).resolve();
                                        if (resolved instanceof PsiParameter) {
                                            PsiParameter parameter = (PsiParameter) resolved;
                                            PsiParameterList parameterList =
                                                    PsiTreeUtil.getParentOfType(
                                                            resolved, PsiParameterList.class);
                                            if (parameterList != null) {
                                                int index =
                                                        parameterList.getParameterIndex(parameter);
                                                if (index != -1 && index < arguments.size()) {
                                                    return getApiLevel(arguments.get(index), null);
                                                }
                                            }
                                        }
                                    }
                                    return -1;
                                };
                        Boolean ok =
                                isVersionCheckConditional(
                                        api, isLowerBound, returnValue, and, null, lookup);
                        if (ok != null) {
                            return ok;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSdkInt(@NonNull PsiElement element) {
        if (element instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression) element;
            if (SDK_INT.equals(ref.getReferenceName())) {
                return true;
            }
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiVariable) {
                PsiExpression initializer = ((PsiVariable) resolved).getInitializer();
                if (initializer != null) {
                    return isSdkInt(initializer);
                }
            }
        } else if (element instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
            if (GET_BUILD_SDK_INT.equals(callExpression.getMethodExpression().getReferenceName())) {
                return true;
            } // else look inside the body?
        }

        return false;
    }

    private static boolean isSdkInt(@NonNull UElement element) {
        if (element instanceof UReferenceExpression) {
            UReferenceExpression ref = (UReferenceExpression) element;
            if (SDK_INT.equals(ref.getResolvedName())) {
                return true;
            }
            PsiElement resolved = ref.resolve();
            if (resolved instanceof ULocalVariable) {
                UExpression initializer = ((ULocalVariable) resolved).getUastInitializer();
                if (initializer != null) {
                    return isSdkInt(initializer);
                }
            } else if (resolved instanceof PsiVariable) {
                PsiExpression initializer = ((PsiVariable) resolved).getInitializer();
                if (initializer != null) {
                    return isSdkInt(initializer);
                }
            }
        } else if (element instanceof UCallExpression) {
            UCallExpression callExpression = (UCallExpression) element;
            if (GET_BUILD_SDK_INT.equals(getMethodName(callExpression))) {
                return true;
            } // else look inside the body?
        }

        return false;
    }

    private static final String[] VERSION_METHOD_NAME_PREFIXES = {
        "isAtLeast", "isRunning", "is", "runningOn", "running"
    };
    private static final String[] VERSION_METHOD_NAME_SUFFIXES = {
        "OrLater", "OrAbove", "OrHigher", "OrNewer", "Sdk"
    };

    @VisibleForTesting
    static int getMinSdkVersionFromMethodName(String name) {
        String prefix = null;
        String suffix = null;
        for (String p : VERSION_METHOD_NAME_PREFIXES) {
            if (name.startsWith(p)) {
                prefix = p;
                break;
            }
        }
        for (String p : VERSION_METHOD_NAME_SUFFIXES) {
            if (endsWithIgnoreCase(name, p)) {
                suffix = p;
                break;
            }
        }

        if ("isAtLeast".equals(prefix) && suffix == null) {
            suffix = "";
        }

        if (prefix != null && suffix != null) {
            String codeName = name.substring(prefix.length(), name.length() - suffix.length());
            int version = SdkVersionInfo.getApiByPreviewName(codeName, false);
            if (version == -1) {
                version = SdkVersionInfo.getApiByBuildCode(codeName, false);
                if (version == -1
                        && codeName.length() == 1
                        && Character.isUpperCase(codeName.charAt(0))) {
                    // Some future API level
                    version = SdkVersionInfo.HIGHEST_KNOWN_API + 1;
                }
            }

            return version;
        }
        return -1;
    }

    @Nullable
    private static Boolean isVersionCheckConditional(
            int api,
            boolean isLowerBound,
            boolean fromThen,
            @NonNull UBinaryExpression binary,
            @Nullable ApiLevelLookup apiLevelLookup) {
        UastBinaryOperator tokenType = binary.getOperator();
        if (tokenType == UastBinaryOperator.GREATER
                || tokenType == UastBinaryOperator.GREATER_OR_EQUALS
                || tokenType == UastBinaryOperator.LESS_OR_EQUALS
                || tokenType == UastBinaryOperator.LESS
                || tokenType == UastBinaryOperator.EQUALS
                || tokenType == UastBinaryOperator.IDENTITY_EQUALS
                || tokenType == UastBinaryOperator.NOT_EQUALS
                || tokenType == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            UExpression left = binary.getLeftOperand();
            int level;
            UExpression right;
            if (!isSdkInt(left)) {
                right = binary.getRightOperand();
                if (isSdkInt(right)) {
                    fromThen = !fromThen;
                    level = getApiLevel(left, apiLevelLookup);
                } else {
                    return null;
                }
            } else {
                right = binary.getRightOperand();
                level = getApiLevel(right, apiLevelLookup);
            }
            if (level != -1) {
                if (tokenType == UastBinaryOperator.GREATER_OR_EQUALS) {
                    if (isLowerBound) {
                        // if (SDK_INT >= ICE_CREAM_SANDWICH) { <call> } else { ... }
                        return level >= api && fromThen;
                    } else {
                        // if (SDK_INT >= ICE_CREAM_SANDWICH) { ... } else { <call> }
                        return level - 1 <= api && !fromThen;
                    }
                } else if (tokenType == UastBinaryOperator.GREATER) {
                    if (isLowerBound) {
                        // if (SDK_INT > ICE_CREAM_SANDWICH) { <call> } else { ... }
                        return level >= api - 1 && fromThen;
                    } else {
                        // if (SDK_INT > ICE_CREAM_SANDWICH) { ... } else { <call> }
                        return level <= api && !fromThen;
                    }
                } else if (tokenType == UastBinaryOperator.LESS_OR_EQUALS) {
                    if (isLowerBound) {
                        // if (SDK_INT <= ICE_CREAM_SANDWICH) { ... } else { <call> }
                        return level >= api - 1 && !fromThen;
                    } else {
                        // if (SDK_INT <= ICE_CREAM_SANDWICH) { <call> } else { ... }
                        return level <= api && fromThen;
                    }
                } else if (tokenType == UastBinaryOperator.LESS) {
                    if (isLowerBound) {
                        // if (SDK_INT < ICE_CREAM_SANDWICH) { ... } else { <call> }
                        return level >= api && !fromThen;
                    } else {
                        // if (SDK_INT < ICE_CREAM_SANDWICH) { <call> } else { ... }
                        return level - 1 <= api && fromThen;
                    }
                } else if (tokenType == UastBinaryOperator.EQUALS
                        || tokenType == UastBinaryOperator.IDENTITY_EQUALS) {
                    // if (SDK_INT == ICE_CREAM_SANDWICH) { <call> } else { ... }
                    if (isLowerBound) {
                        return level >= api && fromThen;
                    } else {
                        return level <= api && fromThen;
                    }
                } else if (tokenType == UastBinaryOperator.NOT_EQUALS
                        || tokenType == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                    // if (SDK_INT != ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return level == api && !fromThen;
                } else {
                    assert false : tokenType;
                }
            }
        }
        return null;
    }

    private static int getApiLevel(
            @Nullable UExpression element, @Nullable ApiLevelLookup apiLevelLookup) {
        int level = -1;
        if (element instanceof UReferenceExpression) {
            UReferenceExpression ref2 = (UReferenceExpression) element;
            String codeName = ref2.getResolvedName();
            if (codeName != null) {
                level = SdkVersionInfo.getApiByBuildCode(codeName, false);
            }
            if (level == -1) {
                Object constant = ConstantEvaluator.evaluate(null, element);
                if (constant instanceof Number) {
                    level = ((Number) constant).intValue();
                }
            }
        } else if (element instanceof ULiteralExpression) {
            ULiteralExpression lit = (ULiteralExpression) element;
            Object value = lit.getValue();
            if (value instanceof Integer) {
                level = (Integer) value;
            }
        }
        if (level == -1 && apiLevelLookup != null && element != null) {
            level = apiLevelLookup.getApiLevel(element);
        }
        return level;
    }

    private static boolean isOredWithConditional(
            @NonNull UElement element, int api, boolean isLowerBound, @Nullable UElement before) {
        if (element instanceof UBinaryExpression) {
            UBinaryExpression inner = (UBinaryExpression) element;
            if (inner.getOperator() == UastBinaryOperator.LOGICAL_OR) {
                UExpression left = inner.getLeftOperand();

                if (before != left) {
                    Boolean ok =
                            isVersionCheckConditional(api, isLowerBound, left, false, null, null);
                    if (ok != null) {
                        return ok;
                    }
                    UExpression right = inner.getRightOperand();
                    ok = isVersionCheckConditional(api, isLowerBound, right, false, null, null);
                    if (ok != null) {
                        return ok;
                    }
                }
            }
            Boolean value = isVersionCheckConditional(api, isLowerBound, false, inner, null);
            return value != null && value;
        } else if (element instanceof UPolyadicExpression) {
            UPolyadicExpression ppe = (UPolyadicExpression) element;
            if (ppe.getOperator() == UastBinaryOperator.LOGICAL_OR) {
                for (UExpression operand : ppe.getOperands()) {
                    if (operand.equals(before)) {
                        break;
                    } else if (isOredWithConditional(operand, api, isLowerBound, before)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isAndedWithConditional(
            @NonNull UElement element, int api, boolean isLowerBound, @Nullable UElement before) {
        if (element instanceof UBinaryExpression) {
            UBinaryExpression inner = (UBinaryExpression) element;
            if (inner.getOperator() == UastBinaryOperator.LOGICAL_AND) {
                UExpression left = inner.getLeftOperand();
                if (before != left) {
                    Boolean ok =
                            isVersionCheckConditional(api, isLowerBound, left, true, null, null);
                    if (ok != null) {
                        return ok;
                    }
                    UExpression right = inner.getRightOperand();
                    ok = isVersionCheckConditional(api, isLowerBound, right, true, null, null);
                    if (ok != null) {
                        return ok;
                    }
                }
            }

            Boolean value = isVersionCheckConditional(api, isLowerBound, true, inner, null);
            return value != null && value;
        } else if (element instanceof UPolyadicExpression) {
            UPolyadicExpression ppe = (UPolyadicExpression) element;
            if (ppe.getOperator() == UastBinaryOperator.LOGICAL_AND) {
                for (UExpression operand : ppe.getOperands()) {
                    if (operand.equals(before)) {
                        break;
                    } else if (isAndedWithConditional(operand, api, isLowerBound, before)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // TODO: Merge with the other isVersionCheckConditional
    @Nullable
    public static Boolean isVersionCheckConditional(int api, @NonNull UBinaryExpression binary) {
        UastBinaryOperator tokenType = binary.getOperator();
        if (tokenType == UastBinaryOperator.GREATER
                || tokenType == UastBinaryOperator.GREATER_OR_EQUALS
                || tokenType == UastBinaryOperator.LESS_OR_EQUALS
                || tokenType == UastBinaryOperator.LESS
                || tokenType == UastBinaryOperator.EQUALS
                || tokenType == UastBinaryOperator.IDENTITY_EQUALS) {
            UExpression left = binary.getLeftOperand();
            if (left instanceof UReferenceExpression) {
                UReferenceExpression ref = (UReferenceExpression) left;
                if (SDK_INT.equals(ref.getResolvedName())) {
                    UExpression right = binary.getRightOperand();
                    int level = -1;
                    if (right instanceof UReferenceExpression) {
                        UReferenceExpression ref2 = (UReferenceExpression) right;
                        String codeName = ref2.getResolvedName();
                        if (codeName == null) {
                            return false;
                        }
                        level = SdkVersionInfo.getApiByBuildCode(codeName, true);
                    } else if (right instanceof ULiteralExpression) {
                        ULiteralExpression lit = (ULiteralExpression) right;
                        Object value = lit.getValue();
                        if (value instanceof Integer) {
                            level = (Integer) value;
                        }
                    }
                    if (level != -1) {
                        if (tokenType == UastBinaryOperator.GREATER_OR_EQUALS && level < api) {
                            // SDK_INT >= ICE_CREAM_SANDWICH
                            return true;
                        } else if (tokenType == UastBinaryOperator.GREATER && level <= api - 1) {
                            // SDK_INT > ICE_CREAM_SANDWICH
                            return true;
                        } else if (tokenType == UastBinaryOperator.LESS_OR_EQUALS && level < api) {
                            return false;
                        } else if (tokenType == UastBinaryOperator.LESS && level <= api) {
                            // SDK_INT < ICE_CREAM_SANDWICH
                            return false;
                        } else if ((tokenType == UastBinaryOperator.EQUALS
                                        || tokenType == UastBinaryOperator.IDENTITY_EQUALS)
                                && level < api) {
                            return false;
                        }
                    }
                }
            }
        }
        return null;
    }
}
