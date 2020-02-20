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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ModelVerifierTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MetadataExtractor metadataExtractor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(metadataExtractor.getSubgraphCount()).thenReturn(1);
    }

    @Test(expected = ModelParsingException.class)
    public void testMultiGraphThrowException() throws ModelParsingException {
        when(metadataExtractor.getSubgraphCount()).thenReturn(2);
        ModelVerifier.verifyModel(metadataExtractor);
    }

    @Test(expected = ModelParsingException.class)
    public void testInvalidMetadataThrowException() throws ModelParsingException {
        when(metadataExtractor.getModelMetaData()).thenReturn(null);
        ModelVerifier.verifyModel(metadataExtractor);
    }
}
