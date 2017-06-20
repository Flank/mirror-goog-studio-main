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
import com.android.build.gradle.internal.model.CoreCmakeOptions;
import java.io.File;
import org.gradle.api.Project;

/**
 * DSL object for per-module CMake configurations, such as the path to your <code>CMakeLists.txt
 * </code> build script and external native build output directory.
 */
public class CmakeOptions implements CoreCmakeOptions {
    @NonNull
    private final Project project;

    @Nullable
    private File path;

    @Nullable private File metadataOutputDirectory;

    public CmakeOptions(@NonNull Project project) {
        this.project = project;
    }

    /**
     * The relative path to your <code>CMakeLists.txt</code> build script.
     * <p>For example, if your
     * CMake build script is in the same folder as your module-level <code>build.gradle</code> file,
     * you simply pass the following:</p>
     * <p><code>path "CMakeLists.txt"</code></p>
     * <p>Gradle requires this build script to add your CMake project as a build dependency and pull
     * your native sources into your Android project.</p>
     */
    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    public void setPath(@Nullable Object path) {
        this.path = project.file(path);
    }

    @Override
    public void setPath(@NonNull File path) {
        this.path = path;
    }

    /**
     * The path to use for the external native build output directory. If the path does not exist,
     * the plugin creates it for you. Relative paths are relative to the <code>build.gradle</code>
     * file. If you do not specify this property, or you specify a path that is a subdirectory of
     * your project's <code>build</code> directory, the plugin uses the <code>
     * &lt;project_dir&gt;/app/.externalNativeBuild</code> directory as the default.
     */
    @Nullable
    @Override
    public File getMetadataOutputDirectory() {
        return metadataOutputDirectory;
    }

    @Override
    public void setMetadataOutputDirectory(@NonNull File metadataOutputDirectory) {
        this.metadataOutputDirectory = project.file(metadataOutputDirectory);
    }

    public void setMetadataOutputDirectory(@Nullable Object metadataOutputDirectory) {
        this.metadataOutputDirectory = project.file(metadataOutputDirectory);
    }
}
