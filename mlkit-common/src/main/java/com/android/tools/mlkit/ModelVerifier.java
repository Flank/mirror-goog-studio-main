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

import static com.android.tools.mlkit.ModelParsingException.ErrorType;

import java.util.HashSet;
import java.util.Set;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/** Verify whether model is valid to generate code */
public class ModelVerifier {
    public static void verifyModel(MetadataExtractor extractor) throws ModelParsingException {
        if (extractor.getSubgraphCount() != 1) {
            throw new ModelParsingException(
                    ErrorType.UNSUPPORTED_SUBGRAPH, "Only support for model with 1 subgraph");
        }

        ModelMetadata metadata = extractor.getModelMetaData();
        if (metadata == null) {
            throw new ModelParsingException(
                    ErrorType.INVALID_METADATA, "Model doesn't have valid metadata");
        }

        int inputCount = extractor.getInputTensorCount(0);
        Set<String> inputNameSet = new HashSet<>();
        for (int i = 0; i < inputCount; i++) {
            verifyDataType(extractor.getInputTensorType(0, i), i, TensorInfo.Source.INPUT);

            TensorMetadata tensorMetadata = metadata.subgraphMetadata(0).inputTensorMetadata(i);
            verifyTensorMetadata(tensorMetadata, i, TensorInfo.Source.INPUT);

            if (inputNameSet.contains(tensorMetadata.name())) {
                throw new ModelParsingException(
                        ErrorType.PARAM_NAME_CONFLICT,
                        "More than one tensor has same name: " + tensorMetadata.name());
            }
            inputNameSet.add(tensorMetadata.name());

            if (TensorInfo.ContentType.fromByte(tensorMetadata.contentType())
                            == TensorInfo.ContentType.IMAGE
                    && extractor.getInputTensorShape(0, i).length != 4) {
                throw new ModelParsingException(
                        ErrorType.INVALID_IMAGE_TENSOR,
                        "Image tensor doesn't have valid tensor shape");
            }
        }

        Set<String> outputNameSet = new HashSet<>();
        int outputCount = extractor.getOutputTensorCount(0);
        for (int i = 0; i < outputCount; i++) {
            verifyDataType(extractor.getOutputTensorType(0, i), i, TensorInfo.Source.OUTPUT);

            TensorMetadata tensorMetadata = metadata.subgraphMetadata(0).outputTensorMetadata(i);
            verifyTensorMetadata(tensorMetadata, i, TensorInfo.Source.OUTPUT);
            if (outputNameSet.contains(tensorMetadata.name())) {
                throw new ModelParsingException(
                        ErrorType.PARAM_NAME_CONFLICT,
                        "More than one tensor has same name: " + tensorMetadata.name());
            }
            outputNameSet.add(tensorMetadata.name());
        }
    }

    private static void verifyTensorMetadata(
            TensorMetadata tensorMetadata, int index, TensorInfo.Source source)
            throws ModelParsingException {
        if (tensorMetadata.name() == null) {
            throw new ModelParsingException(
                    ErrorType.INVALID_PARAM_NAME,
                    String.format(
                            "%s tensor %d doesn't have valid name",
                            source == TensorInfo.Source.INPUT ? "Input" : "Output", index));
        }
    }

    private static void verifyDataType(byte dataType, int index, TensorInfo.Source source)
            throws ModelParsingException {
        if (TensorInfo.DataType.fromByte(dataType) == null) {
            throw new ModelParsingException(
                    ErrorType.UNSUPPORTED_DATA_TYPE,
                    String.format(
                            "Datatype of %s tensor %d is not supported",
                            source == TensorInfo.Source.INPUT ? "input" : "output", index));
        }
    }
}
