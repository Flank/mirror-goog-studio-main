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

import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;

/**
 * Looks for leaks via static fields
 */
public class LeakDetector extends Detector implements Detector.UastScanner {
    /** Leaking data via static fields */
    public static final Issue ISSUE = Issue.create(
            "StaticFieldLeak",
            "Static Field Leaks",

            "A static field will leak contexts.",

            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    LeakDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    /** Constructs a new {@link LeakDetector} check */
    public LeakDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UField.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new FieldChecker(context);
    }

    private static class FieldChecker extends UElementHandler {
        private final JavaContext mContext;

        public FieldChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitField(@NonNull UField field) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }

            PsiType type = field.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }

            String fqn = type.getCanonicalText();
            if (fqn.startsWith("java.")) {
                return;
            }
            PsiClass cls = ((PsiClassType) type).resolve();
            if (cls == null) {
                return;
            }
            if (fqn.startsWith("android.")) {
                if (isLeakCandidate(cls, mContext.getEvaluator())
                        && !isAppContextName(cls, field)) {
                    String message = "Do not place Android context classes in static fields; "
                            + "this is a memory leak (and also breaks Instant Run)";
                    report(field, modifierList, message);
                }
            } else {
                // User application object -- look to see if that one itself has
                // static fields?
                // We only check *one* level of indirection here
                int count = 0;
                for (PsiField referenced : cls.getAllFields()) {
                    // Only check a few; avoid getting bogged down on large classes
                    if (count++ == 20) {
                        break;
                    }

                    PsiType innerType = referenced.getType();
                    if (!(innerType instanceof PsiClassType)) {
                        continue;
                    }

                    fqn = innerType.getCanonicalText();
                    if (fqn.startsWith("java.")) {
                        continue;
                    }
                    PsiClass innerCls = ((PsiClassType) innerType).resolve();
                    if (innerCls == null) {
                        continue;
                    }
                    if (fqn.startsWith("android.")) {
                        if (isLeakCandidate(innerCls, mContext.getEvaluator())
                                && !isAppContextName(innerCls, field)) {
                            String message =
                                    "Do not place Android context classes in static fields "
                                            + "(static reference to `"
                                            + cls.getName() + "` which has field "
                                            + "`" + referenced.getName() + "` pointing to `"
                                            + innerCls.getName() + "`); "
                                        + "this is a memory leak (and also breaks Instant Run)";
                            report(field, modifierList, message);
                            break;
                        }
                    }
                }
            }
        }

        private void report(@NonNull PsiField field, @NonNull PsiModifierList modifierList,
                @NonNull String message) {
            PsiElement locationNode = field;
            // Try to find the static modifier itself
            if (modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                PsiElement child = modifierList.getFirstChild();
                while (child != null) {
                    if (child instanceof PsiKeyword
                            && PsiKeyword.STATIC.equals(child.getText())) {
                        locationNode = child;
                        break;
                    }
                    child = child.getNextSibling();
                }
            }
            Location location = mContext.getLocation(locationNode);
            mContext.report(ISSUE, field, location, message);
        }
    }

    private static boolean isAppContextName(@NonNull PsiClass cls, @NonNull PsiField field) {
        // Don't flag names like "sAppContext" or "applicationContext".
        String name = field.getName();
        if (name != null) {
            String lower = name.toLowerCase(Locale.US);
            if (lower.contains("appcontext") || lower.contains("application")) {
                if (CLASS_CONTEXT.equals(cls.getQualifiedName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isLeakCandidate(
            @NonNull PsiClass cls,
            @NonNull JavaEvaluator evaluator) {
        return evaluator.extendsClass(cls, CLASS_CONTEXT, false)
                || evaluator.extendsClass(cls, CLASS_VIEW, false)
                || evaluator.extendsClass(cls, CLASS_FRAGMENT, false);
    }
}
