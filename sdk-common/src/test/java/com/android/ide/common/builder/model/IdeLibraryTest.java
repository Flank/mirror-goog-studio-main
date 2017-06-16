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

import com.android.builder.model.Library;
import com.android.ide.common.builder.model.stubs.LibraryStub;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.junit.Test;

/** Tests for {@link IdeLibrary}. */
public class IdeLibraryTest {
    @Test
    public void constructor() throws Throwable {
        Library original = new LibraryStub();
        IdeLibrary copy = new IdeLibrary(original, new ModelCache()) {};
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void model1_dot_5() {
        Library original =
                new LibraryStub() {
                    @Override
                    public boolean isProvided() {
                        throw new UnsupportedMethodException(
                                "Unsupported method: AndroidLibrary.isProvided()");
                    }
                };
        IdeLibrary library = new IdeLibrary(original, new ModelCache()) {};
        IdeModelTestUtils.expectUnsupportedMethodException(library::isProvided);
    }

    @Test
    public void equalsAndHashCode() {
        IdeModelTestUtils.createEqualsVerifier(IdeLibrary.class)
                .withRedefinedSubclass(IdeAndroidLibrary.class)
                .verify();
        IdeModelTestUtils.createEqualsVerifier(IdeLibrary.class)
                .withRedefinedSubclass(IdeJavaLibrary.class)
                .verify();
    }
}
