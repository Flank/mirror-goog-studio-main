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
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.gradle.model.stubs.JavaLibraryStub;
import com.android.testutils.Serialization;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeJavaLibrary}. */
public class IdeJavaLibraryTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeJavaLibrary.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeJavaLibrary javaLibrary = new IdeJavaLibrary(createStub(), myModelCache);
        byte[] bytes = Serialization.serialize(javaLibrary);
        Object o = Serialization.deserialize(bytes);
        assertEquals(javaLibrary, o);
    }

    @Test
    public void constructor() throws Throwable {
        JavaLibrary original = createStub();
        IdeJavaLibrary copy = new IdeJavaLibrary(original, myModelCache);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @NonNull
    private static JavaLibrary createStub() {
        return new JavaLibraryStub(new File("jarFile"), Lists.newArrayList(new JavaLibraryStub()));
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeJavaLibrary.class)
                .withRedefinedSuperclass()
                .withIgnoredFields("hashCode")
                .verify();
    }
}
