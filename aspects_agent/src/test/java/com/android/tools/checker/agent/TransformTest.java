/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.checker.agent;

import static org.junit.Assert.assertArrayEquals;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.Objects;
import org.junit.Test;

public class TransformTest {
    @Test
    public void testAnnotationRead() throws IOException {
        String binaryTestClassName = TestClass.class.getCanonicalName().replace('.', '/');

        byte[] classInput =
                ByteStreams.toByteArray(
                        Objects.requireNonNull(
                                TransformTest.class
                                        .getClassLoader()
                                        .getResourceAsStream(binaryTestClassName + ".class")));
        String[] annotations =
                Transform.annotationFromByteBuffer(classInput).toArray(new String[1]);
        assertArrayEquals(new String[] {"com.android.tools.checker.BlockingTest"}, annotations);
    }
}
