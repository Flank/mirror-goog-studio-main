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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.JavaFileManager;

public class LintCoreProjectEnvironment extends JavaCoreProjectEnvironment {
    @NonNull
    public static LintCoreProjectEnvironment create() {
        Disposable parentDisposable = Disposer.newDisposable();
        return new LintCoreProjectEnvironment(parentDisposable,
                LintCoreApplicationEnvironment.get());
    }

    @Override
    protected void preregisterServices() {
        super.preregisterServices();

        ExtensionsArea area = Extensions.getArea(getProject());
        CoreApplicationEnvironment.registerExtensionPoint(area, PsiTreeChangePreprocessor.EP_NAME, PsiTreeChangePreprocessor.class);
        CoreApplicationEnvironment.registerExtensionPoint(area, PsiElementFinder.EP_NAME, PsiElementFinder.class);
    }

    public LintCoreProjectEnvironment(Disposable parentDisposable,
            CoreApplicationEnvironment applicationEnvironment) {
        super(parentDisposable, applicationEnvironment);

        ExtensionsArea area = Extensions.getArea(getProject());
        LintCoreApplicationEnvironment.registerProjectExtensionPoints(area);
        LintCoreApplicationEnvironment.registerProjectServicesForCLI(this);
        LintCoreApplicationEnvironment.registerProjectServices(this);
    }

    @Override
    protected JavaFileManager createCoreFileManager() {
        return super.createCoreFileManager();
    }
}
