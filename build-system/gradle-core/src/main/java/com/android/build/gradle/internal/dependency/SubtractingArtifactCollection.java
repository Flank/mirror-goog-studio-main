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
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;

/**
 * Implementation of a {@link ArtifactCollection} on top of two collections, in order to do lazy
 * subtractions.
 *
 * <p>The main use case for this is building an ArtifactCollection that represents the runtime
 * dependencies of a test app, minus the runtime dependencies of the tested app (to avoid duplicated
 * classes during runtime).
 */
public class SubtractingArtifactCollection implements ArtifactCollection {

    @NonNull private final ArtifactCollection testArtifacts;
    @NonNull private final ArtifactCollection testedArtifacts;
    @NonNull private final FileCollection fileCollection;
    private Set<ResolvedArtifactResult> artifactResults = null;

    public SubtractingArtifactCollection(
            @NonNull ArtifactCollection testArtifacts,
            @NonNull ArtifactCollection testedArtifacts) {
        this.testArtifacts = testArtifacts;
        this.testedArtifacts = testedArtifacts;

        fileCollection =
                testArtifacts.getArtifactFiles().minus(testedArtifacts.getArtifactFiles());
    }

    @Override
    public FileCollection getArtifactFiles() {
        return fileCollection;
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
        if (artifactResults == null) {
            // build a set of componentIdentifier for the tested artifacts.
            Set<ComponentIdentifier> testedIds = Sets.newHashSet();
            for (ResolvedArtifactResult artifactResult : testedArtifacts.getArtifacts()) {
                testedIds.add(artifactResult.getId().getComponentIdentifier());
            }

            // build the final list from the main one, filtering our the tested IDs.
            artifactResults = Sets.newLinkedHashSet();
            for (ResolvedArtifactResult artifactResult : testArtifacts.getArtifacts()) {
                if (!testedIds.contains(artifactResult.getId().getComponentIdentifier())) {
                    artifactResults.add(artifactResult);
                }
            }
        }

        return artifactResults;
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
}
