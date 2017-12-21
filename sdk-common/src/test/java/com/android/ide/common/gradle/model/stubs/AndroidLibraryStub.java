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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public class AndroidLibraryStub extends AndroidBundleStub implements AndroidLibrary {
    @NonNull private final Collection<File> myLocalJars;
    @NonNull private final File myProguardRules;
    @NonNull private final File myLintJar;
    @NonNull private final File myPublicResources;

    public AndroidLibraryStub() {
        this(
                Lists.newArrayList(new File("jar")),
                new File("proguardRules"),
                new File("lintJar"),
                new File("publicResources"));
    }

    public AndroidLibraryStub(
            @NonNull Collection<File> jars,
            @NonNull File proguardRules,
            @NonNull File lintJar,
            @NonNull File publicResources) {
        myLocalJars = jars;
        myProguardRules = proguardRules;
        myLintJar = lintJar;
        myPublicResources = publicResources;
    }

    @Override
    @NonNull
    public Collection<File> getLocalJars() {
        return myLocalJars;
    }

    @Override
    @NonNull
    public File getJniFolder() {
        throw new UnusedModelMethodException("getJniFolder");
    }

    @Override
    @NonNull
    public File getAidlFolder() {
        throw new UnusedModelMethodException("getRenderscriptFolder");
    }

    @Override
    @NonNull
    public File getRenderscriptFolder() {
        throw new UnusedModelMethodException("getRenderscriptFolder");
    }

    @Override
    @NonNull
    public File getProguardRules() {
        return myProguardRules;
    }

    @Override
    @NonNull
    public File getLintJar() {
        return myLintJar;
    }

    @Override
    @NonNull
    public File getExternalAnnotations() {
        throw new UnusedModelMethodException("getExternalAnnotations");
    }

    @Override
    @NonNull
    public File getPublicResources() {
        return myPublicResources;
    }

    @Override
    @NonNull
    public File getSymbolFile() {
        throw new UnusedModelMethodException("getSymbolFile");
    }

    @Override
    @Deprecated
    public boolean isOptional() {
        throw new UnusedModelMethodException("isOptional");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AndroidLibrary)) {
            return false;
        }
        AndroidLibrary library = (AndroidLibrary) o;
        return Objects.equals(getBundle(), library.getBundle())
                && Objects.equals(getFolder(), library.getFolder())
                && Objects.equals(getLibraryDependencies(), library.getLibraryDependencies())
                && Objects.equals(getJavaDependencies(), library.getJavaDependencies())
                && Objects.equals(getManifest(), library.getManifest())
                && Objects.equals(getJarFile(), library.getJarFile())
                && Objects.equals(getResFolder(), library.getResFolder())
                && Objects.equals(getAssetsFolder(), library.getAssetsFolder())
                && Objects.equals(getProjectVariant(), library.getProjectVariant())
                && Objects.equals(getLocalJars(), library.getLocalJars())
                && Objects.equals(getProguardRules(), library.getProguardRules())
                && Objects.equals(getLintJar(), library.getLintJar())
                && Objects.equals(getPublicResources(), library.getPublicResources());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getBundle(),
                getFolder(),
                getLibraryDependencies(),
                getJavaDependencies(),
                getManifest(),
                getJarFile(),
                getResFolder(),
                getAssetsFolder(),
                getProjectVariant(),
                getLocalJars(),
                getProguardRules(),
                getLintJar(),
                getPublicResources());
    }

    @Override
    public String toString() {
        return "AndroidLibraryStub{"
                + "myLocalJars="
                + myLocalJars
                + ", myProguardRules="
                + myProguardRules
                + ", myLintJar="
                + myLintJar
                + ", myPublicResources="
                + myPublicResources
                + "} "
                + super.toString();
    }
}
