/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.build.gradle.internal.dependency.AndroidNativeDependencySpec;
import com.android.build.gradle.internal.dependency.AndroidNativeDependencySpecContainer;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.gradle.api.Action;

/**
 * Implementation of {@link AndroidNativeDependencySpecContainer}
 */
public class DefaultAndroidNativeDependencySpecContainer implements
        AndroidNativeDependencySpecContainer {

    private final List<AndroidNativeDependencySpec.Builder> builders =
            new LinkedList<>();

    @Override
    public AndroidNativeDependencySpec.Builder project(final String value) {
        return doCreate(builder -> builder.project(value));
    }

    @Override
    public AndroidNativeDependencySpec.Builder library(final String value) {
        return doCreate(builder -> builder.library(value));
    }

    @Override
    public AndroidNativeDependencySpec.Builder buildType(final String value) {
        return doCreate(builder -> builder.buildType(value));
    }

    @Override
    public AndroidNativeDependencySpec.Builder productFlavor(final String value) {
        return doCreate(builder -> builder.productFlavor(value));
    }

    @Override
    public Collection<AndroidNativeDependencySpec> getDependencies() {
        if (builders.isEmpty()) {
            return Collections.emptySet();
        }
        return ImmutableSet.copyOf(Lists.transform(builders,
                AndroidNativeDependencySpec.Builder::build));
    }

    private AndroidNativeDependencySpec.Builder doCreate(
            Action<? super AndroidNativeDependencySpec.Builder> action) {
        AndroidNativeDependencySpec.Builder builder = new AndroidNativeDependencySpec.Builder();
        action.execute(builder);
        builders.add(builder);
        return builder;
    }

    @Override
    public boolean isEmpty() {
        return builders.isEmpty();
    }
}
