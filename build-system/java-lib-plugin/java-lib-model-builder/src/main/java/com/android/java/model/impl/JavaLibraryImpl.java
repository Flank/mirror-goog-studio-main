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
import com.android.annotations.Nullable;
import com.android.java.model.JavaLibrary;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of the {@link JavaLibrary} interface.
 */
public final class JavaLibraryImpl implements JavaLibrary, Serializable {

    @Nullable private final String myProject;
    @NonNull private final String myName;
    @Nullable private final File myJarFile;

    public JavaLibraryImpl(@Nullable String project, @NonNull String name, @Nullable File jarFile) {
        this.myProject = project;
        this.myName = name;
        this.myJarFile = jarFile;
    }

    @Override
    @Nullable
    public String getProject() {
        return myProject;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @Nullable
    public File getJarFile() {
        return myJarFile;
    }

    @Override
    @Nullable
    public File getSource() {
        return null;
    }

    @Override
    @Nullable
    public File getJavadoc() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavaLibraryImpl that = (JavaLibraryImpl) o;
        return Objects.equals(myProject, that.myProject)
                && Objects.equals(myName, that.myName)
                && Objects.equals(myJarFile, that.myJarFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                myProject,
                myName,
                myJarFile);
    }
}
