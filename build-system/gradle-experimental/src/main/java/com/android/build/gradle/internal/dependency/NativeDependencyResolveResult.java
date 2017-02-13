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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import java.util.List;
import org.gradle.nativeplatform.NativeLibraryBinary;

/**
 * Result of resolving dependencies for a native project.
 */
public class NativeDependencyResolveResult {

    @NonNull
    private List<NativeLibraryArtifact> nativeArtifacts = Lists.newArrayList();

    @NonNull
    private List<NativeLibraryBinary> prebuiltLibraries = Lists.newArrayList();

    @NonNull
    public List<NativeLibraryArtifact> getNativeArtifacts() {
        return nativeArtifacts;
    }

    @NonNull
    public List<NativeLibraryBinary> getPrebuiltLibraries() {
        return prebuiltLibraries;
    }
}
