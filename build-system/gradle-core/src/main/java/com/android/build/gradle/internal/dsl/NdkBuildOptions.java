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
import com.android.build.gradle.internal.model.CoreNdkBuildOptions;
import java.io.File;
import org.gradle.api.Project;

/**
 * DSL object for per-module ndk-build configurations, such as the path to your <code>Android.mk
 * </code> build script and external native build output directory.
 */
public class NdkBuildOptions implements CoreNdkBuildOptions {
    @NonNull
    private final Project project;

    @Nullable
    private File path;

    @Nullable private File buildStagingDirectory;

    public NdkBuildOptions(@NonNull Project project) {
        this.project = project;
    }

    /**
     * The relative path to your <code>Android.mk</code> build script.
     * <p>For example, if your
     * ndk-build script was in the same folder as your module-level <code>build.gradle</code> file,
     * you simply pass the following:</p>
     * <p><code>path "Android.mk"</code></p>
     * <p>Gradle requires this build script to
     * add your ndk-build project as a build dependency and pull your native sources into your
     * Android project. Gradle also includes the <code>Application.mk</code> file if it is located
     * in the same directory as your <code>Android.mk</code> file.</p>
     */
    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    public void setPath(@NonNull Object path) {
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
    public File getBuildStagingDirectory() {
        return buildStagingDirectory;
    }

    @Override
    public void setBuildStagingDirectory(@NonNull File buildStagingDirectory) {
        this.buildStagingDirectory = project.file(buildStagingDirectory);
    }

    public void setBuildStagingDirectory(@Nullable Object buildStagingDirectory) {
        this.buildStagingDirectory = project.file(buildStagingDirectory);
    }
}
