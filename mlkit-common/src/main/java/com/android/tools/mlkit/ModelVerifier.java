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

import com.android.tools.mlkit.exception.InvalidTfliteException;
import com.android.tools.mlkit.exception.TfliteModelException;
import com.android.tools.mlkit.exception.UnsupportedTfliteException;
import com.android.tools.mlkit.exception.UnsupportedTfliteMetadataException;
import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/** Verify whether model is valid to generate code */
public class ModelVerifier {
    public static void verifyModel(ByteBuffer byteBuffer) throws TfliteModelException {
        MetadataExtractor extractor;
        try {
            extractor = new MetadataExtractor(byteBuffer);
        } catch (Exception e) {
            throw new InvalidTfliteException("Input is not in a valid TFLite flatbuffer format.");
        }
        verifyModel(extractor);
    }

    @VisibleForTesting
    static void verifyModel(MetadataExtractor extractor) throws TfliteModelException {
        if (extractor.getSubgraphCount() != 1) {
            throw new UnsupportedTfliteException("Only support for model with 1 subgraph");
        }

        ModelMetadata metadata = extractor.getModelMetaData();
        if (metadata == null) {
            return;
        }

        int inputCount = extractor.getInputTensorCount(0);
        Set<String> inputNameSet = new HashSet<>();
        for (int i = 0; i < inputCount; i++) {
            verifyDataType(extractor.getInputTensorType(0, i), i, TensorInfo.Source.INPUT);

            TensorMetadata tensorMetadata = metadata.subgraphMetadata(0).inputTensorMetadata(i);
            verifyTensorMetadata(tensorMetadata, i, TensorInfo.Source.INPUT);

            String formattedName = MlkitNames.computeIdentifierName(tensorMetadata.name());
            if (inputNameSet.contains(formattedName)) {
                throw new UnsupportedTfliteMetadataException(
                        "More than one tensor has same name: " + formattedName);
            }
            inputNameSet.add(formattedName);

            if (TensorInfo.extractContentType(tensorMetadata) == TensorInfo.ContentType.IMAGE
                    && extractor.getInputTensorShape(0, i).length != 4) {
                throw new UnsupportedTfliteMetadataException(
                        "Image tensor shape doesn't have length as 4");
            }
        }

        Set<String> outputNameSet = new HashSet<>();
        int outputCount = extractor.getOutputTensorCount(0);
        for (int i = 0; i < outputCount; i++) {
            verifyDataType(extractor.getOutputTensorType(0, i), i, TensorInfo.Source.OUTPUT);

            TensorMetadata tensorMetadata = metadata.subgraphMetadata(0).outputTensorMetadata(i);
            verifyTensorMetadata(tensorMetadata, i, TensorInfo.Source.OUTPUT);

            String formattedName = MlkitNames.computeIdentifierName(tensorMetadata.name());
            if (outputNameSet.contains(formattedName)) {
                throw new UnsupportedTfliteMetadataException(
                        "More than one tensor has same name: " + formattedName);
            }
            outputNameSet.add(formattedName);
        }
    }

    private static void verifyTensorMetadata(
            TensorMetadata tensorMetadata, int index, TensorInfo.Source source)
            throws TfliteModelException {
        if (tensorMetadata == null) {
            throw new UnsupportedTfliteMetadataException(
                    String.format(
                            "Metadata of %s tensor %d is null",
                            source == TensorInfo.Source.INPUT ? "Input" : "Output", index));
        }
        if (tensorMetadata.name() == null) {
            throw new UnsupportedTfliteMetadataException(
                    String.format(
                            "%s tensor %d has name as null",
                            source == TensorInfo.Source.INPUT ? "Input" : "Output", index));
        }
    }

    private static void verifyDataType(byte dataType, int index, TensorInfo.Source source)
            throws TfliteModelException {
        if (TensorInfo.DataType.fromByte(dataType) == null) {
            throw new UnsupportedTfliteMetadataException(
                    String.format(
                            "Datatype of %s tensor %d is not supported",
                            source == TensorInfo.Source.INPUT ? "input" : "output", index));
        }
    }
}
