/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.v2.CustomSourceDirectory;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of SourceProvider for testing that provides the default convention paths.
 */
class MockSourceProvider implements SourceProvider {

    public MockSourceProvider(String root) {
        mRoot = root;
    }

    private final String mRoot;

    @NonNull
    @Override
    public String getName() {
        return mRoot;
    }

    @NonNull
    @Override
    public Set<File> getJavaDirectories() {
        return Collections.singleton(new File(mRoot, "java"));
    }

    @NotNull
    @Override
    public Collection<File> getKotlinDirectories() {
        return Collections.singleton(new File(mRoot, "kotlin"));
    }

    @NonNull
    @Override
    public Set<File> getResourcesDirectories() {
        return Collections.singleton(new File(mRoot, "resources"));
    }

    @Override
    @NonNull
    public Set<File> getResDirectories() {
        return Collections.singleton(new File(mRoot, "res"));
    }

    @Override
    @NonNull
    public Set<File> getAssetsDirectories() {
        return Collections.singleton(new File(mRoot, "assets"));
    }

    @Override
    @NonNull
    public File getManifestFile() {
        return new File(mRoot, "AndroidManifest.xml");
    }

    @Override
    @NonNull
    public Set<File> getAidlDirectories() {
        return Collections.singleton(new File(mRoot, "aidl"));
    }

    @Override
    @NonNull
    public Set<File> getRenderscriptDirectories() {
        return Collections.singleton(new File(mRoot, "rs"));
    }

    @Override
    @NonNull
    public Set<File> getCDirectories() {
        return Collections.singleton(new File(mRoot, "jni"));
    }

    @Override
    @NonNull
    public Set<File> getCppDirectories() {
        return Collections.singleton(new File(mRoot, "jni"));
    }

    @NonNull
    @Override
    public Collection<File> getJniLibsDirectories() {
        return Collections.singleton(new File(mRoot, "jniLibs"));
    }

    @NonNull
    @Override
    public Collection<File> getShadersDirectories() {
        return Collections.singleton(new File(mRoot, "shaders"));
    }

    @NonNull
    @Override
    public Collection<File> getMlModelsDirectories() {
        return Collections.singleton(new File(mRoot, "ml"));
    }

    @NotNull
    @Override
    public Collection<CustomSourceDirectory> getCustomDirectories() {
        return Collections.EMPTY_LIST;
    }
}
