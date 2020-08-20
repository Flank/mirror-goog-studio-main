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
import static com.android.testutils.Serialization.deserialize;
import static com.android.testutils.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.builder.model.LintOptions;
import com.android.ide.common.gradle.model.IdeLintOptions;
import com.android.ide.common.gradle.model.stubs.LintOptionsStub;
import com.android.ide.common.repository.GradleVersion;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeLintOptions}. */
public class IdeLintOptionsTest {
    private ModelCacheTesting myModelCache;
    private GradleVersion myModelVersion;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
        myModelVersion = GradleVersion.parse("2.4.0");
    }

    @Test
    public void serialization() throws Exception {
        IdeLintOptions lintOptions =
                myModelCache.lintOptionsFrom(new LintOptionsStub(), myModelVersion);
        byte[] bytes = serialize(lintOptions);
        Object o = deserialize(bytes);
        assertEquals(lintOptions, o);
    }

    @Test
    public void constructor() throws Throwable {
        LintOptions original = new LintOptionsStub();
        IdeLintOptions copy = myModelCache.lintOptionsFrom(original, myModelVersion);
        assertThat(copy.getBaselineFile()).isEqualTo(original.getBaselineFile());
        assertThat(copy.getLintConfig()).isEqualTo(original.getLintConfig());
        assertThat(copy.getSeverityOverrides()).isEqualTo(original.getSeverityOverrides());
        assertThat(copy.isCheckTestSources()).isEqualTo(original.isCheckTestSources());
        assertThat(copy.isCheckDependencies()).isEqualTo(original.isCheckDependencies());
    }
}
