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
import static com.android.ide.common.gradle.model.impl.IdeVariantOutputImpl.copyFilters;

import com.android.build.VariantOutput;
import com.android.ide.common.gradle.model.stubs.VariantOutputStub;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;

/** Tests for {@link com.android.ide.common.gradle.model.impl.IdeVariantOutputImpl}. */
public class IdeVariantOutputTest {
    @Test
    public void constructor() throws Throwable {
        VariantOutput original = new VariantOutputStub();
        final ModelCache cache = new ModelCache();
        IdeVariantOutputImpl copy =
                new IdeVariantOutputImpl(
                        cache.copy(
                                original.getOutputs(),
                                outputFile -> new IdeOutputFileImpl(outputFile, cache)),
                        ModelCache.Companion.copyNewProperty(
                                () -> ImmutableList.copyOf(original.getFilterTypes()),
                                Collections.emptyList()),
                        copyFilters(original, cache),
                        ModelCache.Companion.copyNewProperty(
                                original::getMainOutputFile,
                                file -> new IdeOutputFileImpl(file, cache),
                                null),
                        ModelCache.Companion.copyNewProperty(original::getOutputType),
                        original.getVersionCode()) {};
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeVariantOutputImpl.class, "hashCode")
                .withRedefinedSubclass(IdeAndroidArtifactOutputImpl.class)
                .verify();
    }
}
