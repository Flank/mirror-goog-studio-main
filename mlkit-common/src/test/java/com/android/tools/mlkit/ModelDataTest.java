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

import static org.junit.Assert.assertEquals;

import com.android.testutils.TestUtils;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;

public class ModelDataTest {
    private static final double DELTA = 10e-6;

    private MetadataExtractor metadataExtractor;

    @Before
    public void setUp() throws IOException {
        File modelFile =
                TestUtils.getWorkspaceFile("prebuilts/tools/common/mlkit/testData/model.tflite");
        RandomAccessFile f = new RandomAccessFile(modelFile, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        f.close();
        metadataExtractor = new MetadataExtractor(ByteBuffer.wrap(data));
    }

    @Test
    public void testModelExtractedCorrectly() {
        ModelData modelData = ModelData.buildFrom(metadataExtractor);

        assertEquals(modelData.getInputs().size(), 1);
        assertEquals(modelData.getOutputs().size(), 1);

        Param inputParam = modelData.getInputs().get(0);
        assertEquals(inputParam.getContentType(), Param.ContentType.IMAGE);
        assertEquals(inputParam.getName(), "image1");
        MetadataExtractor.NormalizationParams inputNormalization =
                inputParam.getNormalizationParams();
        assertEquals(inputNormalization.getMean()[0], 0, DELTA);
        assertEquals(inputNormalization.getStd()[0], 1, DELTA);
        MetadataExtractor.QuantizationParams inputQuantization = inputParam.getQuantizationParams();
        assertEquals(inputQuantization.getZeroPoint(), 128f, DELTA);
        assertEquals(inputQuantization.getScale(), 0.0078125f, DELTA);

        Param outputParam = modelData.getOutputs().get(0);
        assertEquals(outputParam.getName(), "probability");
        assertEquals(outputParam.getFileType(), Param.FileType.TENSOR_AXIS_LABELS);
        MetadataExtractor.QuantizationParams outputQuantization =
                outputParam.getQuantizationParams();
        assertEquals(outputQuantization.getZeroPoint(), 0, DELTA);
        assertEquals(outputQuantization.getScale(), 0.00390625, DELTA);
    }
}
