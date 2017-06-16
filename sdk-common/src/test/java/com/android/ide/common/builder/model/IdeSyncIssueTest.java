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

import com.android.builder.model.SyncIssue;
import com.android.ide.common.builder.model.stubs.SyncIssueStub;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeSyncIssue}. */
public class IdeSyncIssueTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeSyncIssue.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeSyncIssue syncIssue = new IdeSyncIssue(new SyncIssueStub(), myModelCache);
        byte[] bytes = Serialization.serialize(syncIssue);
        Object o = Serialization.deserialize(bytes);
        assertEquals(syncIssue, o);
    }

    @Test
    public void constructor() throws Throwable {
        SyncIssue original = new SyncIssueStub();
        IdeSyncIssue copy = new IdeSyncIssue(original, myModelCache);
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeSyncIssue.class).verify();
    }
}
