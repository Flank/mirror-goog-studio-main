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
package com.android.ide.common.gradle.model;

import static com.android.ide.common.gradle.model.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.InstantRun;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub;
import com.android.ide.common.repository.GradleVersion;
import com.android.testutils.Serialization;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAndroidArtifactImpl}. */
public class IdeAndroidArtifactImplTest {
    private ModelCache myModelCache;
    private GradleVersion myGradleVersion;
    private IdeDependenciesFactory myDependenciesFactory;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
        myGradleVersion = GradleVersion.parse("3.2");
        myDependenciesFactory = new IdeDependenciesFactory();
    }

    @Test
    public void serializable() {
        assertThat(IdeAndroidArtifactImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeAndroidArtifact artifact =
                new IdeAndroidArtifactImpl(
                        new AndroidArtifactStub(),
                        myModelCache,
                        myDependenciesFactory,
                        myGradleVersion);
        byte[] bytes = Serialization.serialize(artifact);
        Object o = Serialization.deserialize(bytes);
        assertEquals(artifact, o);
    }

    @Test
    public void model1_dot_5() {
        AndroidArtifactStub original =
                new AndroidArtifactStub() {
                    @Override
                    @NonNull
                    public InstantRun getInstantRun() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: AndroidArtifact.getInstantRun()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getCompileTaskName(),
                                getAssembleTaskName(),
                                getClassesFolder(),
                                getJavaResourcesFolder(),
                                getDependencies(),
                                getCompileDependencies(),
                                getDependencyGraphs(),
                                getIdeSetupTaskNames(),
                                getGeneratedSourceFolders(),
                                getVariantSourceProvider(),
                                getMultiFlavorSourceProvider(),
                                getOutputs(),
                                getApplicationId(),
                                getSourceGenTaskName(),
                                getGeneratedResourceFolders(),
                                getBuildConfigFields(),
                                getResValues(),
                                getSigningConfigName(),
                                getAbiFilters(),
                                getNativeLibraries(),
                                isSigned());
                    }
                };
        IdeAndroidArtifact artifact =
                new IdeAndroidArtifactImpl(
                        original, myModelCache, myDependenciesFactory, myGradleVersion);
        expectUnsupportedOperationException(artifact::getInstantRun);
    }

    @Test
    public void constructor() throws Throwable {
        AndroidArtifact original = new AndroidArtifactStub();
        IdeAndroidArtifactImpl copy =
                new IdeAndroidArtifactImpl(
                        original, myModelCache, myDependenciesFactory, myGradleVersion);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    // See http://b/64305584
    @Test
    public void withNpeInGetOutputs() throws Throwable {
        AndroidArtifact original =
                new AndroidArtifactStub() {
                    @Override
                    @NonNull
                    public Collection<AndroidArtifactOutput> getOutputs() {
                        throw new NullPointerException();
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getCompileTaskName(),
                                getAssembleTaskName(),
                                getClassesFolder(),
                                getJavaResourcesFolder(),
                                getDependencies(),
                                getCompileDependencies(),
                                getDependencyGraphs(),
                                getIdeSetupTaskNames(),
                                getGeneratedSourceFolders(),
                                getVariantSourceProvider(),
                                getMultiFlavorSourceProvider(),
                                getApplicationId(),
                                getSourceGenTaskName(),
                                getGeneratedResourceFolders(),
                                getBuildConfigFields(),
                                getResValues(),
                                getInstantRun(),
                                getSigningConfigName(),
                                getAbiFilters(),
                                getNativeLibraries(),
                                isSigned(),
                                getAdditionalRuntimeApks(),
                                getTestOptions(),
                                getInstrumentedTestTaskName());
                    }
                };
        IdeAndroidArtifactImpl copy =
                new IdeAndroidArtifactImpl(
                        original, myModelCache, myDependenciesFactory, myGradleVersion);
        assertThat(copy.getOutputs()).isEmpty();
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeAndroidArtifactImpl.class).withRedefinedSuperclass().verify();
    }
}
