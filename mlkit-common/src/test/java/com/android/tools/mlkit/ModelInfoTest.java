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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.mlkit.TensorInfo.ImageProperties;
import com.android.tools.mlkit.TensorInfo.ImageProperties.ColorSpaceType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;

public class ModelInfoTest {
    private static final double DELTA = 10e-6;

    @Test
    public void testImageClassificationQuantModelMetadataExtractedCorrectly()
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
        assertEquals("image", inputTensorInfo.getIdentifierName());
        assertEquals(new ImageProperties(ColorSpaceType.RGB), inputTensorInfo.getImageProperties());
        TensorInfo.NormalizationParams inputNormalization =
                inputTensorInfo.getNormalizationParams();
        assertEquals(127.5f, inputNormalization.getMean()[0], DELTA);
        assertEquals(127.5f, inputNormalization.getStd()[0], DELTA);
        assertEquals(0f, inputNormalization.getMin()[0], DELTA);
        assertEquals(255f, inputNormalization.getMax()[0], DELTA);
        TensorInfo.QuantizationParams inputQuantization = inputTensorInfo.getQuantizationParams();
        assertEquals(128f, inputQuantization.getZeroPoint(), DELTA);
        assertEquals(0.0078125f, inputQuantization.getScale(), DELTA);

        TensorInfo outputTensorInfo = modelInfo.getOutputs().get(0);
        assertEquals("probability", outputTensorInfo.getIdentifierName());
        assertEquals(TensorInfo.FileType.TENSOR_AXIS_LABELS, outputTensorInfo.getFileType());
        TensorInfo.QuantizationParams outputQuantization = outputTensorInfo.getQuantizationParams();
        assertEquals(0, outputQuantization.getZeroPoint(), DELTA);
        assertEquals(0.00390625, outputQuantization.getScale(), DELTA);
    }

    @Test
    public void testImageClassificationQuantModelExtractedCorrectly() throws TfliteModelException, IOException {
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

    @Test
    public void testImageClassificationModelWithMultipleLabelFiles()
            throws TfliteModelException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        extractByteBufferFromModel(
                                "prebuilts/tools/common/mlkit/testData/models/cropnet_classifier_multi_labels.tflite"));

        assertTrue(modelInfo.isMetadataExisted());
        assertEquals(1, modelInfo.getOutputs().size());
        // It should select "probability-labels-en.txt", not "probability-labels.txt".
        assertEquals("probability-labels-en.txt", modelInfo.getOutputs().get(0).getFileName());
    }

    @Test
    public void testObjectDetectionModelMetadataExtractedCorrectly()
            throws TfliteModelException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        extractByteBufferFromModel(
                                "prebuilts/tools/common/mlkit/testData/models/ssd_mobilenet_odt_metadata.tflite"));

        assertEquals(1, modelInfo.getInputs().size());
        assertEquals(4, modelInfo.getOutputs().size());
        assertTrue(modelInfo.isMetadataExisted());

        TensorInfo inputTensorInfo = modelInfo.getInputs().get(0);
        assertEquals(TensorInfo.ContentType.IMAGE, inputTensorInfo.getContentType());
        assertEquals("image", inputTensorInfo.getIdentifierName());
        assertEquals(new ImageProperties(ColorSpaceType.RGB), inputTensorInfo.getImageProperties());
        TensorInfo.NormalizationParams inputNormalization =
                inputTensorInfo.getNormalizationParams();
        assertEquals(127.5f, inputNormalization.getMean()[0], DELTA);
        assertEquals(127.5f, inputNormalization.getStd()[0], DELTA);
        assertEquals(0f, inputNormalization.getMin()[0], DELTA);
        assertEquals(255f, inputNormalization.getMax()[0], DELTA);
        TensorInfo.QuantizationParams inputQuantization = inputTensorInfo.getQuantizationParams();
        assertEquals(128f, inputQuantization.getZeroPoint(), DELTA);
        assertEquals(0.0078125f, inputQuantization.getScale(), DELTA);

        TensorInfo locationsTensor = modelInfo.getOutputs().get(0);
        assertEquals("locations", locationsTensor.getName());
        assertEquals(TensorInfo.ContentType.BOUNDING_BOX, locationsTensor.getContentType());
        TensorInfo.BoundingBoxProperties properties = locationsTensor.getBoundingBoxProperties();
        assertEquals(TensorInfo.BoundingBoxProperties.Type.BOUNDARIES, properties.type);
        assertEquals(TensorInfo.BoundingBoxProperties.CoordinateType.RATIO, properties.coordinateType);
        assertArrayEquals(new int[]{1, 0, 3, 2}, properties.index);
        TensorInfo.ContentRange contentRange = locationsTensor.getContentRange();
        assertEquals(2, contentRange.min);
        assertEquals(2, contentRange.max);

        TensorInfo classesTensor = modelInfo.getOutputs().get(1);
        assertEquals("classes", classesTensor.getName());
        assertEquals(TensorInfo.FileType.TENSOR_VALUE_LABELS, classesTensor.getFileType());
        assertEquals("labelmap.txt", classesTensor.getFileName());

        TensorInfo scoresTensor = modelInfo.getOutputs().get(2);
        assertEquals("scores", scoresTensor.getName());

        TensorInfo numOfDetectionsTensor = modelInfo.getOutputs().get(3);
        assertEquals("number of detections", numOfDetectionsTensor.getName());
    }

    @Test
    public void testV2ObjectDetectionModelGroupMetadataExtractedCorrectly()
            throws TfliteModelException, IOException {
        ModelInfo modelInfo =
                ModelInfo.buildFrom(
                        extractByteBufferFromModel(
                                "prebuilts/tools/common/mlkit/testData/models/ssd_mobilenet_odt_metadata_v1.2.tflite"));

        assertEquals(1, modelInfo.getInputs().size());
        assertEquals(4, modelInfo.getOutputs().size());
        assertTrue(modelInfo.isMetadataExisted());

        assertEquals(0, modelInfo.getInputTensorGroups().size());
        assertEquals(1, modelInfo.getOutputTensorGroups().size());
        TensorGroupInfo outputTensorGroupInfo = modelInfo.getOutputTensorGroups().get(0);
        assertEquals("detection result", outputTensorGroupInfo.getName());
        assertEquals(
                Arrays.asList("locations", "classes", "scores"),
                outputTensorGroupInfo.getTensorNames());
    }

    @Test
    public void testModelInfoSerialization() throws TfliteModelException, IOException {
        testModelInfoSerialization("prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite");
        testModelInfoSerialization("prebuilts/tools/common/mlkit/testData/models/ssd_mobilenet_odt_metadata.tflite");
        testModelInfoSerialization(
                "prebuilts/tools/common/mlkit/testData/models/ssd_mobilenet_odt_metadata_v1.2.tflite");
    }

    private static void testModelInfoSerialization(String modelPath)
            throws TfliteModelException, IOException {
        ModelInfo originalModelInfo =
                ModelInfo.buildFrom(
                        extractByteBufferFromModel(modelPath));

        byte[] serializedBytes;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            originalModelInfo.save(new DataOutputStream(byteArrayOutputStream));
            serializedBytes = byteArrayOutputStream.toByteArray();
        }

        ModelInfo deserializedModelInfo =
                new ModelInfo(new DataInputStream(new ByteArrayInputStream(serializedBytes)));
        assertEquals(originalModelInfo, deserializedModelInfo);
    }

    private static ByteBuffer extractByteBufferFromModel(String filePath) throws IOException {
        File modelFile = TestUtils.resolveWorkspacePath(filePath).toFile();
        try (RandomAccessFile f = new RandomAccessFile(modelFile, "r")) {
            byte[] data = new byte[(int) f.length()];
            f.readFully(data);
            return ByteBuffer.wrap(data);
        }
    }
}
