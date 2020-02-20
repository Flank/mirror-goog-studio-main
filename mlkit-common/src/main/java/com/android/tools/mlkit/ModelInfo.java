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

import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;

/** Stores necessary data for one model. */
public class ModelInfo {
    /** stores necessary data for model input */
    private List<TensorInfo> inputs;

    /** stores necessary data for model output */
    private List<TensorInfo> outputs;

    private String modelName;
    private String modelDescription;
    private String modelVersion;
    private String modelAuthor;
    private String modelLicense;

    private ModelInfo() {
        inputs = new ArrayList<>();
        outputs = new ArrayList<>();
    }

    public List<TensorInfo> getInputs() {
        return inputs;
    }

    public List<TensorInfo> getOutputs() {
        return outputs;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelDescription() {
        return modelDescription;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getModelAuthor() {
        return modelAuthor;
    }

    public String getModelLicense() {
        return modelLicense;
    }

    public static ModelInfo buildFrom(MetadataExtractor extractor) throws ModelParsingException {
        ModelVerifier.verifyModel(extractor);

        ModelInfo modelInfo = new ModelInfo();
        int inputLength = extractor.getInputTensorCount(0);
        for (int i = 0; i < inputLength; i++) {
            modelInfo.inputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.INPUT, i));
        }

        int outputLength = extractor.getOutputTensorCount(0);
        for (int i = 0; i < outputLength; i++) {
            modelInfo.outputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.OUTPUT, i));
        }

        ModelMetadata modelMetadata = extractor.getModelMetaData();
        modelInfo.modelName = modelMetadata.name();
        modelInfo.modelDescription = modelMetadata.description();
        modelInfo.modelVersion = modelMetadata.version();
        modelInfo.modelAuthor = modelMetadata.author();
        modelInfo.modelLicense = modelMetadata.license();

        return modelInfo;
    }
}
