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

import com.android.builder.model.JavaCompileOptions;
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub;
import com.android.testutils.Serialization;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeJavaCompileOptions}. */
public class IdeJavaCompileOptionsTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeJavaCompileOptions.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeJavaCompileOptions compileOptions =
                new IdeJavaCompileOptions(new JavaCompileOptionsStub());
        byte[] bytes = Serialization.serialize(compileOptions);
        Object o = Serialization.deserialize(bytes);
        assertEquals(compileOptions, o);
    }

    @Test
    public void constructor() throws Throwable {
        JavaCompileOptions original = new JavaCompileOptionsStub();
        IdeJavaCompileOptions copy = new IdeJavaCompileOptions(original);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeJavaCompileOptions.class).verify();
    }
}
