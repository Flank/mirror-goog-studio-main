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

package com.android.build.gradle.internal.pipeline;

import static com.android.SdkConstants.DOT_JAR;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.gradle.internal.InternalScope;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.InputFileDetails;

/**
 * Helper to handle the folder structure in the output of transforms.
 */
public class IntermediateFolderUtils {

    public static final String FOLDERS = "folders";
    public static final String JARS = "jars";

    /**
     * Returns the location of content for a given set of Scopes, Content Types, and format.
     *
     * If the format is {@link Format#DIRECTORY} then the result is the file location of the folder.
     * If the format is {@link Format#JAR} then the result is a file representing the jar to create.
     *
     * @param rootLocation the root location from which to create the content.
     * @param name a unique name for the content. For a given set of scopes/types/format it must
     *             be unique.
     * @param types the content types associated with this content.
     * @param scopes the scopes associated with this content.
     * @param format the format of the content.
     * @return the location of the content.
     */
    @NonNull
    public static File getContentLocation(
            @NonNull File rootLocation,
            @NonNull String name,
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes,
            @NonNull Format format) {
        // runtime check these since it's (indirectly) called by 3rd party transforms.
        checkNotNull(name);
        checkNotNull(types);
        checkNotNull(scopes);
        checkNotNull(format);
        checkState(!name.isEmpty());
        checkState(!types.isEmpty());
        checkState(!scopes.isEmpty());

        switch (format) {
            case DIRECTORY: {
                File location = FileUtils.join(rootLocation,
                        FOLDERS,
                        typesToString(types),
                        scopesToString(scopes),
                        name);
                return location;
            }
            case JAR: {
                File location = FileUtils.join(rootLocation,
                        JARS,
                        typesToString(types),
                        scopesToString(scopes),
                        name + DOT_JAR);
                return location;
            }
            default:
                throw new RuntimeException("Unexpected Format: " + format);
        }
    }

    @NonNull
    public static TransformInput computeNonIncrementalInputFromFolder(
            @NonNull File folder,
            @NonNull Set<ContentType> requiredTypes,
            @NonNull Set<? super Scope> requiredScopes) {
        final List<JarInput> jarInputs = Lists.newArrayList();
        final List<DirectoryInput> directoryInputs = Lists.newArrayList();

        File jarsFolder = new File(folder, JARS);
        if (jarsFolder.isDirectory()) {
            parseTypeLevelFolders(
                    jarsFolder,
                    requiredTypes,
                    requiredScopes,
                    new InputGenerator() {
                        @Override
                        public boolean accept(@NonNull File file) {
                            return file.isFile() && file.getName().endsWith(DOT_JAR);
                        }

                        @Override
                        public void generate(
                                @NonNull File file,
                                @NonNull Set<ContentType> types,
                                @NonNull Set<QualifiedContent.ScopeType> scopes) {
                            jarInputs.add(new ImmutableJarInput(
                                    file.getName().substring(0,
                                            file.getName().length() - DOT_JAR.length()),
                                    file,
                                    Status.NOTCHANGED,
                                    types,
                                    scopes));

                        }
                    });
        }

        File foldersFolder = new File(folder, FOLDERS);
        if (foldersFolder.isDirectory()) {
            parseTypeLevelFolders(
                    foldersFolder,
                    requiredTypes,
                    requiredScopes,
                    new InputGenerator() {
                        @Override
                        public boolean accept(@NonNull File file) {
                            return file.isDirectory();
                        }

                        @Override
                        public void generate(@NonNull File file, @NonNull Set<ContentType> types,
                                @NonNull Set<QualifiedContent.ScopeType> scopes) {
                            directoryInputs.add(new ImmutableDirectoryInput(
                                    file.getName(),
                                    file,
                                    types,
                                    scopes));
                        }
                    });
        }

        return new ImmutableTransformInput(jarInputs, directoryInputs, folder);
    }

