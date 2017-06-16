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

import com.android.builder.model.NativeSettings;
import com.android.ide.common.builder.model.stubs.NativeSettingsStub;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeSettings}. */
public class IdeNativeSettingsTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeSettings.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeSettings nativeSettings =
                new IdeNativeSettings(new NativeSettingsStub(), myModelCache);
        byte[] bytes = Serialization.serialize(nativeSettings);
        Object o = Serialization.deserialize(bytes);
        assertEquals(nativeSettings, o);
    }

    @Test
    public void constructor() throws Throwable {
        NativeSettings original = new NativeSettingsStub();
        IdeNativeSettings copy = new IdeNativeSettings(original, myModelCache);
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeNativeSettings.class).verify();
    }
}
