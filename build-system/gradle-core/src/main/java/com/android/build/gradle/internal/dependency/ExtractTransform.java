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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FN_CLASSES_JAR;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LibraryCache;
import com.android.build.gradle.internal.tasks.PrepareLibraryTask;
import com.android.builder.utils.FileCache;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.gradle.api.Project;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/**
 */
public abstract class ExtractTransform extends ArtifactTransform {

    private Project project;
    private FileCache fileCache;

    public void setProject(Project project) {
        this.project = project;
    }
    public void setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
    }

    protected File extractAar(@NonNull File aarFile) throws IOException {
        Consumer<File> unzipAarAction = (File explodedDir) -> {
            LibraryCache.unzipAar(aarFile, explodedDir, project);
            // verify that we have a classes.jar, if we don't just create an empty one.
            File classesJar = new File(new File(explodedDir, FD_JARS), FN_CLASSES_JAR);
            if (classesJar.exists()) {
                return;
            }
            try {
                Files.createParentDirs(classesJar);
                JarOutputStream jarOutputStream =
                        new JarOutputStream(
                                new BufferedOutputStream(new FileOutputStream(classesJar)),
                                new Manifest());
                jarOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException("Cannot create missing classes.jar", e);
            }
        };

        // If the build cache is enabled, we create and cache the exploded aar using the cache's
        // API; otherwise, we explode the aar without using the cache.
        FileCache.Inputs buildCacheInputs = PrepareLibraryTask.getCacheInputs(aarFile);
        File explodedLocation = fileCache.getFileInCache(buildCacheInputs);
        try {
            fileCache.createFileInCacheIfAbsent(
                    buildCacheInputs,
                    unzipAarAction::accept);
        } catch (ExecutionException exception) {
            throw new RuntimeException(
                    String.format(
                            "Unable to unzip '%1$s' to '%2$s'",
                            aarFile.getAbsolutePath(),
                            explodedLocation.getAbsolutePath()),
                    exception);
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format(
                            "Unable to unzip '%1$s' to '%2$s' or find the cached output '%2$s'"
                                    + " using the build cache at '%3$s'\n"
                                    + "Please fix the underlying cause if possible or file a"
                                    + " bug.\n"
                                    + "To suppress this warning, disable the build cache by"
                                    + " setting android.enableBuildCache=false in the"
                                    + " gradle.properties file.",
                            aarFile.getAbsolutePath(),
                            explodedLocation.getAbsolutePath(),
                            fileCache.getCacheDirectory().getAbsolutePath()),
                    exception);
        }

        return explodedLocation;
    }
}
