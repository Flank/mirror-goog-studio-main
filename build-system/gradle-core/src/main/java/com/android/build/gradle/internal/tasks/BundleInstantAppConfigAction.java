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

import static com.android.SdkConstants.EXT_ZIP;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.tasks.bundling.Zip;

/** Config Action for an instantApp bundle. */
public class BundleInstantAppConfigAction implements TaskConfigAction<Zip> {

    @NonNull private VariantOutputScope variantOutputScope;

    public BundleInstantAppConfigAction(@NonNull VariantOutputScope variantOutputScope) {
        this.variantOutputScope = variantOutputScope;
    }

    @NonNull
    @Override
    public String getName() {
        return variantOutputScope.getTaskName("package", "InstantApp");
    }

    @NonNull
    @Override
    public Class<Zip> getType() {
        return Zip.class;
    }

    @Override
    public void execute(@NonNull Zip bundle) {
        bundle.from(
                variantOutputScope
                        .getGlobalScope()
                        .getProject()
                        .files(
                                new Callable<Set<File>>() {
                                    @Override
                                    public Set<File> call() throws Exception {
                                        return variantOutputScope
                                                .getVariantScope()
                                                .getVariantConfiguration()
                                                .getFlatAndroidAtomsDependencies()
                                                .stream()
                                                .map(
                                                        variantOutputScope.getVariantScope()
                                                                ::getPackageAtom)
                                                .collect(Collectors.toSet());
                                    }
                                }));
        bundle.setDestinationDir(variantOutputScope.getFinalPackage().getParentFile());
        bundle.setArchiveName(variantOutputScope.getFinalPackage().getName());
        bundle.setExtension(EXT_ZIP);
    }
}
