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

import com.android.annotations.NonNull;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.gradle.model.stubs.BaseArtifactStub;
import com.android.ide.common.repository.GradleVersion;
import java.io.File;
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
    public void constructor() throws Throwable {
        BaseArtifact original = new BaseArtifactStub();
        IdeBaseArtifactImpl copy =
                new IdeBaseArtifactImpl(
                        original,
                        new ModelCache(),
                        myDependenciesFactory,
                        GradleVersion.parse("2.3.0")) {};
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
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

        IdeBaseArtifactImpl artifact =
                new IdeBaseArtifactImpl(
                        original,
                        new ModelCache(),
                        myDependenciesFactory,
                        GradleVersion.parse("1.5.0")) {};
        expectUnsupportedOperationException(artifact::getCompileDependencies);
        expectUnsupportedOperationException(artifact::getDependencyGraphs);
        expectUnsupportedOperationException(artifact::getJavaResourcesFolder);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeBaseArtifactImpl.class)
                .withRedefinedSubclass(IdeAndroidArtifactImpl.class)
                .verify();
        createEqualsVerifier(IdeBaseArtifactImpl.class)
                .withRedefinedSubclass(IdeJavaArtifact.class)
                .verify();
    }
}
