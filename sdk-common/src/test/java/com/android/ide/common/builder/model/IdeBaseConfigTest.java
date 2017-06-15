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

import com.android.annotations.NonNull;
import com.android.builder.model.BaseConfig;
import com.android.ide.common.builder.model.stubs.BaseConfigStub;
import com.google.common.truth.Truth;
import java.util.Map;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.Test;

/** Tests for {@link IdeBaseConfig}. */
public class IdeBaseConfigTest {
    @Test
    public void constructor() throws Throwable {
        BaseConfig original = new BaseConfigStub();
        IdeBaseConfig copy = new IdeBaseConfig(original, new ModelCache()) {};
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void modelOlderThan3_0() {
        BaseConfigStub original =
                new BaseConfigStub() {
                    @Override
                    @NonNull
                    public Map<String, String> getFlavorSelections() {
                        throw new UnsupportedMethodException(
                                "Unsupported method: AndroidLibrary.getSymbolFile()");
                    }
                };
        IdeBaseConfig copy = new IdeBaseConfig(original, new ModelCache()) {};
        Truth.assertThat(copy.getFlavorSelections()).isEmpty();
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeBaseConfig.class)
                .withRedefinedSubclass(IdeBuildType.class)
                .verify();
        IdeModelTestUtils.createEqualsVerifier(IdeBaseConfig.class)
                .withRedefinedSubclass(IdeProductFlavor.class)
                .verify();
    }
}
