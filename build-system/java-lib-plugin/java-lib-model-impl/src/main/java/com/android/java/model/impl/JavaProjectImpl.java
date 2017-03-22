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

package com.android.java.model.impl;

import com.android.annotations.NonNull;
import com.android.java.model.JavaProject;
import com.android.java.model.SourceSet;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of the {@link JavaProject} model object.
 */
public final class JavaProjectImpl implements JavaProject, Serializable {

    private static final long serialVersionUID = 1L;
    @NonNull private final String myName;
    @NonNull private final Collection<SourceSet> mySourceSets;
    @NonNull private final String myJavaLanguageLevel;

    public JavaProjectImpl(
            @NonNull String name,
            @NonNull Collection<SourceSet> sourceSetProvider,
            @NonNull String javaLanguageLevel) {
        this.myName = name;
        this.mySourceSets = sourceSetProvider;
        this.myJavaLanguageLevel = javaLanguageLevel;
    }

    @Override
    public long getModelVersion() {
        return serialVersionUID;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Collection<SourceSet> getSourceSets() {
        return mySourceSets;
    }

    @Override
    @NonNull
    public String getJavaLanguageLevel() {
        return myJavaLanguageLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavaProjectImpl that = (JavaProjectImpl) o;
        return Objects.equals(myName, that.myName)
                && Objects.equals(mySourceSets, that.mySourceSets)
                && Objects.equals(myJavaLanguageLevel, that.myJavaLanguageLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                myName,
                mySourceSets,
                myJavaLanguageLevel);
    }
}
