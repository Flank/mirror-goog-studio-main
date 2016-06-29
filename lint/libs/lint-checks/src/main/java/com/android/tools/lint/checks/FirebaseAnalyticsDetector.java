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

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FirebaseAnalyticsDetector extends Detector implements Detector.JavaPsiScanner {

    private static final int EVENT_NAME_MAX_LENGTH = 32;
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

    public static final Issue INVALID_EVENT_NAME = Issue.create(
            "InvalidAnalyticsEventName", //$NON-NLS-1$
            "Invalid Analytics Event Name",
            "Event names must contain only alphabetic characters, digits and underscores and the " +
                    "first character must be an alphabetic character. Event name length must be " +
                    String.valueOf(EVENT_NAME_MAX_LENGTH) + " characters or less.",
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

        if (expressions.length == 0) {
            return;
        }

        PsiElement firstArgumentExpression = expressions[0];
        String value = ConstantEvaluator.evaluateString(context, firstArgumentExpression, false);
        if (value == null) {
            return;
        }

        String error = getErrorForEventName(value);

        if (error != null) {
            context.report(INVALID_EVENT_NAME, call, context.getLocation(call), error);
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

        for (int i = 0; i < eventName.length(); i++) {
            char character = eventName.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                String message = "Analytics event name must only consist of letters, numbers and " +
                        "underscores (found %1$s)";
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
}
