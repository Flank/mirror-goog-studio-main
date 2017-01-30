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

import static com.android.SdkConstants.FD_APK_NATIVE_LIBS;
import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.packaging.PackagingFileAction;
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.ZipAbortException;
import com.android.builder.packaging.ZipEntryFilter;
import com.android.builder.packaging.PackagingUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Transform to merge all the Java resources.
 *
 * Based on the value of {@link #getInputTypes()} this will either process native libraries
 * or java resources. While native libraries inside jars are technically java resources, they
 * must be handled separately.
 */
public class MergeJavaResourcesTransform extends Transform {

    private interface FileValidator {
        boolean validateJarPath(@NonNull String path);
        boolean validateFolderPath(@NonNull String path, boolean isSubProject);
        @NonNull
        String folderPathToKey(@NonNull String path, boolean isSubProject);
    }

    @NonNull
    private final ParsedPackagingOptions packagingOptions;

    @NonNull
    private final String name;

    @NonNull
    private final Set<Scope> mergeScopes;
    @NonNull
    private final Set<ContentType> mergedType;
    @NonNull
    private final FileValidator validator;

    public MergeJavaResourcesTransform(
            @NonNull PackagingOptions packagingOptions,
            @NonNull Set<Scope> mergeScopes,
            @NonNull ContentType mergedType,
            @NonNull String name) {
        this.packagingOptions = new ParsedPackagingOptions(packagingOptions);
        this.name = name;
        this.mergeScopes = Sets.immutableEnumSet(mergeScopes);
        this.mergedType = ImmutableSet.of(mergedType);


        if (mergedType == QualifiedContent.DefaultContentType.RESOURCES) {
            validator = new FileValidator() {
                @Override
                public boolean validateJarPath(@NonNull String path) {
                    return !path.endsWith(SdkConstants.DOT_CLASS) &&
                            !path.endsWith(SdkConstants.DOT_NATIVE_LIBS);
                }

                @Override
                public boolean validateFolderPath(@NonNull String path, boolean isSubProject) {
                    return !path.endsWith(SdkConstants.DOT_CLASS) &&
                            !path.endsWith(SdkConstants.DOT_NATIVE_LIBS);

                }

                @NonNull
                @Override
                public String folderPathToKey(@NonNull String path, boolean isSubProject) {
                    return path;
                }
            };

        } else if (mergedType == ExtendedContentType.NATIVE_LIBS) {
            validator = new NativeLibValidator();

        } else {
            throw new UnsupportedOperationException(
                    "mergedType param must be RESOURCES or NATIVE_LIBS");
        }
    }

    private static final class NativeLibValidator implements FileValidator {
        private final Pattern jarAbiPattern = Pattern.compile("lib/([^/]+)/[^/]+");
        private final Pattern folderAbiPattern = Pattern.compile("([^/]+)/[^/]+");
        private final Pattern filenamePattern = Pattern.compile(".*\\.so");

        @Override
        public boolean validateJarPath(@NonNull String path) {
            // extract abi from path, checking the general path structure (lib/<abi>/<filename>)
            Matcher m = jarAbiPattern.matcher(path);

            // if the ABI is accepted, check the 3rd segment
            if (m.matches()) {
                // remove the beginning of the path (lib/<abi>/)
                String filename = path.substring(5 + m.group(1).length());
                // and check the filename
                return filenamePattern.matcher(filename).matches() ||
                        SdkConstants.FN_GDBSERVER.equals(filename) ||
                        SdkConstants.FN_GDB_SETUP.equals(filename);
            }

            return false;
        }

        @Override
        public boolean validateFolderPath(@NonNull String path, boolean isSubProject) {
            if (isSubProject) {
                // sub project publish their jni with lib/ so we use the jar validation instead.
                return validateJarPath(path);
            }

            // extract abi from path, checking the general path structure (<abi>/<filename>)
            Matcher m = folderAbiPattern.matcher(path);

            // if the ABI is accepted, check the 3rd segment
            if (m.matches()) {
                // remove the beginning of the path (<abi>/)
                String filename = path.substring(1 + m.group(1).length());
                // and check the filename
                return filenamePattern.matcher(filename).matches() ||
                        SdkConstants.FN_GDBSERVER.equals(filename) ||
                        SdkConstants.FN_GDB_SETUP.equals(filename);
            }

            return false;
        }

        @NonNull
        @Override
        public String folderPathToKey(@NonNull String path, boolean isSubProject) {
            if (isSubProject) {
                return path;
            }
            return FD_APK_NATIVE_LIBS + "/" + path;
        }
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return mergedType;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return mergeScopes;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of(
                "exclude", packagingOptions.getExcludePatterns(),
                "pickFirst", packagingOptions.getPickFirstPatterns(),
                "merge", packagingOptions.getMergePatterns());
    }

    @Override
    public boolean isIncremental() {
        // FIXME
        return false;
    }

    private static final class MergeSource {
        @NonNull
        private final QualifiedContent sourceInput;
        @NonNull
        private final String sourcePath;

        public MergeSource(
                @NonNull QualifiedContent sourceInput,
                @NonNull String sourcePath) {
            this.sourceInput = sourceInput;
            this.sourcePath = sourcePath;
        }

        static MergeSource of(@NonNull QualifiedContent sourceInput,
                @NonNull String path) {
            return new MergeSource(sourceInput, path);
        }

        @NonNull
        public String getSourcePath() {
            return sourcePath;
        }

        @NonNull
        public QualifiedContent getSourceInput() {
            return sourceInput;
        }
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation) throws IOException, TransformException {

        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // folder to copy the files that were originally in folders.
        File outFolder = null;
        // jar to copy the files that came from jars.  We want copy files from jar into a jar to
        // avoid case sensitivity issue on a case insensitive file system.
        File outJar = null;

        if (invocation.isIncremental()) {
            throw new IllegalStateException("Incremental mode is not supported yet.");
        } else {
            outputProvider.deleteAll();

            // gather all the inputs.
            ListMultimap<String, MergeSource> sourceFileList = ArrayListMultimap.create();
            for (TransformInput input : invocation.getInputs()) {
                for (JarInput jarInput : input.getJarInputs()) {
                    gatherListFromJar(jarInput, sourceFileList);
                }

                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    gatherListFromFolder(directoryInput, sourceFileList);
                }
            }

            // at this point we have what we need, write the output.

            // we're recording all the files that must be merged.
            // this is a map of (archive path -> source folder/jar)
            ListMultimap<String, MergeSource> mergedFiles = ArrayListMultimap.create();

            // we're also going to record for each jar which files comes from it.
            ListMultimap<File, String> jarSources = ArrayListMultimap.create();

            for (String key : sourceFileList.keySet()) {
                PackagingFileAction packagingAction = packagingOptions.getAction(key);

                // first thing we do is check if it's excluded.
                if (packagingAction == PackagingFileAction.EXCLUDE) {
                    // skip, no need to do anything else.
                    continue;
                }

                List<MergeSource> contentSourceList = sourceFileList.get(key);

                MergeSource selectedContent;
                if (packagingAction == PackagingFileAction.MERGE){
                    // if merge is specified, project files have no precedence
                    selectedContent = null;
                } else {
                    // if there is only one content or if one of the source is PROJECT then it wins.
                    // This is similar behavior as the other merger (assets, res, manifest).
                    selectedContent = findUniqueOrProjectContent(contentSourceList);
                }

                // otherwise search for a selection
                if (selectedContent == null) {
                    if (packagingAction == PackagingFileAction.PICK_FIRST) {
                        // if pickFirst then just pick the first one.
                        selectedContent = contentSourceList.get(0);
                    } else if (packagingAction == PackagingFileAction.MERGE) {
                        // if it's selected for merging, we need to record this for later where
                        // we'll merge all the files we've found.
                        for (MergeSource content : contentSourceList) {
                            mergedFiles.put(key, content);
                        }
                    } else {
                        // finally if it's not excluded, then this is an error.
                        // collect the sources.
                        List<File> sources = contentSourceList.stream()
                                .map(mergeSource -> mergeSource.getSourceInput().getFile())
                                .collect(Collectors.toList());
                        throw new TransformException(new DuplicateFileException(key, sources));
                    }
                }

                // if a file was selected, write it here.
                if (selectedContent != null) {
                    QualifiedContent input = selectedContent.getSourceInput();
                    if (input instanceof JarInput) {
                        // or just record it for now if it's coming from a jar.
                        // This will allow to open these source jars just once to copy
                        // all their content out.
                        jarSources.put(input.getFile(), key);
                    } else {
                        if (outFolder == null) {
                            outFolder = outputProvider.getContentLocation(
                                    "main",
                                    getOutputTypes(), getScopes(),
                                    Format.DIRECTORY);
                            mkdirs(outFolder);
                        }
                        copyFromFolder(selectedContent, outFolder, key);
                    }
                }
            }

            // now copy all the non-merged files into the jar.
            JarMerger jarMerger = null;
            if (!jarSources.isEmpty()) {
                outJar = outputProvider.getContentLocation(
                        "main", getOutputTypes(), getScopes(), Format.JAR);
                mkdirs(outJar.getParentFile());
                jarMerger = copyIntoJar(jarSources, outJar);
            }

            // then handle the merged files.
            if (!mergedFiles.isEmpty()) {
                for (String key : mergedFiles.keySet()) {
                    List<MergeSource> mergeSources = mergedFiles.get(key);

                    // first check if we have a jar source
                    boolean hasJarSource = false;
                    for (MergeSource source : mergeSources) {
                        if (source.getSourceInput() instanceof JarInput) {
                            hasJarSource = true;
                            break;
                        }
                    }

                    // merge the content into a ByteArrayOutputStream.
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    for (MergeSource source : mergeSources) {
                        File sourceFile = source.getSourceInput().getFile();
                        final String sourcePath = source.getSourcePath();

                        if (sourceFile.isDirectory()) {
                            File actualFile = computeFile(sourceFile, sourcePath);
                            baos.write(Files.toByteArray(actualFile));
                        } else {
                            try (ZipFile zipFile = new ZipFile(sourceFile)) {
                                ByteStreams.copy(
                                        zipFile.getInputStream(zipFile.getEntry(sourcePath)), baos);
                            }
                        }

                        if (!endsWithUnixNewline(baos.toByteArray())) {
                            baos.write('\n');
                        }
                    }

                    if (hasJarSource) {
                        // if we haven't written into the outjar, create it.
                        if (outJar == null) {
                            outJar = outputProvider.getContentLocation(
                                    "main", getOutputTypes(), getScopes(), Format.JAR);
                            mkdirs(outJar.getParentFile());
                            jarMerger = new JarMerger(outJar);
                        }

                        jarMerger.addEntry(key, baos.toByteArray());
                    } else {
                        if (outFolder == null) {
                            outFolder = outputProvider.getContentLocation(
                                    "main",
                                    getOutputTypes(), getScopes(),
                                    Format.DIRECTORY);
                            mkdirs(outFolder);
                        }

                        File computedFile = computeFile(outFolder, key);
                        Files.createParentDirs(computedFile);
                        Files.write(baos.toByteArray(), computedFile);
                    }
                }
            }

            if (jarMerger != null) {
                jarMerger.close();
            }
        }
    }

    @Nullable
    private static MergeSource findUniqueOrProjectContent(
            @NonNull List<MergeSource> contentSourceList) {
        if (contentSourceList.size() == 1) {
            return contentSourceList.get(0);
        }

        for (MergeSource content : contentSourceList) {
            if (content.getSourceInput().getScopes().contains(Scope.PROJECT)) {
                return content;
            }
        }

        return null;
    }

    private static void copyFromFolder(
            @NonNull MergeSource fromFolder,
            @NonNull File toFolder,
            @NonNull String path)
            throws IOException {
        File from = computeFile(fromFolder.getSourceInput().getFile(), fromFolder.getSourcePath());
        File to = computeFile(toFolder, path);
        mkdirs(to.getParentFile());
        Files.copy(from, to);
    }

    /**
     * computes a file path from a root folder and a zip archive path.
     * @param rootFolder the root folder
     * @param path the archive path
     * @return the File
     */
    private static File computeFile(@NonNull File rootFolder, @NonNull String path) {
        path = path.replace('/', File.separatorChar);
        return new File(rootFolder, path);
    }

    private static class JarFilter implements ZipEntryFilter {
        private final Set<String> allowedPath = Sets.newHashSet();

        void resetList(@NonNull List<String> paths) {
            allowedPath.clear();
            allowedPath.addAll(paths);
        }

        @Override
        public boolean checkEntry(String archivePath) throws ZipAbortException {
            return allowedPath.contains(archivePath);
        }
    }

    private static JarMerger copyIntoJar(@NonNull ListMultimap<File, String> jarSources,
            @NonNull File outJar)
            throws IOException {
        JarMerger jarMerger = new JarMerger(outJar);

        JarFilter filter = new JarFilter();
        jarMerger.setFilter(filter);

        for (File jarFile : jarSources.keySet()) {
            // reset filter to allow the expected list of files for that particular jar file.
            filter.resetList(jarSources.get(jarFile));

            // copy the jar file
            jarMerger.addJar(jarFile);
        }

        return jarMerger;
    }

    private void gatherListFromJar(
            @NonNull JarInput jarInput,
            @NonNull ListMultimap<String, MergeSource> content) throws IOException {

        try (ZipFile zipFile = new ZipFile(jarInput.getFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                String path = entry.getName();
                if (skipEntry(entry, path)) {
                    continue;
                }

                content.put(path, MergeSource.of(jarInput, path));
            }

        }
    }

    private boolean skipEntry(
            @NonNull ZipEntry entry,
            @NonNull String path) {
        if (entry.isDirectory() ||
                JarFile.MANIFEST_NAME.equals(path) ||
                !validator.validateJarPath(path)) {
            return true;
        }

        // split the path into segments.
        String[] segments = path.split("/");

        // empty path? skip to next entry.
        if (segments.length == 0) {
            return true;
        }

        return !PackagingUtils.checkFileForApkPackaging(path, false /*allowClassFiles*/);
    }

    private void gatherListFromFolder(
            @NonNull DirectoryInput directoryInput,
            @NonNull ListMultimap<String, MergeSource> content) {
        boolean subProject = directoryInput.getScopes().containsAll(ImmutableList.of(Scope.SUB_PROJECTS));
        gatherListFromFolder(directoryInput.getFile(), "", directoryInput, subProject, content);
    }

    private void gatherListFromFolder(
            @NonNull File file,
            @NonNull String path,
            @NonNull DirectoryInput directoryInput,
            boolean isSubProject,
            @NonNull ListMultimap<String, MergeSource> content) {
        File[] children = file.listFiles(
                (childFile, childName) -> childFile.isDirectory() || !childName.endsWith(SdkConstants.DOT_CLASS));

        if (children != null) {
            for (File child : children) {
                String newPath = path.isEmpty() ? child.getName() : path + '/' + child.getName();
                if (child.isDirectory()) {
                    gatherListFromFolder(
                            child,
                            newPath,
                            directoryInput,
                            isSubProject,
                            content);
                } else if (child.isFile() && validator.validateFolderPath(newPath, isSubProject)) {
                    content.put(
                            validator.folderPathToKey(newPath, isSubProject),
                            new MergeSource(directoryInput, newPath));
                }
            }
        }
    }

    /**
     * Checks whether a byte array ends with a UNIX newline character.
     *
     * @param data the data
     * @return does it end with a UNIX newline character?
     */
    private static boolean endsWithUnixNewline(@NonNull byte[] data) {
        return data.length > 0 && data[data.length - 1] == '\n';
    }
}
