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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import java.io.File;
import org.gradle.api.tasks.Copy;

/** Configuration action for a copy-lint task. */
public class CopyLintConfigAction  implements TaskConfigAction<Copy> {

    @NonNull private VariantScope variantScope;

    public CopyLintConfigAction(@NonNull VariantScope variantScope) {
        this.variantScope = variantScope;
    }

    @NonNull
    @Override
    public String getName() {
        return variantScope.getTaskName("copy", "Lint");
    }

    @NonNull
    @Override
    public Class<Copy> getType() {
        return Copy.class;
    }

    @Override
    public void execute(@NonNull Copy copyLint) {
        copyLint.from(
                new File(variantScope.getGlobalScope().getIntermediatesDir(), "lint/lint.jar"));
        copyLint.into(variantScope.getBaseBundleDir());
    }
}
