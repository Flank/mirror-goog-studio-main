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
import com.android.builder.model.NativeAndroidProject;
import com.android.ide.common.gradle.model.stubs.NativeAndroidProjectStub;
import com.android.testutils.Serialization;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeAndroidProjectImpl}. */
public class IdeNativeAndroidProjectImplTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeAndroidProjectImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeAndroidProject nativeAndroidProject =
                new IdeNativeAndroidProjectImpl(new NativeAndroidProjectStub(), myModelCache);
        byte[] bytes = Serialization.serialize(nativeAndroidProject);
        Object o = Serialization.deserialize(bytes);
        assertEquals(nativeAndroidProject, o);
    }

    @Test
    public void constructor() throws Throwable {
        NativeAndroidProject original = new NativeAndroidProjectStub();
        IdeNativeAndroidProjectImpl copy = new IdeNativeAndroidProjectImpl(original, myModelCache);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void getBuildSystemsWithExperimentalPlugin0dot7() {
        NativeAndroidProject original =
                new NativeAndroidProjectStub() {
                    @Override
                    @NonNull
                    public Collection<String> getBuildSystems() {
                        throw new UnsupportedOperationException("getBuildSystems");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getModelVersion(),
                                getName(),
                                getBuildFiles(),
                                getArtifacts(),
                                getToolChains(),
                                getSettings(),
                                getFileExtensions(),
                                getApiVersion());
                    }
                };
        IdeNativeAndroidProjectImpl copy = new IdeNativeAndroidProjectImpl(original, myModelCache);
        try {
            copy.getBuildSystems();
            Assert.fail("Expecting UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ignored
        }
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeNativeAndroidProjectImpl.class).verify();
    }
}
