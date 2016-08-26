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
import com.android.tools.lint.detector.api.Issue;
import com.intellij.psi.PsiElement;

/**
 * Interface implemented by some lint checks which need to bridge to
 * IDE inspections. This lets these issues be reported from IDE inspections
 * that do not run as part of lint (with access to lint's contexts, projects, etc)
 * without duplicating the logic of the check on the IDE side. See
 * the ResourceTypeInspection in the IDE.
 */
public interface LintInspectionBridge {
    /**
     * Report the given issue
     */
    void report(@NonNull Issue issue,
            @NonNull PsiElement locationNode,
            @NonNull PsiElement scopeNode,
            @NonNull String message);

    boolean isTestSource();

    JavaEvaluator getEvaluator();
}
