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
import static org.junit.Assert.assertEquals;

import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl;
import com.android.ide.common.gradle.model.stubs.NativeSettingsStub;
import com.android.testutils.Serialization;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeSettingsImpl}. */
public class IdeNativeSettingsTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeSettingsImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeSettingsImpl nativeSettings =
                myModelCache.nativeSettingsFrom(new NativeSettingsStub());
        byte[] bytes = Serialization.serialize(nativeSettings);
        Object o = Serialization.deserialize(bytes);
        assertEquals(nativeSettings, o);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeNativeSettingsImpl.class).verify();
    }
}
