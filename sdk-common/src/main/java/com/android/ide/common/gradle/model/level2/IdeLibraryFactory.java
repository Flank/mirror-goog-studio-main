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
package com.android.ide.common.gradle.model.level2;

import static com.android.builder.model.level2.Library.*;
import static com.android.ide.common.gradle.model.IdeLibraries.computeAddress;
import static com.android.ide.common.gradle.model.IdeLibraries.isLocalAarModule;
import static com.android.utils.FileUtils.join;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.ModelCache;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.stream.Collectors;

/** Creates instance of {@link Library}. */
class IdeLibraryFactory {
    /**
     * @param library Instance of level 2 library returned by android plugin.
     * @param modelCache Cache that stores previously copied entries.
     * @return Deep copy of {@link Library} based on library type.
     */
    @NonNull
    Library create(@NonNull Library library, @NonNull ModelCache modelCache) {
        if (library.getType() == LIBRARY_ANDROID) {
            File folder = library.getFolder();
            return new IdeAndroidLibrary(
                    library,
                    modelCache,
                    library.getArtifactAddress(),
                    library.getFolder(),
                    getFullPath(folder, library.getManifest()),
                    getFullPath(folder, library.getJarFile()),
                    getFullPath(folder, library.getResFolder()),
                    getFullPath(folder, library.getAssetsFolder()),
                    getLocalJars(library, folder),
                    getFullPath(folder, library.getJniFolder()),
                    getFullPath(folder, library.getAidlFolder()),
                    getFullPath(folder, library.getRenderscriptFolder()),
                    getFullPath(folder, library.getProguardRules()),
                    getFullPath(folder, library.getLintJar()),
                    getFullPath(folder, library.getExternalAnnotations()),
                    getFullPath(folder, library.getPublicResources()),
                    library.getArtifact(),
                    library.getSymbolFile());
        }
        if (library.getType() == LIBRARY_JAVA) {
            return new IdeJavaLibrary(
                    library.getArtifactAddress(), library.getArtifact(), modelCache, library);
        }
        if (library.getType() == LIBRARY_MODULE) {
            return new IdeModuleLibrary(library, library.getArtifactAddress(), modelCache);
        }
        throw new UnsupportedOperationException("Unknown library type " + library.getType());
    }

    @NonNull
    private static ImmutableList<String> getLocalJars(
            @NonNull Library library, @NonNull File libraryFolderPath) {
        return library.getLocalJars()
                .stream()
                .map(jar -> getFullPath(libraryFolderPath, jar))
                .collect(ImmutableCollectors.toImmutableList());
    }

    /**
     * @param androidLibrary Instance of {@link AndroidLibrary} returned by android plugin.
     * @param moduleBuildDirs Instance of {@link BuildFolderPaths} that contains map from project
     *     path to build directory for all modules.
     * @param modelCache Cache that stores previously copied entries.
     * @return Instance of {@link Library} based on dependency type.
     */
    @NonNull
    Library create(
            @NonNull AndroidLibrary androidLibrary,
            @NonNull BuildFolderPaths moduleBuildDirs,
            @NonNull ModelCache modelCache) {
        // If the dependency is a sub-module that wraps local aar, it should be considered as external dependency, i.e. type LIBRARY_ANDROID.
        // In AndroidLibrary, getProject() of such dependency returns non-null project name, but they should be converted to IdeLevel2AndroidLibrary.
        // Identify such case with the location of aar bundle.
        // If the aar bundle is inside of build directory of sub-module, then it's regular library module dependency, otherwise it's a wrapped aar module.
        if (androidLibrary.getProject() != null
                && !isLocalAarModule(androidLibrary, moduleBuildDirs)) {
            return new IdeModuleLibrary(androidLibrary, computeAddress(androidLibrary), modelCache);
        } else {
            return new IdeAndroidLibrary(
                    androidLibrary,
                    modelCache,
                    computeAddress(androidLibrary),
                    androidLibrary.getFolder(),
                    androidLibrary.getManifest().getPath(),
                    androidLibrary.getJarFile().getPath(),
                    androidLibrary.getResFolder().getPath(),
                    androidLibrary.getAssetsFolder().getPath(),
                    androidLibrary
                            .getLocalJars()
                            .stream()
                            .map(File::getPath)
                            .collect(Collectors.toList()),
                    androidLibrary.getJniFolder().getPath(),
                    androidLibrary.getAidlFolder().getPath(),
                    androidLibrary.getRenderscriptFolder().getPath(),
                    androidLibrary.getProguardRules().getPath(),
                    androidLibrary.getLintJar().getPath(),
                    androidLibrary.getExternalAnnotations().getPath(),
                    androidLibrary.getPublicResources().getPath(),
                    androidLibrary.getBundle(),
                    getSymbolFilePath(androidLibrary));
        }
    }

    @NonNull
    private static String getFullPath(@NonNull File libraryFolderPath, @NonNull String fileName) {
        return join(libraryFolderPath, fileName).getPath();
    }

    @Nullable
    private static String getSymbolFilePath(@NonNull AndroidLibrary androidLibrary) {
        try {
            return androidLibrary.getSymbolFile().getPath();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * @param javaLibrary Instance of {@link JavaLibrary} returned by android plugin.
     * @param modelCache Cache that stores previously copied entries.
     * @return Instance of {@link Library} based on dependency type.
     */
    @NonNull
    Library create(@NonNull JavaLibrary javaLibrary, @NonNull ModelCache modelCache) {
        String project = getProject(javaLibrary);
        if (project != null) {
            // Java modules don't have variant.
            return new IdeModuleLibrary(javaLibrary, computeAddress(javaLibrary), modelCache);
        } else {
            return new IdeJavaLibrary(
                    computeAddress(javaLibrary), javaLibrary.getJarFile(), modelCache, javaLibrary);
        }
    }

    @Nullable
    private static String getProject(@NonNull JavaLibrary javaLibrary) {
        try {
            return javaLibrary.getProject();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * @param projectPath Name of module dependencies.
     * @param modelCache Cache that stores previously copied entries.
     * @return An instance of {@link Library} of type LIBRARY_MODULE.
     */
    @NonNull
    static Library create(
            @NonNull String projectPath,
            @NonNull String artifactAddress,
            @NonNull ModelCache modelCache,
            @Nullable String buildId) {
        return new IdeModuleLibrary(projectPath, artifactAddress, modelCache, buildId);
    }
}
