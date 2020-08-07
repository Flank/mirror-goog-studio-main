/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import static com.android.ide.common.gradle.model.impl.IdeBaseArtifactImpl.createSourceProvider;
import static com.android.ide.common.gradle.model.impl.IdeBaseArtifactImpl.getGeneratedSourceFolders;
import static com.android.ide.common.gradle.model.impl.IdeBaseArtifactImpl.getIdeSetupTaskNames;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.gradle.model.stubs.BaseArtifactStub;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeBaseArtifactImpl}. */
public class IdeBaseArtifactImplTest {
    private IdeDependenciesFactory myDependenciesFactory;

    @Before
    public void setup() {
        myDependenciesFactory = new IdeDependenciesFactory();
    }

    @Test
    public void addGeneratedSourceFolder() {
        BaseArtifact original = new BaseArtifactStub();
        final ModelCache cache = new ModelCache();
        IdeBaseArtifactImpl copy =
                new IdeBaseArtifactImpl(
                        original.getName(),
                        original.getCompileTaskName(),
                        original.getAssembleTaskName(),
                        ModelCache.Companion.copyNewProperty(
                                original::getAssembleTaskOutputListingFile, ""),
                        original.getClassesFolder(),
                        ModelCache.Companion.copyNewProperty(original::getJavaResourcesFolder),
                        ImmutableSet.copyOf(getIdeSetupTaskNames(original)),
                        new LinkedHashSet<File>(getGeneratedSourceFolders(original)),
                        createSourceProvider(cache, original.getVariantSourceProvider()),
                        createSourceProvider(cache, original.getMultiFlavorSourceProvider()),
                        ModelCache.Companion.copyNewProperty(
                                original::getAdditionalClassesFolders, Collections.emptySet()),
                        myDependenciesFactory.create(original)) {};
        File folder = new File("foo");
        copy.addGeneratedSourceFolder(folder);
        Collection<File> generatedSourceFolders = copy.getGeneratedSourceFolders();
        assertThat(generatedSourceFolders).contains(folder);
    }

    @Test
    public void model1_dot_5() {
        BaseArtifact original =
                new BaseArtifactStub() {
                    @Override
                    @NonNull
                    public Dependencies getCompileDependencies() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: BaseArtifact.getCompileDependencies()");
                    }

                    @Override
                    @NonNull
                    public DependencyGraphs getDependencyGraphs() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: BaseArtifact.getDependencyGraphs");
                    }

                    @Override
                    @NonNull
                    public File getJavaResourcesFolder() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: BaseArtifact.getJavaResourcesFolder");
                    }
                };

        final ModelCache cache = new ModelCache();
        IdeBaseArtifactImpl artifact =
                new IdeBaseArtifactImpl(
                        original.getName(),
                        original.getCompileTaskName(),
                        original.getAssembleTaskName(),
                        ModelCache.Companion.copyNewProperty(
                                original::getAssembleTaskOutputListingFile, ""),
                        original.getClassesFolder(),
                        ModelCache.Companion.copyNewProperty(original::getJavaResourcesFolder),
                        ImmutableSet.copyOf(getIdeSetupTaskNames(original)),
                        new LinkedHashSet<File>(getGeneratedSourceFolders(original)),
                        createSourceProvider(cache, original.getVariantSourceProvider()),
                        createSourceProvider(cache, original.getMultiFlavorSourceProvider()),
                        ModelCache.Companion.copyNewProperty(
                                original::getAdditionalClassesFolders, Collections.emptySet()),
                        myDependenciesFactory.create(original)) {};
        expectUnsupportedOperationException(artifact::getJavaResourcesFolder);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeBaseArtifactImpl.class, "hashCode")
                .withRedefinedSubclass(IdeAndroidArtifactImpl.class)
                .verify();
        createEqualsVerifier(IdeBaseArtifactImpl.class, "hashCode")
                .withRedefinedSubclass(IdeJavaArtifactImpl.class)
                .verify();
    }
}
