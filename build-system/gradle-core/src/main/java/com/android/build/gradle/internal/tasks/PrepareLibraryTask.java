/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LibraryCache;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.utils.FileCache;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

@ParallelizableTask
public class PrepareLibraryTask extends DefaultAndroidTask {

    private File bundle;

    // We register this field as @OutputDirectory depending on whether the build cache is enabled or
    // not; see method init of this class.
    @Nullable private File explodedDir;

    private boolean shouldUseBuildCache;

    // These fields are used only when the build cache is used
    @Nullable private FileCache buildCache;
    @Nullable private MavenCoordinates mavenCoordinates;

    /**
     * Initializes the properties of this task.
     */
    public void init(
            @NonNull File bundle,
            @NonNull File explodedDir,
            @NonNull Optional<FileCache> buildCache,
            @NonNull MavenCoordinates mavenCoordinates) {
        this.bundle = bundle;
        this.explodedDir = explodedDir;
        this.shouldUseBuildCache = shouldUseBuildCache(buildCache.isPresent(), mavenCoordinates);
        if (shouldUseBuildCache) {
            this.buildCache = buildCache.get();
            this.mavenCoordinates = mavenCoordinates;
        }

        // If the build cache is used, we must not register the exploded directory as the output
        // directory of this task as there are potential issues with incremental builds when
        // multiple tasks share the same output directory. Thus, we register the output directory if
        // and only if the build cache is not used (this action is equivalent to annotating the
        // exploded directory field as @OutputDirectory, except that we need to do it at run time).
        if (!shouldUseBuildCache) {
            this.getOutputs().dir(explodedDir);
        }
    }

    @InputFile
    public File getBundle() {
        return bundle;
    }

    @TaskAction
    public void prepare() throws IOException {
        Preconditions.checkNotNull(explodedDir, "explodedDir must not be null");
        if (shouldUseBuildCache) {
            Preconditions.checkNotNull(buildCache, "buildCache must not be null");
            Preconditions.checkNotNull(mavenCoordinates, "mavenCoordinates must not be null");
        }

        Consumer<File> unzipAarAction = (File explodedDir) -> {
            LibraryCache.unzipAar(bundle, explodedDir, getProject());
            // verify that we have a classes.jar, if we don't just create an empty one.
            File classesJar = new File(new File(explodedDir, "jars"), "classes.jar");
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

        // If the build cache is used, we create and cache the exploded aar using the cache's API;
        // otherwise, we explode the aar without using the cache.
        if (shouldUseBuildCache) {
            FileCache.Inputs buildCacheInputs = getBuildCacheInputs(mavenCoordinates, bundle);
            try {
                buildCache.createFileInCacheIfAbsent(
                        buildCacheInputs,
                        (explodedDir) -> unzipAarAction.accept(explodedDir));
            } catch (ExecutionException exception) {
                throw new RuntimeException(
                        String.format(
                                "Unable to unzip '%1$s' to '%2$s'",
                                bundle.getAbsolutePath(),
                                explodedDir.getAbsolutePath()),
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
                            bundle.getAbsolutePath(),
                            explodedDir.getAbsolutePath(),
                            buildCache.getCacheDirectory().getAbsolutePath()),
                        exception);
            }
        } else {
            unzipAarAction.accept(explodedDir);
        }
    }

    /**
     * Returns {@code true} if the build cache should be used for the prepare-library task, and
     * {@code false} otherwise.
     */
    public static boolean shouldUseBuildCache(
            boolean buildCacheEnabled, @NonNull MavenCoordinates mavenCoordinates) {
        // We use the build cache only when it is enabled *and* the Maven artifact is not a snapshot
        // version (to address http://b.android.com/228623)
        return buildCacheEnabled && !mavenCoordinates.getVersion().endsWith("-SNAPSHOT");
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * prepare-library task to use the build cache.
     */
    @NonNull
    public static FileCache.Inputs getBuildCacheInputs(
            @NonNull MavenCoordinates mavenCoordinates, @NonNull File artifactFile)
            throws IOException {
        // Convert mavenCoordinates to a unique string (similar to
        // MavenCoordinatesImpl#computeToString())
        StringBuilder sb = new StringBuilder();
        sb.append(mavenCoordinates.getGroupId())
                .append(':').append(mavenCoordinates.getArtifactId())
                .append(':').append(mavenCoordinates.getVersion());
        if (mavenCoordinates.getClassifier() != null) {
            sb.append(':').append(mavenCoordinates.getClassifier());
        }
        sb.append('@').append(mavenCoordinates.getPackaging());
        String mavenCoordinatesString = sb.toString().intern();

        return new FileCache.Inputs.Builder(FileCache.Command.PREPARE_LIBRARY)
                .putString(FileCacheInputParams.MAVEN_COORDINATES.name(), mavenCoordinatesString)
                .putFileHash(FileCacheInputParams.FILE_HASH.name(), artifactFile)
                .build();
    }

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link PrepareLibraryTask}.
     */
    private enum FileCacheInputParams {

        /** The Maven coordinates of the library. */
        MAVEN_COORDINATES,

        /** The hash of the library. */
        FILE_HASH,
    }
}