    static class IntermediateTransformInput extends IncrementalTransformInput {

        @NonNull
        private final File inputRoot;
        private List<String> rootLocationSegments = null;

        IntermediateTransformInput(@NonNull File inputRoot) {
            this.inputRoot = inputRoot;
        }

        @Override
        protected boolean checkRemovedFolder(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            if (!checkRootSegments(fileSegments)) {
                return false;
            }

            // there must be at least 5 additional segments (4 to the root of the folder and 1 for
            // the file inside.
            if (fileSegments.size() <= rootLocationSegments.size() + 4) {
                return false;
            }

            // now check that the segments after the root are what we expect.
            int index = rootLocationSegments.size();
            if (!fileSegments.get(index++).equals(FOLDERS)) {
                return false;
            }

            // get the types.
            Set<ContentType> types = stringToTypes(fileSegments.get(index++));
            if (types == null) {
                return false;
            }

            // if the transform is not interested in this folder type, we don't care about those
            // changes
            if (Sets.intersection(transformInputTypes, types).isEmpty()) {
                return true;
            }

            // get the scopes.
            Set<QualifiedContent.ScopeType> scopes = stringToScopes(fileSegments.get(index++));
            if (scopes == null) {
                return false;
            }

            // if the scopes do not match the transform scope, we don't care about those changes.
            if (Sets.intersection(transformScopes, scopes).isEmpty()) {
                return true;
            }

            String name = fileSegments.get(index);

            // create the folder input. a mutable one so that it can be directly used
            // for other removed files from the same folder.
            // The root location of this folder is fileSegments, up to rootLocation + 4 (as the
            // rest is the changed file.
            File root = new File(FileUtils.join(
                    fileSegments.subList(0, rootLocationSegments.size() + 4)));
            MutableDirectoryInput folder = new MutableDirectoryInput(name, root, types, scopes);
            // add this file to it.
            Logging.getLogger(TransformManager.class).info(
                    "Tagged" + file.getAbsolutePath() + " as removed");
            folder.addChangedFile(file, Status.REMOVED);

            // add it to the list.
            addFolderInput(folder);

            return true;
        }

        @Override
        boolean checkRemovedJarFile(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            if (!checkRootSegments(fileSegments)) {
                return false;
            }

            // there must be only 4 additional segments.
            if (fileSegments.size() != rootLocationSegments.size() + 4) {
                return false;
            }

            // last segment must end in .jar
            if (!file.getPath().endsWith(DOT_JAR)) {
                return false;
            }

            // now check that the segments after the root are what we expect.
            int index = rootLocationSegments.size();
            if (!fileSegments.get(index++).equals(JARS)) {
                return false;
            }

            // get the types.
            Set<ContentType> types = stringToTypes(fileSegments.get(index++));
            if (types == null) {
                return false;
            }
            // if the transform is not interested in this jar type, we don't care about those
            // changes
            if (Sets.intersection(transformInputTypes, types).isEmpty()) {
                return true;
            }

            // get the scopes.
            Set<QualifiedContent.ScopeType> scopes = stringToScopes(fileSegments.get(index++));
            if (scopes == null) {
                return false;
            }

            // if the scopes do not match the transform scope, we don't care about those changes.
            if (Sets.intersection(transformScopes, scopes).isEmpty()) {
                return true;
            }

            String name = fileSegments.get(index);

            // create the jar input
            addImmutableJar(new ImmutableJarInput(name, file, Status.REMOVED, types, scopes));

            return true;
        }

        private boolean checkRootSegments(@NonNull List<String> fileSegments) {
            if (rootLocationSegments == null) {
                rootLocationSegments = Lists.newArrayList(
                        Splitter.on(File.separatorChar).split(inputRoot.getAbsolutePath()));
            }

            if (fileSegments.size() <= rootLocationSegments.size()) {
                return false;
            }

            // compare segments going backward as the leafs are more likely to be different.
            // We can ignore the segments of the file that are beyond the segments for the folder.
            for (int i = rootLocationSegments.size() - 1 ; i >= 0 ; i--) {
                if (!rootLocationSegments.get(i).equals(fileSegments.get(i))) {
                    return false;
                }
            }

            return true;
        }

    }

