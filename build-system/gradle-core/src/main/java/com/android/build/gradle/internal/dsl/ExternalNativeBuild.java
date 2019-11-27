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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import javax.inject.Inject;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.gradle.api.Action;

/** See {@link com.android.build.api.dsl.ExternalNativeBuild} */
public class ExternalNativeBuild
        implements CoreExternalNativeBuild,
                com.android.build.api.dsl.ExternalNativeBuild<CmakeOptions, NdkBuildOptions> {
    private NdkBuildOptions ndkBuild;
    private CmakeOptions cmake;

    @Inject
    public ExternalNativeBuild(@NonNull DslScope dslScope) {
        ndkBuild = dslScope.getObjectFactory().newInstance(NdkBuildOptions.class, dslScope);
        cmake = dslScope.getObjectFactory().newInstance(CmakeOptions.class, dslScope);
    }

    @NonNull
    @Override
    public NdkBuildOptions getNdkBuild() {
        return this.ndkBuild;
    }

    /* Not directly in interface as having a non-void return type is unconventional */
    public NdkBuildOptions ndkBuild(Action<NdkBuildOptions> action) {
        action.execute(ndkBuild);
        return this.ndkBuild;
    }

    @Override
    public void ndkBuild(Function1<? super NdkBuildOptions, Unit> action) {
        action.invoke(ndkBuild);
    }

    @NonNull
    @Override
    public CmakeOptions getCmake() {
        return cmake;
    }

    /* Not directly in interface as having a non-void return type is unconventional */
    public CmakeOptions cmake(Action<CmakeOptions> action) {
        action.execute(cmake);
        return this.cmake;
    }

    @Override
    public void cmake(Function1<? super CmakeOptions, Unit> action) {
        action.invoke(cmake);
    }
}
