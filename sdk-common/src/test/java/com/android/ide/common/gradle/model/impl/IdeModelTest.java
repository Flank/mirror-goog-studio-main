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

import static com.android.ide.common.gradle.model.impl.ModelCache.copyNewProperty;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.android.annotations.NonNull;
import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link com.android.ide.common.gradle.model.impl.IdeModel}. */
public class IdeModelTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void testCopyNewProperty() {
        String copy = copyNewProperty(() -> "Hello", "Hi!");
        assertEquals("Hello", copy);

        copy =
                copyNewProperty(
                        () -> {
                            throw new UnsupportedOperationException("Test");
                        },
                        "Hi!");
        assertEquals("Hi!", copy);
    }

    @Test
    public void copyStringSet() {
        Set<String> original = Sets.newHashSet("1", "2", "3");
        Set<String> copy = ModelCache.copy(original);
        assertThat(copy).isNotSameAs(original);
        assertEquals(original, copy);
    }

    // https://code.google.com/p/android/issues/detail?id=360245
    @Test
    public void withRecursiveModel() {
        try {
            RecursiveModel original = new OriginalRecursiveModel();
            IdeRecursiveModel copy = new IdeRecursiveModel(original, myModelCache);
            assertSame(copy, copy.getRecursiveModel());
            Assert.fail();
        } catch (IllegalStateException T) {
        }
    }

    private interface RecursiveModel {
        @NonNull
        RecursiveModel getRecursiveModel();
    }

    private static class OriginalRecursiveModel implements RecursiveModel {
        @Override
        @NonNull
        public RecursiveModel getRecursiveModel() {
            return this;
        }
    }

    private static class IdeRecursiveModel implements RecursiveModel, Serializable {
        @NonNull private final RecursiveModel myRecursiveModel;

        IdeRecursiveModel(@NonNull RecursiveModel recursiveModel, @NonNull ModelCache modelCache) {
            //noinspection Convert2Lambda
            myRecursiveModel =
                    modelCache.computeIfAbsent(
                            recursiveModel.getRecursiveModel(),
                            new Function<RecursiveModel, RecursiveModel>() {
                                @Override
                                public RecursiveModel apply(RecursiveModel recursiveModel) {
                                    return new IdeRecursiveModel(recursiveModel, modelCache);
                                }
                            });
        }

        @Override
        @NonNull
        public RecursiveModel getRecursiveModel() {
            return myRecursiveModel;
        }
    }
}
