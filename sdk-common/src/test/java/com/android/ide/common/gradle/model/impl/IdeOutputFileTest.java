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

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.ide.common.gradle.model.stubs.OutputFileStub;
import com.google.common.collect.Lists;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeOutputFileImpl}. */
public class IdeOutputFileTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeOutputFileImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeOutputFileImpl outputFile = new IdeOutputFileImpl(createStub(), myModelCache);
        byte[] bytes = serialize(outputFile);
        Object o = deserialize(bytes);
        assertEquals(outputFile, o);
    }

    @Test
    public void constructor() throws Throwable {
        OutputFile original = createStub();
        IdeOutputFileImpl copy = new IdeOutputFileImpl(original, myModelCache);
        assertEqualsOrSimilar(original, copy);
    }

    @NonNull
    private static OutputFileStub createStub() {
        return new OutputFileStub(Lists.newArrayList(new OutputFileStub()));
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeOutputFileImpl.class);
    }

    @Test
    public void stackOverflowInToString() {
        OutputFileStub file = new OutputFileStub();
        file.addOutputFile(file);

        IdeOutputFileImpl outputFile = new IdeOutputFileImpl(file, myModelCache);
        String text = outputFile.toString();
        System.out.println(text);
        assertThat(text).contains("this");
    }
}
