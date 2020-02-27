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
import org.junit.Test;

public class ModelInfoTest {
    private static final double DELTA = 10e-6;

    @Test
    public void testQuantMetadataModelExtractedCorrectly()
            throws ModelParsingException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        createExtractorFromModel(
                                "prebuilts/tools/common/mlkit/testData/mobilenet_quant_metadata.tflite"));

        assertEquals(modelInfo.getInputs().size(), 1);
        assertEquals(modelInfo.getOutputs().size(), 1);
        assertEquals(modelInfo.isMetadataExisted(), true);

        TensorInfo inputTensorInfo = modelInfo.getInputs().get(0);
        assertEquals(inputTensorInfo.getContentType(), TensorInfo.ContentType.IMAGE);
        assertEquals(inputTensorInfo.getName(), "image1");
        MetadataExtractor.NormalizationParams inputNormalization =
                inputTensorInfo.getNormalizationParams();
        assertEquals(inputNormalization.getMean()[0], 127.5f, DELTA);
        assertEquals(inputNormalization.getStd()[0], 127.5f, DELTA);
        assertEquals(inputNormalization.getMin()[0], -1f, DELTA);
        assertEquals(inputNormalization.getMax()[0], 1f, DELTA);
        MetadataExtractor.QuantizationParams inputQuantization =
                inputTensorInfo.getQuantizationParams();
        assertEquals(inputQuantization.getZeroPoint(), 128f, DELTA);
        assertEquals(inputQuantization.getScale(), 0.0078125f, DELTA);

        TensorInfo outputTensorInfo = modelInfo.getOutputs().get(0);
        assertEquals(outputTensorInfo.getName(), "probability");
        assertEquals(outputTensorInfo.getFileType(), TensorInfo.FileType.TENSOR_AXIS_LABELS);
        MetadataExtractor.QuantizationParams outputQuantization =
                outputTensorInfo.getQuantizationParams();
        assertEquals(outputQuantization.getZeroPoint(), 0, DELTA);
        assertEquals(outputQuantization.getScale(), 0.00390625, DELTA);
    }

    @Test
    public void testQuantModelExtractedCorrectly() throws ModelParsingException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        createExtractorFromModel(
                                "prebuilts/tools/common/mlkit/testData/mobilenet_quant_no_metadata.tflite"));

        assertEquals(modelInfo.getInputs().size(), 1);
        assertEquals(modelInfo.getOutputs().size(), 1);
        assertEquals(modelInfo.isMetadataExisted(), false);
        assertEquals(modelInfo.getInputs().get(0).getName(), "inputFeature0");
        assertEquals(modelInfo.getOutputs().get(0).getName(), "outputFeature0");
    }

    private MetadataExtractor createExtractorFromModel(String filePath) throws IOException {
        File modelFile = TestUtils.getWorkspaceFile(filePath);
        RandomAccessFile f = new RandomAccessFile(modelFile, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        f.close();
        return new MetadataExtractor(ByteBuffer.wrap(data));
    }
}
