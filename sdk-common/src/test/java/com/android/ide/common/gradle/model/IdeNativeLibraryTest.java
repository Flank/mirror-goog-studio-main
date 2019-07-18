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
import static com.android.testutils.Serialization.deserialize;
import static com.android.testutils.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.builder.model.NativeLibrary;
import com.android.ide.common.gradle.model.stubs.NativeLibraryStub;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeLibrary}. */
public class IdeNativeLibraryTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeLibrary.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeLibrary nativeLibrary = new IdeNativeLibrary(new NativeLibraryStub());
        byte[] bytes = serialize(nativeLibrary);
        Object o = deserialize(bytes);
        assertEquals(nativeLibrary, o);
    }

    @Test
    public void constructor() throws Throwable {
        NativeLibrary original = new NativeLibraryStub();
        IdeNativeLibrary copy = new IdeNativeLibrary(original);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeNativeLibrary.class).verify();
    }
}
