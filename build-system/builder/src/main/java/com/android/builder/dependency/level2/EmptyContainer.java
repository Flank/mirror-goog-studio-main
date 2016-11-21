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

package com.android.builder.dependency.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Empty version of DependencyContainer;
 */
public class EmptyContainer implements DependencyContainer {

    private static final DependencyContainer EMPTY = new EmptyContainer();

    public static DependencyContainer get() {
        return EMPTY;
    }

    @NonNull
    @Override
    public ImmutableMap<Object, Dependency> getDependencyMap() {
        return ImmutableMap.of();
    }

    @NonNull
    @Override
    public ImmutableList<DependencyNode> getDependencies() {
        return ImmutableList.of();
    }

    @Override
    public boolean isSkipped(@NonNull Dependency dependency) {
        return false;
    }

    @Override
    public boolean isProvided(@NonNull Dependency dependency) {
        return false;
    }

    @NonNull
    @Override
    public ImmutableList<Dependency> getAllDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<Dependency> getAllPackagedDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<JavaDependency> getAllJavaDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<AndroidDependency> getAllAndroidDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<AtomDependency> getAllAtomDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<JavaDependency> getDirectJavaDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<JavaDependency> getDirectLocalJavaDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<AndroidDependency> getDirectAndroidDependencies() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ImmutableList<AtomDependency> getDirectAtomDependencies() {
        return ImmutableList.of();
    }

    @Nullable
    @Override
    public AtomDependency getBaseAtom() {
        return null;
    }
}
