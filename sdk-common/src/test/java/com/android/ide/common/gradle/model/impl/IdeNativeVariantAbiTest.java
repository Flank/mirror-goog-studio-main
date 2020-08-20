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

import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;

import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl;
import com.android.ide.common.gradle.model.stubs.NativeVariantAbiStub;
import com.android.testutils.Serialization;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeVariantAbiImpl}. */
public class IdeNativeVariantAbiTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeVariantAbiImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeVariantAbiImpl nativeVariantAbi =
                myModelCache.nativeVariantAbiFrom(new NativeVariantAbiStub());
        byte[] bytes = Serialization.serialize(nativeVariantAbi);
        Object o = Serialization.deserialize(bytes);
        assertEquals(nativeVariantAbi, o);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeNativeVariantAbiImpl.class).verify();
    }
}