    @NonNull
    public static IncrementalTransformInput computeIncrementalInputFromFolder(
            @NonNull File rootLocation,
            @NonNull Set<ContentType> requiredTypes,
            @NonNull Set<? super Scope> requiredScopes) {
        final IncrementalTransformInput input = new IntermediateTransformInput(rootLocation);

        File jarsFolder = new File(rootLocation, JARS);
        if (jarsFolder.isDirectory()) {
            parseTypeLevelFolders(
                    jarsFolder,
                    requiredTypes,
                    requiredScopes,
                    new InputGenerator() {
                        @Override
                        public boolean accept(@NonNull File file) {
                            return file.isFile() && file.getName().endsWith(DOT_JAR);
                        }

                        @Override
                        public void generate(
                                @NonNull File file,
                                @NonNull Set<ContentType> types,
                                @NonNull Set<QualifiedContent.ScopeType> scopes) {
                            input.addJarInput(new QualifiedContentImpl(
                                    file.getName().substring(0,
                                            file.getName().length() - DOT_JAR.length()),
                                    file,
                                    types,
                                    scopes) {
                            });

                        }
                    });
        }

        File foldersFolder = new File(rootLocation, FOLDERS);
        if (foldersFolder.isDirectory()) {
            parseTypeLevelFolders(
                    foldersFolder,
                    requiredTypes,
                    requiredScopes,
                    new InputGenerator() {
                        @Override
                        public boolean accept(@NonNull File file) {
                            return file.isDirectory();
                        }

                        @Override
                        public void generate(@NonNull File file, @NonNull Set<ContentType> types,
                                @NonNull Set<QualifiedContent.ScopeType> scopes) {
                            input.addFolderInput(new MutableDirectoryInput(
                                    file.getName(),
                                    file,
                                    types,
                                    scopes));
                        }
                    });
        }

        return input;
    }

    private interface InputGenerator {
        boolean accept(@NonNull File file);
        void generate(
                @NonNull File file,
                @NonNull Set<ContentType> types,
                @NonNull Set<QualifiedContent.ScopeType> scopes);
    }

    private static void parseTypeLevelFolders(
            @NonNull File rootFolder,
            @NonNull Set<ContentType> requiredTypes,
            @NonNull Set<? super Scope> requiredScopes,
            @NonNull InputGenerator generator) {
        File[] files = rootFolder.listFiles(File::isDirectory);

        if (files != null && files.length > 0) {
            for (File file : files) {
                Set<ContentType> types = stringToTypes(file.getName());
                if (types != null) {
                    // check these are types we care about and only pass down types we care about.
                    // In this case we can safely return the content with a limited type,
                    // as file extension allows for differentiation.
                    Set<ContentType> limitedTypes = Sets.intersection(requiredTypes, types);
                    if (!limitedTypes.isEmpty()) {
                        parseScopeLevelFolders(file, limitedTypes, requiredScopes, generator);
                    }
                }
            }
        }
    }

