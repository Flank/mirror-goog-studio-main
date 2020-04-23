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

import com.android.annotations.NonNull;
import com.android.tools.mlkit.exception.TfliteModelException;
import com.google.common.base.Strings;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;

/** Stores necessary data for one model. */
public class ModelInfo {

    private final boolean metadataExisted;
    private final String modelName;
    private final String modelDescription;
    private final String modelVersion;
    private final String modelAuthor;
    private final String modelLicense;

    /** Stores necessary data for model input. */
    private final List<TensorInfo> inputs = new ArrayList<>();

    /** Stores necessary data for model output. */
    private final List<TensorInfo> outputs = new ArrayList<>();

    public ModelInfo() {
        metadataExisted = false;
        modelName = "";
        modelDescription = "";
        modelVersion = "";
        modelAuthor = "";
        modelLicense = "";
    }

    public ModelInfo(@NonNull ModelMetadata modelMetadata) {
        metadataExisted = true;
        modelName = Strings.nullToEmpty(modelMetadata.name());
        modelDescription = Strings.nullToEmpty(modelMetadata.description());
        modelVersion = Strings.nullToEmpty(modelMetadata.version());
        modelAuthor = Strings.nullToEmpty(modelMetadata.author());
        modelLicense = Strings.nullToEmpty(modelMetadata.license());
    }

    public boolean isMetadataExisted() {
        return metadataExisted;
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

    public List<TensorInfo> getInputs() {
        return inputs;
    }

    public List<TensorInfo> getOutputs() {
        return outputs;
    }

    public static ModelInfo buildFrom(ByteBuffer byteBuffer) throws TfliteModelException {
        ModelVerifier.verifyModel(byteBuffer);
        return buildWithoutVerification(byteBuffer);
    }

    public static ModelInfo buildWithoutVerification(ByteBuffer byteBuffer) {
        MetadataExtractor extractor = new MetadataExtractor(byteBuffer);
        ModelMetadata modelMetadata = extractor.getModelMetaData();
        ModelInfo modelInfo =
                modelMetadata != null ? new ModelInfo(modelMetadata) : new ModelInfo();

        int inputLength = extractor.getInputTensorCount();
        for (int i = 0; i < inputLength; i++) {
            modelInfo.inputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.INPUT, i));
        }

        int outputLength = extractor.getOutputTensorCount();
        for (int i = 0; i < outputLength; i++) {
            modelInfo.outputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.OUTPUT, i));
        }

        return modelInfo;
    }
}
