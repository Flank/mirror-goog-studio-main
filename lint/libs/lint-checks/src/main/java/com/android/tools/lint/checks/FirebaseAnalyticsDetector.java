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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FirebaseAnalyticsDetector extends Detector implements Detector.JavaPsiScanner {

    private static final int EVENT_NAME_MAX_LENGTH = 32;
    private static final int EVENT_PARAM_NAME_MAX_LENGTH = 24;
    private static final Implementation IMPLEMENTATION = new Implementation(
            FirebaseAnalyticsDetector.class,
            Scope.JAVA_FILE_SCOPE);

    // This list is taken from:
    // https://developers.google.com/android/reference/com/google/firebase/analytics/FirebaseAnalytics.Event
    private static final Set<String> RESERVED_EVENT_NAMES = Sets.newHashSet(
            "app_clear_data",
            "app_uninstall",
            "app_update",
            "error",
            "first_open",
            "in_app_purchase",
            "notification_dismiss",
            "notification_foreground",
            "notification_open",
            "notification_receive",
            "os_update",
            "session_start",
            "user_engagement");

    public static final Issue INVALID_NAME = Issue.create(
            "InvalidAnalyticsName", //$NON-NLS-1$
            "Invalid Analytics Name",
            "Event names and parameters must follow the naming conventions defined in the" +
                    "`FirebaseAnalytics#logEvent()` documentation.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo(
                    "http://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics#logEvent(java.lang.String,%20android.os.Bundle)");

    /**
     * Constructs a new {@link FirebaseAnalyticsDetector}
     */
    public FirebaseAnalyticsDetector() {
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod method) {
        String firebaseAnalytics = "com.google.firebase.analytics.FirebaseAnalytics";
        if (!context.getEvaluator().isMemberInClass(method, firebaseAnalytics)) {
            return;
        }

        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        if (expressions.length < 2) {
            return;
        }

        PsiElement firstArgumentExpression = expressions[0];
        String value = ConstantEvaluator.evaluateString(context, firstArgumentExpression, false);
        if (value == null) {
            return;
        }

        String error = getErrorForEventName(value);

        if (error != null) {
            context.report(INVALID_NAME, call, context.getLocation(call), error);
        }

        PsiExpression secondParameter = expressions[1];
        List<BundleModification> bundleModifications = getBundleModifications(context,
                secondParameter);

        if (bundleModifications != null && !bundleModifications.isEmpty()) {
            validateEventParameters(context, bundleModifications, call);
        }
    }

    private static void validateEventParameters(JavaContext context,
            List<BundleModification> parameters,
            PsiElement call) {
        for (BundleModification bundleModification : parameters) {
            String error = getErrorForEventParameterName(bundleModification.mName);
            if (error != null) {
                Location location = context.getLocation(call);
                location.withSecondary(context.getLocation(bundleModification.mLocation), error);
                context.report(INVALID_NAME, location,
                        "Bundle with invalid Analytics event parameters passed to logEvent.");
            }
        }
    }

    @Nullable
    private static List<BundleModification> getBundleModifications(JavaContext context,
            PsiExpression secondParameter) {
        PsiType type = secondParameter.getType();
        if (type != null && !type.getCanonicalText().equals(SdkConstants.CLASS_BUNDLE)) {
            return null;
        }

        if (secondParameter instanceof PsiNewExpression) {
            return Collections.emptyList();
        }

        List<BundleModification> modifications = null;

        if (secondParameter instanceof PsiReferenceExpression) {
            PsiReferenceExpression bundleReference = (PsiReferenceExpression) secondParameter;
            modifications = BundleModificationFinder.find(context, bundleReference);
        }

        return modifications;
    }

    /**
     * Given a reference to an instance of Bundle, find the putString method calls that modify the
     * bundle.
     *
     * This will recursively search across files within the project.
     */
    private static class BundleModificationFinder extends JavaRecursiveElementVisitor {

        private final PsiReferenceExpression mBundleReference;
        private final JavaContext mContext;
        private final List<BundleModification> mParameters = new ArrayList<>();

        private BundleModificationFinder(JavaContext context,
                PsiReferenceExpression bundleReference) {
            mContext = context;
            mBundleReference = bundleReference;
        }

        @Override
        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
            for (PsiElement element : statement.getDeclaredElements()) {
                if (!(element instanceof PsiLocalVariable)) {
                    continue;
                }

                PsiLocalVariable local = (PsiLocalVariable) element;
                String name = local.getName();

                if (name == null || !name.equals(mBundleReference.getText())) {
                    continue;
                }

                if (!(local.getInitializer() instanceof PsiMethodCallExpression)) {
                    continue;
                }

                PsiMethodCallExpression call = (PsiMethodCallExpression) local.getInitializer();
                PsiReferenceExpression returnReference = ReturnReferenceExpressionFinder
                        .find(call.resolveMethod());

                if (returnReference != null) {
                    addParams(find(mContext, returnReference));
                }
            }
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            String method = expression.getMethodExpression().getCanonicalText();

            if (!method.endsWith(".putString") && !method.endsWith(".putLong") && !method
                    .endsWith(".putDouble")) {
                return;
            }

            PsiElement token = expression.getMethodExpression().getQualifier();
            if (token == null || !mBundleReference.getText().equals(token.getText())) {
                return;
            }

            PsiExpression[] expressions = expression.getArgumentList().getExpressions();
            String evaluatedName = ConstantEvaluator.evaluateString(mContext,
                    expressions[0], false);

            if (evaluatedName != null) {
                addParam(evaluatedName, expressions[1].getText(), expression);
            }
        }

        private void addParam(String key, String value, PsiMethodCallExpression location) {
            mParameters.add(new BundleModification(key, value, location));
        }

        private void addParams(Collection<BundleModification> bundleModifications) {
            mParameters.addAll(bundleModifications);
        }

        @NotNull
        static List<BundleModification> find(JavaContext context,
                PsiReferenceExpression bundleReference) {
            BundleModificationFinder scanner = new BundleModificationFinder(context,
                    bundleReference);
            PsiMethod enclosingMethod = PsiTreeUtil
                    .getParentOfType(bundleReference, PsiMethod.class);
            if (enclosingMethod == null) {
                return Collections.emptyList();
            }
            enclosingMethod.accept(scanner);
            return scanner.mParameters;
        }
    }

    /**
     * Given a method, find the last `return` expression that returns a reference.
     */
    @SuppressWarnings("UnsafeReturnStatementVisitor")
    private static class ReturnReferenceExpressionFinder extends JavaRecursiveElementVisitor {

        private PsiReferenceExpression mReturnReference = null;

        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
            PsiExpression returnExpression = statement.getReturnValue();
            if (returnExpression instanceof PsiReferenceExpression) {
                mReturnReference = (PsiReferenceExpression) returnExpression;
            }
        }

        @Nullable
        static PsiReferenceExpression find(PsiMethod method) {
            ReturnReferenceExpressionFinder finder = new ReturnReferenceExpressionFinder();
            method.accept(finder);
            return finder.mReturnReference;
        }
    }

    private static class BundleModification {

        public final String mName;
        @SuppressWarnings("unused")
        public final String mValue;
        public final PsiMethodCallExpression mLocation;

        public BundleModification(String name, String value,
                PsiMethodCallExpression location) {
            mName = name;
            mValue = value;
            mLocation = location;
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("logEvent");
    }

    @Nullable
    private static String getErrorForEventName(String eventName) {
        if (eventName.length() > EVENT_NAME_MAX_LENGTH) {
            String message = "Analytics event name must be less than %1$d characters (found %2$d)";
            return String.format(message, EVENT_NAME_MAX_LENGTH, eventName.length());
        }

        if (eventName.isEmpty()) {
            return "Analytics event name cannot be empty";
        }

        if (!Character.isAlphabetic(eventName.charAt(0))) {
            String message
                    = "Analytics event name must start with an alphabetic character (found %1$s)";
            return String.format(message, eventName);
        }

        String message = "Analytics event name must only consist of letters, numbers and " +
                "underscores (found %1$s)";
        for (int i = 0; i < eventName.length(); i++) {
            char character = eventName.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return String.format(message, eventName);
            }
        }

        if (eventName.startsWith("firebase_")) {
            return "Analytics event name should not start with `firebase_`";
        }

        if (RESERVED_EVENT_NAMES.contains(eventName)) {
            return String.format("`%1$s` is a reserved Analytics event name and cannot be used",
                    eventName);
        }

        return null;
    }

    @Nullable
    private static String getErrorForEventParameterName(String eventParameterName) {
        if (eventParameterName.length() > EVENT_PARAM_NAME_MAX_LENGTH) {
            String message =
                    "Analytics event parameter name must be %1$d characters or less (found %2$d)";
            return String.format(message, EVENT_PARAM_NAME_MAX_LENGTH, eventParameterName.length());
        }

        if (eventParameterName.isEmpty()) {
            return "Analytics event parameter name cannot be empty";
        }

        if (!Character.isAlphabetic(eventParameterName.charAt(0))) {
            String message = "Analytics event parameter name must start with an alphabetic " +
                    "character (found %1$s)";
            return String.format(message, eventParameterName);
        }

        String message = "Analytics event name must only consist of letters, numbers and " +
                "underscores (found %1$s)";
        for (int i = 0; i < eventParameterName.length(); i++) {
            char character = eventParameterName.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return String.format(message, eventParameterName);
            }
        }

        if (eventParameterName.startsWith("firebase_")) {
            return "Analytics event parameter name cannot be start with `firebase_`";
        }

        return null;
    }
}
