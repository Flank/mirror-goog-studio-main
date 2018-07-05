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

package com.android.build.gradle.internal.transforms;

import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Transform to extract jars.
 *
 */
public class ExtractJarsTransform extends Transform {

    @VisibleForTesting
    static Logger LOGGER = Logging.getLogger(ExtractJarsTransform.class);

    @NonNull
    private final Set<ContentType> contentTypes;
    @NonNull
    private final Set<Scope> scopes;

    public ExtractJarsTransform(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<Scope> scopes) {
        this.contentTypes = contentTypes;
        this.scopes = scopes;
    }

    @NonNull
    @Override
    public String getName() {
        return "extractJars";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return contentTypes;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return scopes;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws IOException, TransformException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // as_input transform and no referenced scopes, all the inputs will in InputOutputStreams.
        final boolean extractCode = contentTypes.contains(DefaultContentType.CLASSES);

        if (!isIncremental) {
            outputProvider.deleteAll();
        }

        try {
            WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

            for (TransformInput input : transformInvocation.getInputs()) {
                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    File dirOutput =
                            outputProvider.getContentLocation(
                                    dirInput.getFile().toString(),
                                    dirInput.getContentTypes(),
                                    dirInput.getScopes(),
                                    Format.DIRECTORY);
                    // since we do a full copy, we have to delete the output first.
                    // FIXME make this incremental!
                    if (dirOutput.isDirectory()) {
                        FileUtils.deleteDirectoryContents(dirOutput);
                    }
                    if (dirInput.getFile().exists()) {
                        org.apache.commons.io.FileUtils.copyDirectory(
                                dirInput.getFile(), dirOutput);
                    }
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    final File jarFile = jarInput.getFile();

                    // create an output folder for this jar, keeping its type and scopes.
                    final File outJarFolder =
                            outputProvider.getContentLocation(
                                    jarInput.getFile().toString(),
                                    jarInput.getContentTypes(),
                                    jarInput.getScopes(),
                                    Format.DIRECTORY);
                    FileUtils.mkdirs(outJarFolder);

                    if (!isIncremental) {
                        executor.execute(() -> {
                            extractJar(outJarFolder, jarFile, extractCode);
                            return null;
                        });
                    } else {
                        switch (jarInput.getStatus()) {
                            case CHANGED:
                                executor.execute(() -> {
                                    FileUtils.cleanOutputDir(outJarFolder);
                                    extractJar(outJarFolder, jarFile, extractCode);
                                    return null;
                                });
                                break;
                            case ADDED:
                                executor.execute(() -> {
                                    extractJar(outJarFolder, jarFile, extractCode);
                                    return null;
                                });
                                break;
                            case REMOVED:
                                executor.execute(
                                        () -> {
                                            FileUtils.cleanOutputDir(outJarFolder);
                                            return null;
                                        });
                                break;
                            case NOTCHANGED:
                                break;
                        }
                    }
                }
            }

            executor.waitForTasksWithQuickFail(true);

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    private static void extractJar(
            @NonNull File outJarFolder,
            @NonNull File jarFile,
            boolean extractCode) throws IOException {
        mkdirs(outJarFolder);
        HashSet<String> lowerCaseNames = new HashSet<>();
        boolean foundCaseInsensitiveIssue = false;

        try (InputStream fis = new BufferedInputStream(new FileInputStream(jarFile));
             ZipInputStream zis = new ZipInputStream(fis)) {
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    String name = entry.getName();

                    // do not take directories
                    if (entry.isDirectory()) {
                        continue;
                    }

                    foundCaseInsensitiveIssue = foundCaseInsensitiveIssue ||
                            !lowerCaseNames.add(name.toLowerCase(Locale.US));

                    Action action = getAction(name, extractCode);
                    if (action == Action.COPY) {
                        File outputFile = new File(outJarFolder,
                                name.replace('/', File.separatorChar));
                        mkdirs(outputFile.getParentFile());

                        try (OutputStream outputStream =
                                     new BufferedOutputStream(new FileOutputStream(outputFile))) {
                            ByteStreams.copy(zis, outputStream);
                            outputStream.flush();
                        }
                    }
                } finally {
                    zis.closeEntry();
                }
            }

        }

        if (foundCaseInsensitiveIssue) {
            LOGGER.error(
                    "Jar '{}' contains multiple entries which will map to "
                            + "the same file on case insensitive file systems.\n"
                            + "This can be caused by obfuscation with useMixedCaseClassNames.\n"
                            + "This build will be incorrect on case insensitive "
                            + "file systems.", jarFile.getAbsolutePath());
        }
    }

    /**
     * Define all possible actions for a Jar file entry.
     */
    enum Action {
        /**
         * Copy the file to the output destination.
         */
        COPY,
        /**
         * Ignore the file.
         */
        IGNORE
    }

    /**
     * Provides an {@link Action} for the archive entry.
     * @param archivePath the archive entry path in the archive.
     * @param extractCode whether to extract class files
     * @return the action to implement.
     */
    @NonNull
    public static Action getAction(@NonNull String archivePath, boolean extractCode) {
        // Manifest files are never merged.
        if (JarFile.MANIFEST_NAME.equals(archivePath)) {
            return Action.IGNORE;
        }

        // split the path into segments.
        String[] segments = archivePath.split("/");

        // empty path? skip to next entry.
        if (segments.length == 0) {
            return Action.IGNORE;
        }

        return PackagingUtils.checkFileForApkPackaging(archivePath, extractCode)
                ? Action.COPY
                : Action.IGNORE;
    }

}
