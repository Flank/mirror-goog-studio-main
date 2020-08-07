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

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.gradle.model.ClassFieldStub;
import com.android.ide.common.gradle.model.GradleModelConverterUtil;
import com.android.ide.common.gradle.model.IdeClassField;
import com.android.projectmodel.DynamicResourceValue;
import com.android.resources.ResourceType;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Tests for accessing {@link GradleModelConverterUtil} from Java.
 */
public class GradleModelConverterJavaTest {
    /**
     * Verifies that the result of classFieldsToDynamicResourceValues is immutable when accessed from java.
     */
    @Test
    public void testResultIsStillImmutableFromJava() {
        ModelCache modelCache = new ModelCache();
        IdeClassField foo1 =
                modelCache.classFieldFrom(
                        new ClassFieldStub(ResourceType.STRING.getName(), "foo1", "baz"));
        IdeClassField foo2 =
                modelCache.classFieldFrom(
                        new ClassFieldStub(ResourceType.STRING.getName(), "foo2", "baz"));
        IdeClassField foo3 =
                modelCache.classFieldFrom(
                        new ClassFieldStub(ResourceType.STRING.getName(), "foo3", "baz"));

        Map<String, IdeClassField> input = new HashMap<>();
        input.put("foo1", foo1);
        input.put("foo2", foo2);
        input.put("foo3", foo3);

        Map<String, DynamicResourceValue> output = GradleModelConverterUtil
                .classFieldsToDynamicResourceValues(input);

        boolean caughtException = false;
        try {
            output.put("blah", new DynamicResourceValue(ResourceType.STRING, "foo"));
        } catch (UnsupportedOperationException e) {
            caughtException = true;
        }

        assertThat(caughtException).isTrue();
    }
}
