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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.services.DslServices;
import com.google.common.annotations.VisibleForTesting;
import javax.inject.Inject;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.gradle.api.Action;

public class ExternalNativeBuildOptions
        implements CoreExternalNativeBuildOptions,
                com.android.build.api.dsl.ExternalNativeBuildOptions {
    @NonNull
    private ExternalNativeNdkBuildOptions ndkBuildOptions;
    @NonNull
    private ExternalNativeCmakeOptions cmakeOptions;

    @VisibleForTesting
    public ExternalNativeBuildOptions() {
        ndkBuildOptions = new ExternalNativeNdkBuildOptions();
        cmakeOptions = new ExternalNativeCmakeOptions();
    }

    @Inject
    public ExternalNativeBuildOptions(@NonNull DslServices dslServices) {
        ndkBuildOptions = dslServices.newInstance(ExternalNativeNdkBuildOptions.class);
        cmakeOptions = dslServices.newInstance(ExternalNativeCmakeOptions.class);
    }

    public void _initWith(ExternalNativeBuildOptions that) {
        ndkBuildOptions._initWith(that.getExternalNativeNdkBuildOptions());
        cmakeOptions._initWith(that.getExternalNativeCmakeOptions());
    }

    @Nullable
    @Override
    public ExternalNativeNdkBuildOptions getExternalNativeNdkBuildOptions() {
        return getNdkBuild();
    }

    @NonNull
    @Override
    public ExternalNativeNdkBuildOptions getNdkBuild() {
        return ndkBuildOptions;
    }

    public void ndkBuild(Action<ExternalNativeNdkBuildOptions> action) {
        action.execute(ndkBuildOptions);
    }

    @Nullable
    @Override
    public ExternalNativeCmakeOptions getExternalNativeCmakeOptions() {
        return getCmake();
    }

    @NonNull
    @Override
    public ExternalNativeCmakeOptions getCmake() {
        return cmakeOptions;
    }

    public void cmake(Action<ExternalNativeCmakeOptions> action) {
        action.execute(cmakeOptions);
    }

    @Override
    public void ndkBuild(
            @NonNull
                    Function1<? super com.android.build.api.dsl.ExternalNativeNdkBuildOptions, Unit>
                            action) {
        action.invoke(ndkBuildOptions);
    }

    @Override
    public void cmake(
            @NonNull
                    Function1<? super com.android.build.api.dsl.ExternalNativeCmakeOptions, Unit>
                            action) {
        action.invoke(cmakeOptions);
    }
}
