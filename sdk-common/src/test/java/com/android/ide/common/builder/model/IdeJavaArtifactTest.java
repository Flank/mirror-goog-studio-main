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

import com.android.builder.model.JavaArtifact;
import com.android.ide.common.builder.model.stubs.JavaArtifactStub;
import com.android.ide.common.repository.GradleVersion;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeJavaArtifact}. */
public class IdeJavaArtifactTest {
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
        assertThat(IdeJavaArtifact.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeJavaArtifact javaArtifact =
                new IdeJavaArtifact(
                        new JavaArtifactStub(),
                        myModelCache,
                        myDependenciesFactory,
                        myGradleVersion);
        byte[] bytes = Serialization.serialize(javaArtifact);
        Object o = Serialization.deserialize(bytes);
        assertEquals(javaArtifact, o);
    }

    @Test
    public void constructor() throws Throwable {
        JavaArtifact original = new JavaArtifactStub();
        IdeJavaArtifact copy =
                new IdeJavaArtifact(original, myModelCache, myDependenciesFactory, myGradleVersion);
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeJavaArtifact.class)
                .withRedefinedSuperclass()
                .verify();
    }
}
