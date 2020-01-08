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

import java.nio.ByteBuffer;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import tflite.Metadata;
import tflite.Model;
import tflite.SubGraph;
import tflite.Tensor;
import tflite.TensorType;

/** Class to load metadata from TFLite FlatBuffer. */
// TODO(jackqdyulei): This file is forked from metadata library because right now we only have
// Android library(not java one).
// Remove this file once we have official library.
public class MetadataExtractor {
    /** Model that is loaded from TFLite FlatBuffer. */
    private final Model bufferModel;

    /**
     * Creates a {@link MetadataExtractor} with TFLite FlatBuffer {@code buffer}.
     *
     * @throws NullPointerException if {@code buffer} is null.
     */
    public MetadataExtractor(ByteBuffer buffer) {

        this.bufferModel = Model.getRootAsModel(buffer);
    }

    /** Gets the count of subgraphs in the model. */
    public int getSubgraphCount() {
        return bufferModel.subgraphsLength();
    }

    /** Gets the count of input tensors in the subgraph with {@code subgraphIndex}. */
    public int getInputTensorCount(int subgraphIndex) {
        SubGraph subgraph = getSubGraph(subgraphIndex);
        return subgraph.inputsLength();
    }

    /** Gets the count of output tensors in the subgraph with {@code subgraphIndex}. */
    public int getOutputTensorCount(int subgraphIndex) {
        SubGraph subgraph = getSubGraph(subgraphIndex);
        return subgraph.outputsLength();
    }

    /** Gets shape of the input tensor with {@code subgraphIndex} and {@code inputIndex}. */
    public int[] getInputTensorShape(int subgraphIndex, int inputIndex) {
        Tensor tensor = getInputTensor(subgraphIndex, inputIndex);
        return getShape(tensor);
    }

    /**
     * Gets {@link TensorType} of the input tensor with {@code subgraphIndex} and {@code
     * inputIndex}.
     */
    public byte getInputTensorType(int subgraphIndex, int inputIndex) {
        Tensor tensor = getInputTensor(subgraphIndex, inputIndex);
        return tensor.type();
    }

    /** Gets shape of the output tensor with {@code subgraphIndex} and {@code outputIndex}. */
    public int[] getOutputTensorShape(int subgraphIndex, int outputIndex) {
        Tensor tensor = getOutputTensor(subgraphIndex, outputIndex);
        return getShape(tensor);
    }

    /**
     * Gets {@link TensorType} of the output tensor with {@code subgraphIndex} and {@code
     * outputIndex}.
     */
    public byte getOutputTensorType(int subgraphIndex, int outputIndex) {
        Tensor tensor = getOutputTensor(subgraphIndex, outputIndex);
        return tensor.type();
    }

    /**
     * Gets the subgraph with {@code subgraphIndex}.
     *
     * @throws IllegalArgumentException if {@code subgraphIndex} is out of bounds.
     */
    private SubGraph getSubGraph(int subgraphIndex) {
        return this.bufferModel.subgraphs(subgraphIndex);
    }

    /** Gets the input tensor with {@code subgraphIndex} and {@code inputIndex}. */
    private Tensor getInputTensor(int subgraphIndex, int inputIndex) {
        return getTensor(subgraphIndex, inputIndex, true);
    }

    /** Gets the output tensor with {@code subgraphIndex} and {@code outputIndex}. */
    private Tensor getOutputTensor(int subgraphIndex, int outputIndex) {
        return getTensor(subgraphIndex, outputIndex, false);
    }

    /**
     * Gets the input/output tensor with {@code subgraphIndex} and {@code tensorIndex}.
     *
     * @param isInput indicates the tensor is input or output.
     * @throws IllegalArgumentException if {@code tensorIndex} is out of bounds.
     */
    private Tensor getTensor(int subgraphIndex, int tensorIndex, boolean isInput) {
        SubGraph subgraph = getSubGraph(subgraphIndex);

        if (isInput) {
            // Input tensor.
            return subgraph.tensors(subgraph.inputs(tensorIndex));
        } else {
            // Output tensor.
            return subgraph.tensors(subgraph.outputs(tensorIndex));
        }
    }

    /** Gets the shape of a tensor. */
    private static int[] getShape(Tensor tensor) {
        int shapeDim = tensor.shapeLength();
        int[] tensorShape = new int[shapeDim];
        for (int i = 0; i < shapeDim; i++) {
            tensorShape[i] = tensor.shape(i);
        }
        return tensorShape;
    }

    public ModelMetadata getModelMetaData() {
        int length = bufferModel.metadataLength();
        for (int i = 0; i < length; i++) {
            Metadata metadata = bufferModel.metadata(i);
            if ("TFLITE_METADATA".equals(metadata.name())) {
                long bufferIndex = metadata.buffer();
                return ModelMetadata.getRootAsModelMetadata(
                        bufferModel.buffers((int) bufferIndex).dataAsByteBuffer());
            }
        }
        return null;
    }

    public String getOutputLabelFileName() {
        ModelMetadata modelMetadata = getModelMetaData();
        return modelMetadata.subgraphMetadata(0).outputTensorMetadata(0).associatedFiles(0).name();
    }
}
