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

import org.junit.Test;

/** Tests for {@link IdeBaseConfigImpl}. */
public class IdeBaseConfigTest {
    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeBaseConfigImpl.class, "hashCode")
                .withRedefinedSubclass(IdeBuildTypeImpl.class)
                .verify();
        createEqualsVerifier(IdeBaseConfigImpl.class, "hashCode")
                .withRedefinedSubclass(IdeProductFlavorImpl.class)
                .verify();
    }
}
