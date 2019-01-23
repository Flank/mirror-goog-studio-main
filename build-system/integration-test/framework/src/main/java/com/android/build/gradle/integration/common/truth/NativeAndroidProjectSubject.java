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

package com.android.build.gradle.integration.common.truth;

import static com.android.build.gradle.internal.cxx.configure.ConstantsKt.CXX_DEFAULT_CONFIGURATION_SUBFOLDER;
import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A Truth subject for testing NativeAndroidProject
 */
public class NativeAndroidProjectSubject
        extends Subject<NativeAndroidProjectSubject, NativeAndroidProject> {

    public static Subject.Factory<NativeAndroidProjectSubject, NativeAndroidProject>
            nativeAndroidProjects() {
        return NativeAndroidProjectSubject::new;
    }

    private NativeAndroidProjectSubject(
            @NonNull FailureMetadata failureMetadata, @NonNull NativeAndroidProject subject) {
        super(failureMetadata, subject);
    }


    @NonNull
    public static NativeAndroidProjectSubject assertThat(@Nullable NativeAndroidProject project) {
        return assertAbout(nativeAndroidProjects()).that(project);
    }

    @NonNull
    private Multimap<String, NativeArtifact> getArtifactsGroupedByGroupName() {
        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();
        for (NativeArtifact artifact : actual().getArtifacts()) {
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }
        return groupToArtifacts;
    }

    @NonNull
    private Multimap<String, NativeArtifact> getArtifactsByName() {
        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();
        for (NativeArtifact artifact : actual().getArtifacts()) {
            groupToArtifacts.put(artifact.getName(), artifact);
        }
        return groupToArtifacts;
    }

    @NonNull
    private List<File> getOutputs() {
        List<File> outputs = Lists.newArrayList();
        for (NativeArtifact artifact : actual().getArtifacts()) {
            outputs.add(artifact.getOutputFile());
        }
        return outputs;
    }

    @NonNull
    private Set<File> getIntermediatesFolders(@NonNull  String baseFolder) {
        Set<File> intermediatesFolders = Sets.newHashSet();
        for (NativeArtifact artifact : actual().getArtifacts()) {
            File intermediatesBaseFolder = artifact.getOutputFile();
            File cxxFolder;
            do {
                if (intermediatesBaseFolder.getName().equals("project")) {
                    return intermediatesFolders;
                }
                intermediatesBaseFolder = intermediatesBaseFolder.getParentFile();
                cxxFolder = new File(intermediatesBaseFolder, baseFolder);
            } while (!cxxFolder.isDirectory());
            intermediatesFolders.add(cxxFolder);
        }
        return intermediatesFolders;
    }

    @NonNull
    private Set<Path> getIntermediates(String baseFolder) throws IOException {
        Set<File> intermediatesFolders = getIntermediatesFolders(baseFolder);

        Set<Path> intermediates = Sets.newHashSet();
        for (File intermediatesFolder : intermediatesFolders) {
            Path intermediatesPath = Paths.get(intermediatesFolder.getPath());
            try (Stream<Path> stream = Files.find(intermediatesPath, 12,
                    (path, attributes) -> attributes.isRegularFile())) {
                stream.forEach(it -> intermediates.add(it));
            }
        }
        return intermediates;
    }

    @NonNull
    private Set<String> getIntermediatesNames(String extension,
            String baseFolder) throws IOException {
        Set<Path> intermediates = getIntermediates(baseFolder);
        Set<String> names = Sets.newHashSet();
        for (Path intermediate : intermediates) {
            if (intermediate.getFileName().toString().endsWith(extension)) {
                names.add(intermediate.getFileName().toString());
            }
        }
        return names;
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    private void hasExactOutputFiles(
            String extension,
            String baseFolder,
            String... baseName) throws IOException {
        Set<String> intermediateNames = getIntermediatesNames(extension, baseFolder);
        Set<String> expected = Sets.newHashSet(baseName);
        Set<String> expectedNotFound = Sets.newHashSet();
        expectedNotFound.addAll(expected);
        expectedNotFound.removeAll(intermediateNames);
        if (!expectedNotFound.isEmpty()) {
            failWithRawMessage("Not true that %s build intermediates was %s. Set %s was missing %s",
                    getIntermediatesFolders(baseFolder),
                    expected,
                    intermediateNames,
                    expectedNotFound);
        }

        Set<String> foundNotExpected = Sets.newHashSet();
        foundNotExpected.addAll(intermediateNames);
        foundNotExpected.removeAll(expected);
        if (!foundNotExpected.isEmpty()) {
            failWithRawMessage("Not true that %s build intermediates was %s. It had extras %s",
                    getIntermediatesFolders(baseFolder),
                    expected,
                    foundNotExpected);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactObjectFilesInBuildFolder(String... baseName) throws IOException {
        hasExactOutputFiles(".o", "build", baseName);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactObjectFilesInCxxFolder(String... baseName) throws IOException {
        hasExactOutputFiles(".o", CXX_DEFAULT_CONFIGURATION_SUBFOLDER, baseName);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactSharedObjectFilesInBuildFolder(String... baseName) throws IOException {
        hasExactOutputFiles(".so", "build", baseName);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasBuildOutputCountEqualTo(int expectedCount) {
        List<File> buildOutputs = getOutputs();

        if (buildOutputs.size() != expectedCount) {
            failWithRawMessage(
                    "Not true that %s build output count was %s. It was %s",
                    actualAsString(), expectedCount, buildOutputs.size());
        }
    }

    public void allBuildOutputsExist() {
        List<File> exist = Lists.newArrayList();
        List<File> dontExist = Lists.newArrayList();
        for (File buildOutput : getOutputs()) {
            if (!buildOutput.isFile()) {
                dontExist.add(buildOutput);
            } else {
                exist.add(buildOutput);
            }
        }
        if (!dontExist.isEmpty()) {
            failWithRawMessage(
                    "Not true that %s build outputs <%s> exist. Existing build outputs are <%s>",
                    actualAsString(), dontExist, exist);
        }
    }

    public void noBuildOutputsExist() {
        List<File> exist = Lists.newArrayList();
        List<File> dontExist = Lists.newArrayList();
        for (File buildOutput : getOutputs()) {
            if (!buildOutput.isFile()) {
                dontExist.add(buildOutput);
            } else {
                exist.add(buildOutput);
            }
        }
        if (!exist.isEmpty()) {
            failWithRawMessage(
                    "Not true that %s build outputs <%s> don't exist. Nonexistent build outputs are <%s>",
                    actualAsString(), exist, dontExist);
        }
    }

    /**
     * Asseerts that the this project has build files of names shortNames and no others. This checks
     * just the file name and not the path.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactBuildFilesShortNames(String... shortNames) {
        Set<String> expected = Sets.newHashSet();
        for (File buildFile : actual().getBuildFiles()) {
            expected.add(buildFile.getName());
        }
        Set<String> actual = Sets.newHashSet(shortNames);
        if (!actual.equals(expected)) {
            failWithRawMessage(
                    "Not true that %s build files are <%s>. They are <%s>",
                    actualAsString(), expected, actual);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasArtifactGroupsNamed(String ...artifacts) {
        Set<String> expected = Sets.newHashSet(artifacts);
        Multimap<String, NativeArtifact> groups = getArtifactsGroupedByGroupName();
        if (!groups.keySet().equals(expected)) {
            failWithRawMessage(
                    "Not true that %s artifact groups are <%s>. They are <%s>",
                    actualAsString(), expected, groups.keySet());
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasTargetsNamed(String ...artifacts) {
        Set<String> expected = Sets.newHashSet(artifacts);
        Multimap<String, NativeArtifact> groups = getArtifactsByName();
        if (!groups.keySet().equals(expected)) {
            failWithRawMessage(
                    "Not true that %s that qualified targets are <%s>. They are <%s>",
                    actualAsString(), expected, groups.keySet());
        }
    }


    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasArtifactGroupsOfSize(long size) {
        Multimap<String, NativeArtifact> groups = getArtifactsGroupedByGroupName();
        for (String groupName : groups.keySet()) {
            if (groups.get(groupName).size() != size) {
                failWithRawMessage(
                        "Not true that %s artifact group %s has size %s. " + "Actual size is <%s>",
                        actualAsString(), groupName, size, groups.get(groupName).size());
            }
        }
    }
}