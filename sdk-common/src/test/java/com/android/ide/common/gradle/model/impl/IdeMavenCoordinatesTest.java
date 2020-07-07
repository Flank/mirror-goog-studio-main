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

import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.assertEqualsOrSimilar;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.createEqualsVerifier;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.verifyUsageOfImmutableCollections;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub;
import com.android.testutils.Serialization;
import java.io.Serializable;
import org.junit.Test;

/** Tests for {@link IdeMavenCoordinatesImpl}. */
public class IdeMavenCoordinatesTest {
    @Test
    public void serializable() {
        assertThat(IdeMavenCoordinatesImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeMavenCoordinatesImpl coordinates =
                ModelCache.Companion.mavenCoordinatesFrom(new MavenCoordinatesStub());
        byte[] bytes = Serialization.serialize(coordinates);
        Object o = Serialization.deserialize(bytes);
        assertEquals(coordinates, o);
    }

    @Test
    public void constructor() throws Throwable {
        MavenCoordinates original = new MavenCoordinatesStub();
        IdeMavenCoordinatesImpl copy = ModelCache.Companion.mavenCoordinatesFrom(original);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeMavenCoordinatesImpl.class).verify();
    }
}