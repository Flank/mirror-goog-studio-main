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

package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.file.FileCollection;

/**
 * Implementation of a {@link ArtifactCollection} on top of another ArtifactCollection and a {@link
 * FileCollection} that contains the tested artifact.
 *
 * <p>Getting the list of ResolvedArtifactResult on this ArtifactCollection will fail.
 */
public class ArtifactCollectionWithTestedArtifact implements ArtifactCollection {

    @NonNull private final ArtifactCollection testArtifacts;
    @NonNull private final FileCollection testedArtifact;
    @NonNull private final String projectPath;
    @NonNull private final FileCollection fileCollection;
    private Set<ResolvedArtifactResult> artifactResults = null;

    public ArtifactCollectionWithTestedArtifact(
            @NonNull ArtifactCollection testArtifacts,
            @NonNull FileCollection testedArtifact,
            @NonNull String projectPath) {
        this.testArtifacts = testArtifacts;
        this.testedArtifact = testedArtifact;
        this.projectPath = projectPath;

        fileCollection = testArtifacts.getArtifactFiles().plus(testedArtifact);
    }

    @Override
    public FileCollection getArtifactFiles() {
        return fileCollection;
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
        if (artifactResults == null) {
            artifactResults = Sets.newLinkedHashSet();

            artifactResults.addAll(computedTestedArtifact());
            artifactResults.addAll(testArtifacts.getArtifacts());
        }

        return artifactResults;
    }

    @Override
    public Collection<Throwable> getFailures() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public Iterator<ResolvedArtifactResult> iterator() {
        return getArtifacts().iterator();
    }

    @Override
    public void forEach(Consumer<? super ResolvedArtifactResult> action) {
        getArtifacts().forEach(action);
    }

    @Override
    public Spliterator<ResolvedArtifactResult> spliterator() {
        return getArtifacts().spliterator();
    }

    /** Returns the base underlying {@link ArtifactCollection} without the tested artifact. */
    @NonNull
    public ArtifactCollection getTestArtifacts() {
        return testArtifacts;
    }

    @NonNull
    private List<ResolvedArtifactResult> computedTestedArtifact() {
        Set<File> testedFiles = testedArtifact.getFiles();
        List<ResolvedArtifactResult> list = Lists.newArrayListWithCapacity(testedFiles.size());
        for (File file : testedFiles) {
            list.add(
                    new TestedResolvedArtifactResult(
                            file,
                            new TestedComponentArtifactIdentifier(
                                    new TestedComponentIdentifier(projectPath))));
        }

        return list;
    }

    private static final class TestedResolvedArtifactResult implements ResolvedArtifactResult {

        @NonNull private final File artifactFile;
        @NonNull private final TestedComponentArtifactIdentifier artifactId;

        private TestedResolvedArtifactResult(
                @NonNull File artifactFile, @NonNull TestedComponentArtifactIdentifier artifactId) {
            this.artifactFile = artifactFile;
            this.artifactId = artifactId;
        }

        @Override
        public File getFile() {
            return artifactFile;
        }

        @Override
        public ResolvedVariantResult getVariant() {
            throw new UnsupportedOperationException(
                    "Call to TestedResolvedArtifactResult.getVariant is not allowed");
        }

        @Override
        public ComponentArtifactIdentifier getId() {
            return artifactId;
        }

        @Override
        public Class<? extends Artifact> getType() {
            throw new UnsupportedOperationException(
                    "Call to TestedResolvedArtifactResult.getType is not allowed");
        }
    }

    private static final class TestedComponentArtifactIdentifier
            implements ComponentArtifactIdentifier {

        @NonNull private final TestedComponentIdentifier id;

        public TestedComponentArtifactIdentifier(@NonNull TestedComponentIdentifier id) {
            this.id = id;
        }

        @Override
        public ComponentIdentifier getComponentIdentifier() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return id.getDisplayName();
        }
    }

    public static final class TestedComponentIdentifier implements ComponentIdentifier {
        // this should be here to disambiguate between different component identifier
        private final String projectPath;

        public TestedComponentIdentifier(String projectPath) {
            this.projectPath = projectPath;
        }

        @Override
        public String getDisplayName() {
            return "__tested_artifact__:" + projectPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestedComponentIdentifier that = (TestedComponentIdentifier) o;
            return Objects.equals(projectPath, that.projectPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectPath);
        }
    }
}
