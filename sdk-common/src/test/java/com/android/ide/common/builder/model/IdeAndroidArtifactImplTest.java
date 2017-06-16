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
package com.android.ide.common.builder.model;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.InstantRun;
import com.android.ide.common.builder.model.stubs.AndroidArtifactStub;
import com.android.ide.common.repository.GradleVersion;
import java.io.Serializable;
import java.util.Objects;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAndroidArtifactImpl}. */
public class IdeAndroidArtifactImplTest {
    private ModelCache myModelCache;
    private GradleVersion myGradleVersion;
    private IdeLevel2DependenciesFactory myDependenciesFactory;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
        myGradleVersion = GradleVersion.parse("3.2");
        myDependenciesFactory = new IdeLevel2DependenciesFactory();
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
                        throw new UnsupportedMethodException(
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
        IdeModelTestUtils.expectUnsupportedMethodException(artifact::getInstantRun);
    }

    @Test
    public void constructor() throws Throwable {
        AndroidArtifact original = new AndroidArtifactStub();
        IdeAndroidArtifactImpl copy =
                new IdeAndroidArtifactImpl(
                        original, myModelCache, myDependenciesFactory, myGradleVersion);
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeAndroidArtifactImpl.class)
                .withRedefinedSuperclass()
                .verify();
    }
}
