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

import com.android.annotations.NonNull;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.builder.model.stubs.BaseArtifactStub;
import com.android.ide.common.repository.GradleVersion;
import java.io.File;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeBaseArtifactImpl}. */
public class IdeBaseArtifactImplTest {
    private IdeLevel2DependenciesFactory myDependenciesFactory;

    @Before
    public void setup() {
        myDependenciesFactory = new IdeLevel2DependenciesFactory();
    }

    @Test
    public void constructor() throws Throwable {
        BaseArtifact original = new BaseArtifactStub();
        IdeBaseArtifactImpl copy =
                new IdeBaseArtifactImpl(
                        original,
                        new ModelCache(),
                        myDependenciesFactory,
                        GradleVersion.parse("2.3.0")) {};
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void model1_dot_5() {
        BaseArtifact original =
                new BaseArtifactStub() {
                    @Override
                    @NonNull
                    public Dependencies getCompileDependencies() {
                        throw new UnsupportedMethodException(
                                "Unsupported method: BaseArtifact.getCompileDependencies()");
                    }

                    @Override
                    @NonNull
                    public DependencyGraphs getDependencyGraphs() {
                        throw new UnsupportedMethodException(
                                "Unsupported method: BaseArtifact.getDependencyGraphs");
                    }

                    @Override
                    @NonNull
                    public File getJavaResourcesFolder() {
                        throw new UnsupportedMethodException(
                                "Unsupported method: BaseArtifact.getJavaResourcesFolder");
                    }
                };

        IdeBaseArtifactImpl artifact =
                new IdeBaseArtifactImpl(
                        original,
                        new ModelCache(),
                        myDependenciesFactory,
                        GradleVersion.parse("1.5.0")) {};
        IdeModelTestUtils.expectUnsupportedMethodException(artifact::getCompileDependencies);
        IdeModelTestUtils.expectUnsupportedMethodException(artifact::getDependencyGraphs);
        IdeModelTestUtils.expectUnsupportedMethodException(artifact::getJavaResourcesFolder);
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeBaseArtifactImpl.class)
                .withRedefinedSubclass(IdeAndroidArtifactImpl.class)
                .verify();
        IdeModelTestUtils.createEqualsVerifier(IdeBaseArtifactImpl.class)
                .withRedefinedSubclass(IdeJavaArtifact.class)
                .verify();
    }
}
