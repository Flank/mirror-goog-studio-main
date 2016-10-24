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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Detect calls to get device Identifiers.
 */
public class HardwareIdDetector extends Detector implements Detector.JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            HardwareIdDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Hardware Id Usages  */
    public static final Issue ISSUE = Issue.create(
            "HardwareIds",
            "Hardware Id Usage",

            "Using these device identifiers is not recommended " +
            "other than for high value fraud prevention and advanced telephony use-cases. " +
            "For advertising use-cases, use `AdvertisingIdClient$Info#getId` and for " +
            "analytics, use `InstanceId#getId`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "https://developer.android.com/training/articles/user-data-ids.html");

    private static final String BLUETOOTH_ADAPTER_GET_ADDRESS = "getAddress";
    private static final String WIFI_INFO_GET_MAC_ADDRESS = "getMacAddress";
    private static final String TELEPHONY_MANAGER_GET_DEVICE_ID = "getDeviceId";
    private static final String TELEPHONY_MANAGER_GET_LINE_1_NUMBER =
            "getLine1Number";
    private static final String TELEPHONY_MANAGER_GET_SIM_SERIAL_NUMBER =
            "getSimSerialNumber";
    private static final String TELEPHONY_MANAGER_GET_SUBSCRIBER_ID =
            "getSubscriberId";
    private static final String SETTINGS_SECURE_GET_STRING = "getString";
    private static final String PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION =
            "com.google.android.gms.common.GooglePlayServicesNotAvailableException";
    private static final String MESSAGE_DEVICE_IDENTIFIERS =
      "Using `%1$s` to get device identifiers is not recommended.";
    private static final String RO_SERIALNO = "ro.serialno";
    private static final String CLASS_FOR_NAME = "forName";
    private static final String CLASSLOADER_LOAD_CLASS = "loadClass";


    /**
     * Constructs a new {@link HardwareIdDetector}
     */
    public HardwareIdDetector() {
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                BLUETOOTH_ADAPTER_GET_ADDRESS,
                WIFI_INFO_GET_MAC_ADDRESS,
                TELEPHONY_MANAGER_GET_DEVICE_ID,
                TELEPHONY_MANAGER_GET_LINE_1_NUMBER,
                TELEPHONY_MANAGER_GET_SIM_SERIAL_NUMBER,
                TELEPHONY_MANAGER_GET_SUBSCRIBER_ID,
                SETTINGS_SECURE_GET_STRING,
                CLASS_FOR_NAME,
                CLASSLOADER_LOAD_CLASS
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        String className = null;
        String methodName = method.getName();
        switch (methodName) {
            case BLUETOOTH_ADAPTER_GET_ADDRESS:
                className = "android.bluetooth.BluetoothAdapter";
                break;
            case WIFI_INFO_GET_MAC_ADDRESS:
                className = "android.net.wifi.WifiInfo";
                break;
            case TELEPHONY_MANAGER_GET_DEVICE_ID:
            case TELEPHONY_MANAGER_GET_LINE_1_NUMBER:
            case TELEPHONY_MANAGER_GET_SIM_SERIAL_NUMBER:
            case TELEPHONY_MANAGER_GET_SUBSCRIBER_ID:
                className = "android.telephony.TelephonyManager";
                break;
            case SETTINGS_SECURE_GET_STRING:
                className = "android.provider.Settings.Secure";
                break;
            case CLASS_FOR_NAME:
                className = "java.lang.Class";
                break;
            case CLASSLOADER_LOAD_CLASS:
                className = "java.lang.ClassLoader";
                break;
            default:
                assert false;
        }

        if (!evaluator.isMemberInClass(method, className)) {
            return;
        }

        if (methodName.equals(SETTINGS_SECURE_GET_STRING)) {
            if (evaluator.getParameterCount(method) != 2
                    || node.getArgumentList().getExpressions().length != 2) {
                // we are explicitly looking for Secure.getString(x, ANDROID_ID) here
                return;
            }
            String value = ConstantEvaluator.evaluateString(
                    context, node.getArgumentList().getExpressions()[1], false);
            // Check if the value matches Settings.Secure.ANDROID_ID
            if (!"android_id".equals(value)) {
                return;
            }
            // The 2nd parameter resolved to the constant value Settings.Secure.ANDROID_ID
            // which is not recommended so continue and show an error.
        } else if (methodName.equals(CLASS_FOR_NAME)
                || methodName.equals(CLASSLOADER_LOAD_CLASS)) {
            // Here we are looking for usages of
            // `android.os.SystemProperties.get("ro.serialno")` using reflection.
            //
            // Typical code for this would looks like the following:
            //
            // Class<?> c = Class.forName("android.os.SystemProperties");
            // Method get = c.getMethod("get", String.class);
            // ...result = (String) get.invoke(null, "ro.serialno");
            //
            findReflectionUsage(node, context);
            return;
        }

        // If any of the calls to get device identifiers are explicitly in a catch block for
        // GooglePlayServicesNotAvailableException, then don't report a warning.
        // This is to handle the case where the alternate play services api is unavailable on
        // the device.
        if (inCatchPlayServicesNotAvailableException(node)) {
            return;
        }

        String message = String.format(MESSAGE_DEVICE_IDENTIFIERS, methodName);
        context.report(ISSUE, node, context.getLocation(node), message);
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("SERIAL");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @Nullable JavaElementVisitor visitor,
            @NonNull PsiJavaCodeReferenceElement reference,
            @NonNull PsiElement resolved) {

        JavaEvaluator evaluator = context.getEvaluator();
        if (resolved instanceof PsiField
                && evaluator.isMemberInSubClassOf((PsiField)resolved,
                "android.os.Build", false)) {
            String message =
                    String.format(MESSAGE_DEVICE_IDENTIFIERS, "SERIAL");
            context.report(ISSUE, reference, context.getNameLocation(reference), message);
        }
    }

    /**
     * Check if the given expression is within a catch block of
     * {@code PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION}
     *
     * @param expression PsiExpression that can be within a catch block
     * @return true iff the expression is within the catch block
     */
    private static boolean inCatchPlayServicesNotAvailableException(PsiExpression expression) {

        PsiCatchSection surroundingCatchSection =
                PsiTreeUtil.getParentOfType(expression, PsiCatchSection.class, true);

        if (surroundingCatchSection != null && surroundingCatchSection.getCatchType() != null) {
            PsiType catchType = surroundingCatchSection.getCatchType();
            // Handle multi-catch statements such as (IOException | AnotherException e)
            if (catchType instanceof PsiDisjunctionType) {
                PsiDisjunctionType disjunctionType = (PsiDisjunctionType) catchType;
                if (disjunctionType.getDisjunctions()
                        .stream()
                        .anyMatch(t -> t.equalsToText(PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION))) {
                    return true;
                }
            } else if (catchType.equalsToText(PLAY_SERVICES_NOT_AVAILABLE_EXCEPTION)) {
                return true;
            }
        }
        return false;
    }

    private static void findReflectionUsage(@NonNull PsiMethodCallExpression expression,
            @NonNull JavaContext context) {
        PsiExpression[] methodArgs = expression.getArgumentList().getExpressions();
        if (methodArgs.length < 1) {
            return;
        }
        String value = ConstantEvaluator.evaluateString(context, methodArgs[0], false);
        if (!"android.os.SystemProperties".equals(value)) {
            return;
        }
        PsiMethod surroundingMethod =
                PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true);
        if (surroundingMethod == null) {
            return;
        }

        InvokeCallVisitor visitor = new InvokeCallVisitor(context, expression);
        surroundingMethod.accept(visitor);
        PsiParameter argExpression = visitor.getPsiParameter();
        if (argExpression == null) {
            // If RO_SERIALNO string was found, the warning is already reported by the
            // visitor.
            return;
        }

        // The key was passed into the method as a parameter into the given method.
        // e.g. getSystemProperty(context, "ro.serialno")
        // So we need to find calls to the current method. Note: Here we restrict
        // the search to the current class.
        PsiClass surroundingClass = PsiTreeUtil.getParentOfType(
                surroundingMethod, PsiClass.class, true);

        if (surroundingClass != null) {
            int paramIndex = surroundingMethod.getParameterList()
                    .getParameterIndex(argExpression);
            if (paramIndex < 0) {
                return;
            }
            FindMethodCallVisitor methodCallVisitor =
                    new FindMethodCallVisitor(context, surroundingMethod, paramIndex);
            surroundingClass.accept(methodCallVisitor);
        }
    }

    /**
     * Search for a sequence of reflection methods calls leading to
     * {@link java.lang.reflect.Method#invoke(Object, Object...)} and also check the parameter(s)
     * passed into invoke.
     */
    private static final class InvokeCallVisitor extends JavaRecursiveElementVisitor {

        private final PsiMethodCallExpression mLoadMethod;
        private final JavaContext mContext;

        private String mLoadVariable;
        private String mMethodVariable;
        private boolean mProcessingDone;
        private PsiParameter mPsiParameter;

        public InvokeCallVisitor(JavaContext context, PsiMethodCallExpression expression) {
            mContext = context;
            mLoadMethod = expression;
        }

        @Override
        public void visitElement(PsiElement element) {
            // stop processing if we have already concluded our search.
            if (!mProcessingDone) {
                super.visitElement(element);
            }
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (expression == mLoadMethod) {
                PsiVariable variable = CleanupDetector.getVariableElement(expression);
                mLoadVariable = variable == null ? null : variable.getName();
            } else if (mLoadVariable != null
                    && isDesiredMethodCall(expression, mLoadVariable,
                    "java.lang.Class", "getMethod", 0)) {
                // clazz.getMethod("get", ..)
                PsiExpression arg = methodParameterAt(expression, 0 /* param index */);
                String value = ConstantEvaluator.evaluateString(mContext, arg, false);
                if ("get".equals(value)) {
                    PsiVariable variable = CleanupDetector.getVariableElement(expression);
                    mMethodVariable = variable == null ? null : variable.getName();
                }
            } else if (mMethodVariable != null
                    && isDesiredMethodCall(expression, mMethodVariable,
                    "java.lang.reflect.Method", "invoke", 1 /* param index */)) {
                // method.invoke(instance, "ro.serialno")
                PsiExpression arg = methodParameterAt(expression, 1);
                String value = ConstantEvaluator.evaluateString(mContext, arg, false);
                if (RO_SERIALNO.equals(value)) {
                    mContext.report(ISSUE, arg, mContext.getLocation(arg),
                            String.format(MESSAGE_DEVICE_IDENTIFIERS, RO_SERIALNO));
                } else if (arg instanceof PsiReferenceExpression) {
                    PsiElement resolved = ((PsiReferenceExpression)arg).resolve();
                    if (resolved instanceof PsiParameter) {
                        mPsiParameter = (PsiParameter)resolved;
                    }
                }
                mProcessingDone = true;
            }
        }

        @Nullable
        PsiParameter getPsiParameter() {
            return mPsiParameter;
        }

        private static PsiExpression methodParameterAt(PsiMethodCallExpression expression,
                int index) {
            PsiExpression[] expressions = expression.getArgumentList().getExpressions();
            assert expressions.length > index;
            return expressions[index];
        }

        private static boolean isDesiredMethodCall(@NonNull PsiMethodCallExpression expression,
                @NonNull String variableQualifier,
                @NonNull String containingClass,
                @NonNull String desiredMethodName, int paramIndex) {

            if (!desiredMethodName.equals(expression.getMethodExpression().getReferenceName())) {
                return false;
            }
            // Check that the qualifier used is the same.
            PsiExpression qualifierExpression = expression
                    .getMethodExpression()
                    .getQualifierExpression();

            if (qualifierExpression == null
                    || !variableQualifier.equals(qualifierExpression.getText())) {
                return false;
            }

            PsiMethod method = expression.resolveMethod();

            return method != null
                    && method.getContainingClass() != null
                    && containingClass.equals(method.getContainingClass().getQualifiedName())
                    && expression.getArgumentList().getExpressions().length > paramIndex;
        }
    }

    /**
     * Find calls to a given method and report an issue if a parameter at parametIndex
     * evaluates to a constant 'ro.serialno'
     */
    private static final class FindMethodCallVisitor extends JavaRecursiveElementVisitor {

        private final JavaContext mContext;
        private final PsiMethod mPsiMethod;
        private final int mParamIndex;

        FindMethodCallVisitor(JavaContext context, PsiMethod method, int paramIndex) {
            mContext = context;
            mPsiMethod = method;
            mParamIndex = paramIndex;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (mPsiMethod == expression.resolveMethod()) {
                PsiExpression[] expressions = expression.getArgumentList().getExpressions();
                if (expressions.length > mParamIndex) {
                    PsiExpression paramExpr = expressions[mParamIndex];
                    String value = ConstantEvaluator.evaluateString(mContext, paramExpr, false);
                    if (RO_SERIALNO.equals(value)
                            && !inCatchPlayServicesNotAvailableException(expression)) {
                        String message =
                                String.format(MESSAGE_DEVICE_IDENTIFIERS, RO_SERIALNO);
                        mContext.report(ISSUE, paramExpr, mContext.getLocation(paramExpr), message);
                    }
                }
            }
        }
    }
}
