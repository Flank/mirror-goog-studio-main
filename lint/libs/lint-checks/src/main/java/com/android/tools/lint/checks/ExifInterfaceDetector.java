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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import java.util.Collections;
import java.util.List;

/**
 * Checks for errors related to the Exif Interface
 */
public class ExifInterfaceDetector extends Detector implements Detector.JavaPsiScanner {
    public static final String EXIF_INTERFACE = "ExifInterface";
    public static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION = new Implementation(
            ExifInterfaceDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Using android.media.ExifInterface */
    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using `android.media.ExifInterface`",

            "The `android.media.ExifInterface` implementation has some known security " +
            "bugs in older versions of Android. There is a new implementation available " +
            "of this library in the support library, which is preferable.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Constructs a new {@link ExifInterfaceDetector} */
    public ExifInterfaceDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList(EXIF_INTERFACE);
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiJavaCodeReferenceElement reference, @NonNull PsiElement resolved) {
        if (resolved instanceof PsiMethod || resolved instanceof PsiField) { // !PsiClass
            resolved = ((PsiMember) resolved).getContainingClass();
        }
        if (resolved instanceof PsiClass) {
            String qualifiedName = ((PsiClass) resolved).getQualifiedName();
            if (qualifiedName != null && OLD_EXIF_INTERFACE.equals(qualifiedName)) {
                PsiElement locationNode = reference;
                while (locationNode.getParent() instanceof PsiReferenceExpression) {
                    locationNode = locationNode.getParent();
                }

                Location location = context.getLocation(reference);
                String message = "Avoid using `android.media.ExifInterface`; use "
                        + "`android.support.media.ExifInterface` from the support library instead";
                context.report(ISSUE, reference, location, message);
            }
        }
    }
}
