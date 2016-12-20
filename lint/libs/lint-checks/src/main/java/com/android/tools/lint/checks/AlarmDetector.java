/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

/**
 * Makes sure that alarms are handled correctly
 */
public class AlarmDetector extends Detector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AlarmDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Alarm set too soon/frequently  */
    public static final Issue ISSUE = Issue.create(
            "ShortAlarm",
            "Short or Frequent Alarm",

            "Frequent alarms are bad for battery life. As of API 22, the `AlarmManager` " +
            "will override near-future and high-frequency alarm requests, delaying the alarm " +
            "at least 5 seconds into the future and ensuring that the repeat interval is at " +
            "least 60 seconds.\n" +
            "\n" +
            "If you really need to do work sooner than 5 seconds, post a delayed message " +
            "or runnable to a Handler.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Constructs a new {@link AlarmDetector} check */
    public AlarmDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setRepeating");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isMemberInClass(method, "android.app.AlarmManager") &&
                evaluator.getParameterCount(method) == 4) {
            ensureAtLeast(context, node, 1, 5000L);
            ensureAtLeast(context, node, 2, 60000L);
        }
    }

    private static void ensureAtLeast(@NonNull JavaContext context,
            @NonNull UCallExpression node, int parameter, long min) {
        UExpression argument = node.getValueArguments().get(parameter);
        long value = getLongValue(context, argument);
        if (value < min) {
            String message = String.format("Value will be forced up to %1$d as of Android 5.1; "
                    + "don't rely on this to be exact", min);
            context.report(ISSUE, argument, context.getLocation(argument), message);
        }
    }

    private static long getLongValue(
            @NonNull JavaContext context,
            @NonNull UExpression argument) {
        Object value = ConstantEvaluator.evaluate(context, argument);
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }

        return Long.MAX_VALUE;
    }
}