    private static void parseScopeLevelFolders(
            @NonNull File rootFolder,
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> requiredScopes,
            @NonNull InputGenerator generator) {
        File[] files = rootFolder.listFiles(File::isDirectory);

        if (files != null && files.length > 0) {
            for (File file : files) {
                Set<QualifiedContent.ScopeType> scopes = stringToScopes(file.getName());
                if (scopes != null) {
                    // we need up to the requiredScopes, but no more.
                    // content that only contains unwanted Scope can be safely dropped, however
                    // content that is both in and out of Scope will trigger a runtime error.
                    // check these are the scope we want, and only pass down scopes we care about.
                    Set<QualifiedContent.ScopeType> limitedScopes = Sets.newHashSetWithExpectedSize(requiredScopes.size());
                    boolean foundUnwanted = false;
                    for (QualifiedContent.ScopeType scope : scopes) {
                        if (requiredScopes.contains(scope)) {
                            limitedScopes.add(scope);
                        } else {
                            foundUnwanted = true;
                        }
                    }
                    if (!limitedScopes.isEmpty()) {
                        if (foundUnwanted) {
                            throw new RuntimeException(
                                    "Unexpected scopes found. Required: "
                                            + Joiner.on(", ").join(requiredScopes)
                                            + ". Found: "
                                            + Joiner.on(", ").join(scopes));
                        }
                        parseContentLevelFolders(file, types, limitedScopes,
                                generator);
                    }
                }
            }
        }
    }

    private static void parseContentLevelFolders(
            @NonNull File rootFolder,
            @NonNull Set<ContentType> types,
            @NonNull Set<QualifiedContent.ScopeType> scopes,
            @NonNull final InputGenerator generator) {

        File[] files = rootFolder.listFiles(generator::accept);

        if (files != null && files.length > 0) {
            for (File file : files) {
                generator.generate(file, types, scopes);
            }
        }
    }

    @Nullable
    private static Set<ContentType> stringToTypes(String folderName) {
        int value;
        try {
            value = Integer.parseInt(folderName, 16);
        } catch (NumberFormatException e) {
            return null;
        }

        ImmutableSet.Builder<ContentType> typesBuilder = ImmutableSet.builder();

        for (ContentType type : ExtendedContentType.getAllContentTypes()) {
            if ((type.getValue() & value) != 0) {
                typesBuilder.add(type);
            }
        }

        Set<ContentType> types = typesBuilder.build();
        if (types.isEmpty()) {
            return null;
        }

        return types;
    }

    private static String typesToString(@NonNull Set<ContentType> types) {
        int value = 0;
        for (ContentType type : types) {
            value += type.getValue();
        }

        return String.format("%x", value);
    }

    @Nullable
    private static Set<QualifiedContent.ScopeType> stringToScopes(String folderName) {
        int value;
        try {
            value = Integer.parseInt(folderName, 16);
        } catch (NumberFormatException e) {
            return null;
        }

        ImmutableSet.Builder<QualifiedContent.ScopeType> scopesBuilder = ImmutableSet.builder();

        for (QualifiedContent.ScopeType scope : allScopes()) {
            if ((scope.getValue() & value) != 0) {
                scopesBuilder.add(scope);
            }
        }
        ImmutableSet<QualifiedContent.ScopeType> scopes = scopesBuilder.build();

        if (scopes.isEmpty()) {
            return null;
        }

        return scopes;
    }

    private static List<QualifiedContent.ScopeType> allScopes() {
        ImmutableList.Builder<QualifiedContent.ScopeType> scopeTypes = ImmutableList.builder();
        for (QualifiedContent.ScopeType scopeType : Scope.values()) {
            scopeTypes.add(scopeType);
        }
        for (QualifiedContent.ScopeType scopeType : InternalScope.values()) {
            scopeTypes.add(scopeType);
        }
        return scopeTypes.build();
    }

    private static String scopesToString(@NonNull Set<? super Scope> scopes) {
        int value = 0;
        for (QualifiedContent.ScopeType scope : (Set<QualifiedContent.ScopeType>) scopes) {
            value += scope.getValue();
        }

        return String.format("%x", value);
    }

    @NonNull
    static Status inputFileDetailsToStatus(@NonNull InputFileDetails inputFileDetails) {
        if (inputFileDetails.isAdded()) return Status.ADDED;
        if (inputFileDetails.isModified()) return Status.CHANGED;
        if (inputFileDetails.isRemoved()) return Status.REMOVED;
        return Status.NOTCHANGED;
    }
}
