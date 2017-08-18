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

    @Nullable private File buildStagingDirectory;

    // CMake version to use. If it's null, it'll default to the CMake shipped with the SDK
    @Nullable private String version;

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
    public File getBuildStagingDirectory() {
        return buildStagingDirectory;
    }

    @Override
    public void setBuildStagingDirectory(@NonNull File buildStagingDirectory) {
        this.buildStagingDirectory = project.file(buildStagingDirectory);
    }

    /**
     * The version of CMake that Android Studio should use to build the native library.
     *
     * <p>When you specify a version of CMake, as shown below, the plugin searches for the
     * appropriate CMake binary within your PATH environmental variable.
     *
     * <pre>
     *     android {
     *         ...
     *         externalNativeBuild {
     *             cmake {
     *                 ...
     *                 // Specifies the version of CMake the Android plugin should use. You need to
     *                 // include the path to the CMake binary of this version to your PATH
     *                 // environmental variable.
     *                 version "3.7.1"
     *             }
     *         }
     *     }
     * </pre>
     *
     * <p>If you do not configure this property, the plugin uses the version of CMake available from
     * the <a href="https://developer.android.com/studio/intro/update.html#sdk-manager">SDK
     * manager</a>. (Android Studio prompts you to download this version of CMake if you haven't
     * already done so)
     *
     * <p>Alternatively, you can specify a version of CMake in your project's <code>local.properties
     * </code> file, as shown below:
     *
     * <pre>
     *     // The path may be either absolute or relative to the the local.properties file you are
     *     // editing.
     *     cmake.dir="&ltpath-to-cmake&gt"
     * </pre>
     */
    @Nullable
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(@NonNull String version) {
        this.version = version;
    }

    public void setBuildStagingDirectory(@Nullable Object buildStagingDirectory) {
        this.buildStagingDirectory = project.file(buildStagingDirectory);
    }
}
