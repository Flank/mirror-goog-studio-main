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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.mlkit.exception.TfliteModelException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import org.junit.Test;

public class ModelInfoTest {
    private static final double DELTA = 10e-6;

    @Test
    public void testQuantMetadataModelExtractedCorrectly()
            throws TfliteModelException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        extractByteBufferFromModel(
                                "prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite"));

        assertEquals(1, modelInfo.getInputs().size());
        assertEquals(1, modelInfo.getOutputs().size());
        assertTrue(modelInfo.isMetadataExisted());

        TensorInfo inputTensorInfo = modelInfo.getInputs().get(0);
        assertEquals(TensorInfo.ContentType.IMAGE, inputTensorInfo.getContentType());
        assertEquals("image1", inputTensorInfo.getIdentifierName());
        assertEquals(
                TensorInfo.ImageProperties.ColorSpaceType.RGB,
                inputTensorInfo.getImageProperties().colorSpaceType);
        MetadataExtractor.NormalizationParams inputNormalization =
                inputTensorInfo.getNormalizationParams();
        assertEquals(127.5f, inputNormalization.getMean()[0], DELTA);
        assertEquals(127.5f, inputNormalization.getStd()[0], DELTA);
        assertEquals(0f, inputNormalization.getMin()[0], DELTA);
        assertEquals(255f, inputNormalization.getMax()[0], DELTA);
        MetadataExtractor.QuantizationParams inputQuantization =
                inputTensorInfo.getQuantizationParams();
        assertEquals(128f, inputQuantization.getZeroPoint(), DELTA);
        assertEquals(0.0078125f, inputQuantization.getScale(), DELTA);

        TensorInfo outputTensorInfo = modelInfo.getOutputs().get(0);
        assertEquals("probability", outputTensorInfo.getIdentifierName());
        assertEquals(TensorInfo.FileType.TENSOR_AXIS_LABELS, outputTensorInfo.getFileType());
        MetadataExtractor.QuantizationParams outputQuantization =
                outputTensorInfo.getQuantizationParams();
        assertEquals(0, outputQuantization.getZeroPoint(), DELTA);
        assertEquals(0.00390625, outputQuantization.getScale(), DELTA);
    }

    @Test
    public void testQuantModelExtractedCorrectly() throws TfliteModelException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        extractByteBufferFromModel(
                                "prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_no_metadata.tflite"));

        assertEquals(1, modelInfo.getInputs().size());
        assertEquals(1, modelInfo.getOutputs().size());
        assertFalse(modelInfo.isMetadataExisted());
        assertEquals("inputFeature0", modelInfo.getInputs().get(0).getIdentifierName());
        assertEquals("outputFeature0", modelInfo.getOutputs().get(0).getIdentifierName());
    }

    private static ByteBuffer extractByteBufferFromModel(String filePath) throws IOException {
        File modelFile = TestUtils.getWorkspaceFile(filePath);
        try (RandomAccessFile f = new RandomAccessFile(modelFile, "r")) {
            byte[] data = new byte[(int) f.length()];
            f.readFully(data);
            return ByteBuffer.wrap(data);
        }
    }
}
