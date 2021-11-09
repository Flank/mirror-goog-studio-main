/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.kotlin.KotlinInternalUastUtilsKt;

// Class which contains some code which cannot be expressed in Kotlin;
// not public since the public LintUtils methods will more directly expose them
class LintJavaUtils {
    /** Returns true if assertions are enabled */
    @SuppressWarnings("all")
    static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        return assertionsEnabled;
    }

    @SuppressWarnings("KotlinInternalInJava")
    @NonNull
    static PsiType getType(
            @NonNull KotlinType type,
            @Nullable UElement source,
            @NonNull KtElement ktElement,
            boolean boxed) {
        // TODO(kotlin-uast-cleanup): avoid using "internal" utils
        return KotlinInternalUastUtilsKt.toPsiType(type, source, ktElement, boxed);
    }

    @Nullable
    static PsiElement resolveToPsiMethod(
            @NonNull KtElement context,
            @NonNull DeclarationDescriptor descriptor,
            @Nullable PsiElement source) {
        //noinspection KotlinInternalInJava
        return KotlinInternalUastUtilsKt.resolveToPsiMethod(context, descriptor, source);
    }
}
