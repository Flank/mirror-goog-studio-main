/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.ide.common.gradle.model.IdeModelTestUtils.assertEqualsOrSimilar;
import static com.android.ide.common.gradle.model.IdeModelTestUtils.createEqualsVerifier;
import static com.android.ide.common.gradle.model.IdeModelTestUtils.verifyUsageOfImmutableCollections;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAaptOptions}. */
public class IdeAaptOptionsTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeAaptOptions.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeAaptOptions aaptOptions = new IdeAaptOptions(new AaptOptionsStub(), myModelCache);
        byte[] bytes = Serialization.serialize(aaptOptions);
        Object o = Serialization.deserialize(bytes);
        assertEquals(aaptOptions, o);
    }

    @Test
    public void constructor() throws Throwable {
        AaptOptions original = new AaptOptionsStub();
        IdeAaptOptions copy = new IdeAaptOptions(original, myModelCache);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeClassField.class).verify();
    }
}
