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

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import java.util.Locale;
import org.gradle.api.InvalidUserDataException;

/**
 * Specification for native dependencies.
 *
 * This class provide different types of native dependencies.  Supported dependencies are:
 *
 * 1) Project dependency
 * Project dependency can be specified by setting the projectPath to the target Gradle project.
 * The target Gradle project must apply any of Gradle's component model plugin.
 * buildType and productFlavor can be optionally set to specified a build type or product flavor
 * respectively on the target project.  If unset, the build type and product flavor of the current
 * project will be use.
 *
 * 2) File dependency
 * User can set dependency on a specific library file by setting the libraryPath.  The abi must also
 * be set to indicate what ABI the target file is for.
 */
public class AndroidNativeDependencySpec {

    @Nullable
    private final String projectPath;
    @Nullable
    private final String libraryPath;
    @Nullable
    private final String buildType;
    @Nullable
    private final String productFlavor;
    @Nullable
    private final NativeDependencyLinkage linkage;

    public AndroidNativeDependencySpec(
            @Nullable String projectPath,
            @Nullable String libraryPath,
            @Nullable String buildType,
            @Nullable String productFlavor,
            @Nullable NativeDependencyLinkage linkage) {
        this.projectPath = projectPath;
        this.libraryPath = libraryPath;
        this.buildType = buildType;
        this.productFlavor = productFlavor;
        this.linkage = linkage;
    }

    @Nullable
    public String getProjectPath() {
        return projectPath;
    }

    @Nullable
    public String getLibraryPath() {
        return libraryPath;
    }

    @Nullable
    public String getBuildType() {
        return buildType;
    }

    @Nullable
    public String getProductFlavor() {
        return productFlavor;
    }

    @Nullable
    public NativeDependencyLinkage getLinkage() {
        return linkage;
    }

    public void validate() {
        if (projectPath == null && libraryPath == null) {
            throw new InvalidUserDataException(
                    "Native dependency must contain either project or library.");
        }
        if (projectPath != null && libraryPath != null) {
            throw new InvalidUserDataException(
                    "Native dependency cannot contain both project and library");
        }
    }

    public static class Builder {

        @Nullable
        private String projectPath;
        @Nullable
        private String libraryPath;
        @Nullable
        private String buildType;
        @Nullable
        private String productFlavor;
        @Nullable
        NativeDependencyLinkage linkage;

        public Builder project(String value) {
            projectPath = value;
            return this;
        }

        public Builder library(String value) {
            libraryPath = value;
            return this;
        }

        public Builder buildType(String value) {
            buildType = value;
            return this;
        }

        public Builder productFlavor(String value) {
            productFlavor = value;
            return this;
        }

        public Builder linkage(String value) {
            linkage = NativeDependencyLinkage.valueOf(value.toUpperCase(Locale.US));
            return this;
        }

        public Builder linkage(NativeDependencyLinkage value) {
            linkage = value;
            return this;
        }

        public AndroidNativeDependencySpec build() {
            return new AndroidNativeDependencySpec(
                    projectPath,
                    libraryPath,
                    buildType,
                    productFlavor,
                    linkage);
        }
    }
}
