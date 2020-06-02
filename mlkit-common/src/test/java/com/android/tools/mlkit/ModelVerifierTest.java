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

package com.android.tools.mlkit;

import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tensorflow.lite.support.metadata.MetadataExtractor;

public class ModelVerifierTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MetadataExtractor metadataExtractor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEmptyMetadataNotThrowException() throws TfliteModelException {
        when(metadataExtractor.getModelMetadata()).thenReturn(null);
        ModelVerifier.verifyModel(metadataExtractor);
    }

    @Test(expected = TfliteModelException.class)
    public void testInvalidModelThrowException() throws TfliteModelException {
        ModelVerifier.getExtractorWithVerification(ByteBuffer.wrap(new byte[] {1, 2, 4, 6}));
    }

    @Test(expected = TfliteModelException.class)
    public void testUnsupportedDataTypeThrowException() throws TfliteModelException {
        ModelVerifier.verifyDataType((byte) -1, 0, TensorInfo.Source.INPUT);
    }
}
