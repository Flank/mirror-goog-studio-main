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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidAtom;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.util.Set;

/**
 * Packaging scope specific to the base atom.
 */
public class BaseAtomPackagingScope extends AtomPackagingScope {

    public BaseAtomPackagingScope(@NonNull VariantOutputScope variantOutputScope,
            @NonNull AndroidAtom androidAtom) {
        super(variantOutputScope, androidAtom);
    }

    @NonNull
    @Override
    public File getFinalResourcesFile() {
        return androidAtom.getResourcePackage();
    }

    @NonNull
    @Override
    public Set<File> getDexFolders() {
        return ImmutableSet.<File>builder().add(androidAtom.getDexFolder()).build();
    }

}
