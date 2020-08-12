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

import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.createEqualsVerifier;

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
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeBaseArtifactImpl.class, "hashCode")
                .withRedefinedSubclass(IdeAndroidArtifactImpl.class)
                .verify();
        createEqualsVerifier(IdeBaseArtifactImpl.class, "hashCode")
                .withRedefinedSubclass(IdeJavaArtifactImpl.class)
                .verify();
    }
}
