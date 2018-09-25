/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.SdkConstants.CLASS_PARCELABLE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

/** Looks for Parcelable classes that are missing a CREATOR field */
public class ParcelDetector extends Detector implements SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "ParcelCreator",
                            "Missing Parcelable `CREATOR` field",
                            "According to the `Parcelable` interface documentation, "
                                    + "\"Classes implementing the Parcelable interface must also have a "
                                    + "static field called `CREATOR`, which is an object implementing the "
                                    + "`Parcelable.Creator` interface.\"",
                            Category.CORRECTNESS,
                            3,
                            Severity.ERROR,
                            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true)
                    .addMoreInfo(
                            "http://developer.android.com/reference/android/os/Parcelable.html");

    /** Constructs a new {@link ParcelDetector} check */
    public ParcelDetector() {}

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_PARCELABLE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            // Anonymous classes aren't parcelable
            return;
        }

        // Only applies to concrete classes
        if (declaration.isInterface()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Parceling spans is handled in TextUtils#CHAR_SEQUENCE_CREATOR
        if (context.getEvaluator()
                .implementsInterface(declaration, "android.text.ParcelableSpan", false)) {
            return;
        }

        boolean isKotlin = Lint.isKotlin(declaration);
        if (isKotlin) {
            PsiModifierList modifierList = declaration.getModifierList();
            if (modifierList != null
                    && modifierList.findAnnotation("kotlinx.android.parcel.Parcelize") != null) {
                // Already using @Parcelize: nothing to suggest (and don't warn about missing
                // CREATOR field below)
                return;
            }
        }

        PsiField field = declaration.findFieldByName("CREATOR", true);
        if (field == null) {
            Location location = context.getNameLocation(declaration);
            String message =
                    "This class implements `Parcelable` but does not "
                            + "provide a `CREATOR` field";
            context.report(ISSUE, declaration, location, message, null);
        } else if (Lint.isKotlin(field) && !hasCreatorInnerClass(declaration)) {
            // Make sure fields in Kotlin are marked @JvmField
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList != null
                    && modifierList.findAnnotation("kotlin.jvm.JvmField") == null) {
                Location location = context.getNameLocation(field);
                LintFix fix =
                        fix().name("Add @JvmField", true)
                                .replace()
                                .text("val")
                                .with("@JvmField val")
                                .range(context.getLocation(field))
                                .autoFix()
                                .build();
                context.report(
                        ISSUE, field, location, "Field should be annotated with `@JvmField`", fix);
            }
        }
    }

    private static boolean hasCreatorInnerClass(@NonNull UClass declaration) {
        // Might be using a companion object; we can't see that in UAST
        for (UClass inner : declaration.getInnerClasses()) {
            String name = inner.getName();
            if ("CREATOR".equals(name)) {
                // Yes, there is an inner class also called CREATOR;
                // it's likely our CREATOR field was generated from
                // a companion object
                return true;
            }
        }
        return false;
    }
}
