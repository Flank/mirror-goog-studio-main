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

import com.android.annotations.Nullable;
import java.nio.FloatBuffer;
import org.tensorflow.lite.schema.Tensor;
import org.tensorflow.lite.support.metadata.schema.AssociatedFile;
import org.tensorflow.lite.support.metadata.schema.Content;
import org.tensorflow.lite.support.metadata.schema.ContentProperties;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.NormalizationOptions;
import org.tensorflow.lite.support.metadata.schema.ProcessUnit;
import org.tensorflow.lite.support.metadata.schema.ProcessUnitOptions;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/**
 * Stores necessary data for each single input or output. For tflite model, this class stores
 * necessary data for input or output tensor.
 */
public class TensorInfo {
    private static final String DEFAULT_INPUT_NAME = "inputFeature";
    private static final String DEFAULT_OUTPUT_NAME = "outputFeature";

    public enum DataType {
        FLOAT32(0),
        INT32(2),
        UINT8(3),
        INT64(4);

        private final int id;

        DataType(int id) {
            this.id = id;
        }

        public static DataType fromByte(byte id) {
            for (DataType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum Source {
        INPUT,
        OUTPUT
    }

    public enum FileType {
        UNKNOWN(0),
        DESCRIPTIONS(1),
        TENSOR_AXIS_LABELS(2);

        private int id;

        FileType(int id) {
            this.id = id;
        }

        public static FileType fromByte(byte id) {
            for (FileType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum ContentType {
        UNKNOWN(0),
        FEATURE(1),
        IMAGE(2);

        private int id;

        ContentType(int id) {
            this.id = id;
        }

        public static ContentType fromByte(byte id) {
            for (ContentType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    private String name;
    private int[] shape;
    private DataType dataType;
    private String fileName;
    private FileType fileType;
    private Source source;
    private ContentType contentType;
    private String description;
    private boolean metadataExisted;
    private MetadataExtractor.NormalizationParams normalizationParams;
    private MetadataExtractor.QuantizationParams quantizationParams;
    @Nullable private ImageProperties imageProperties;

    public String getName() {
        return name;
    }

    public int[] getShape() {
        return shape;
    }

    public DataType getDataType() {
        return dataType;
    }

    public String getFileName() {
        return fileName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public Source getSource() {
        return source;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public ImageProperties getImageProperties() {
        return imageProperties;
    }

    public boolean isMetadataExisted() {
        return metadataExisted;
    }

    public MetadataExtractor.NormalizationParams getNormalizationParams() {
        return normalizationParams;
    }

    public MetadataExtractor.QuantizationParams getQuantizationParams() {
        return quantizationParams;
    }

    public static class Builder {
        private String name = "";
        private int[] shape;
        private DataType dataType;
        private String fileName;
        private FileType fileType = FileType.UNKNOWN;
        private Source source;
        private ContentType contentType = ContentType.UNKNOWN;
        private String description = "";
        private boolean metadataExisted;
        private MetadataExtractor.NormalizationParams normalizationParams;
        private MetadataExtractor.QuantizationParams quantizationParams;
        @Nullable private ImageProperties imageProperties;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setShape(int[] shape) {
            this.shape = shape;
            return this;
        }

        public Builder setDataType(DataType dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setFileType(FileType fileType) {
            this.fileType = fileType;
            return this;
        }

        public Builder setContentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setMetadataExisted(boolean metadataExisted) {
            this.metadataExisted = metadataExisted;
            return this;
        }

        public Builder setNormalizationParams(
                MetadataExtractor.NormalizationParams normalizationParams) {
            this.normalizationParams = normalizationParams;
            return this;
        }

        public Builder setQuantizationParams(
                MetadataExtractor.QuantizationParams quantizationParams) {
            this.quantizationParams = quantizationParams;
            return this;
        }

        public Builder setImageProperties(ImageProperties imageProperties) {
            this.imageProperties = imageProperties;
            return this;
        }

        public Builder setSource(Source source) {
            this.source = source;
            return this;
        }

        public TensorInfo build() {
            TensorInfo tensorInfo = new TensorInfo();
            tensorInfo.name = name;
            tensorInfo.shape = shape;
            tensorInfo.dataType = dataType;
            tensorInfo.fileName = fileName;
            tensorInfo.fileType = fileType;
            tensorInfo.source = source;
            tensorInfo.contentType = contentType;
            tensorInfo.description = description;
            tensorInfo.metadataExisted = metadataExisted;
            tensorInfo.normalizationParams = normalizationParams;
            tensorInfo.quantizationParams = quantizationParams;
            tensorInfo.imageProperties = imageProperties;

            return tensorInfo;
        }
    }

    public static TensorInfo parseFrom(MetadataExtractor extractor, Source source, int index) {
        TensorInfo.Builder builder = new TensorInfo.Builder();

        // Deal with data from original model
        if (source == Source.INPUT) {
            builder.setShape(extractor.getInputTensorShape(0, index));
            builder.setDataType(DataType.fromByte(extractor.getInputTensorType(0, index)));
        } else {
            builder.setShape(extractor.getOutputTensorShape(0, index));
            builder.setDataType(DataType.fromByte(extractor.getOutputTensorType(0, index)));
        }
        builder.setSource(source);

        // Deal with data from extra metadata
        ModelMetadata metadata = extractor.getModelMetaData();
        if (metadata == null) {
            builder.setMetadataExisted(false);
            builder.setName(getDefaultName(source, index));
        } else {
            builder.setMetadataExisted(true);
            TensorMetadata tensorMetadata =
                    source == Source.INPUT
                            ? metadata.subgraphMetadata(0).inputTensorMetadata(index)
                            : metadata.subgraphMetadata(0).outputTensorMetadata(index);
            Tensor tensor =
                    source == Source.INPUT
                            ? extractor.getInputTensor(0, index)
                            : extractor.getOutputTensor(0, index);

            AssociatedFile file = tensorMetadata.associatedFiles(0);
            if (file != null) {
                builder.setFileName(file.name());
                builder.setFileType(FileType.fromByte(file.type()));
            }

            builder.setContentType(extractContentType(tensorMetadata));
            builder.setName(
                    MlkitNames.computeIdentifierName(
                            tensorMetadata.name(), getDefaultName(source, index)));
            builder.setDescription(tensorMetadata.description());
            builder.setQuantizationParams(MetadataExtractor.getQuantizationParams(tensor));

            if (builder.contentType == ContentType.IMAGE) {
                org.tensorflow.lite.support.metadata.schema.ImageProperties properties =
                        (org.tensorflow.lite.support.metadata.schema.ImageProperties)
                                tensorMetadata
                                        .content()
                                        .contentProperties(
                                                new org.tensorflow.lite.support.metadata.schema
                                                        .ImageProperties());
                builder.setImageProperties(
                        new ImageProperties(
                                ImageProperties.ColorSpaceType.fromByte(properties.colorSpace())));
            }

            NormalizationOptions normalizationOptions = extractNormalizationOptions(tensorMetadata);

            FloatBuffer mean =
                    normalizationOptions != null
                            ? normalizationOptions.meanAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(0);
            FloatBuffer std =
                    normalizationOptions != null
                            ? normalizationOptions.stdAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(1);
            FloatBuffer min =
                    tensorMetadata.stats() != null
                            ? tensorMetadata.stats().minAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(Float.MIN_VALUE);
            FloatBuffer max =
                    tensorMetadata.stats() != null
                            ? tensorMetadata.stats().maxAsByteBuffer().asFloatBuffer()
                            : toFloatBuffer(Float.MAX_VALUE);

            builder.setNormalizationParams(
                    new MetadataExtractor.NormalizationParams(mean, std, min, max));
        }

        return builder.build();
    }

    private static FloatBuffer toFloatBuffer(float value) {
        return FloatBuffer.wrap(new float[] {value});
    }

    private static String getDefaultName(Source source, int index) {
        return (source == Source.INPUT ? DEFAULT_INPUT_NAME : DEFAULT_OUTPUT_NAME) + index;
    }

    public static TensorInfo.ContentType extractContentType(TensorMetadata tensorMetadata) {
        Content content = tensorMetadata.content();
        if (content == null) {
            return ContentType.UNKNOWN;
        }
        byte type = content.contentPropertiesType();
        if (type == ContentProperties.ImageProperties) {
            return ContentType.IMAGE;
        } else if (type == ContentProperties.FeatureProperties) {
            return ContentType.FEATURE;
        }
        return ContentType.UNKNOWN;
    }

    private static NormalizationOptions extractNormalizationOptions(TensorMetadata tensorMetadata) {
        for (int i = 0; i < tensorMetadata.processUnitsLength(); i++) {
            ProcessUnit unit = tensorMetadata.processUnits(i);
            if (unit.optionsType() == ProcessUnitOptions.NormalizationOptions) {
                return (NormalizationOptions) unit.options(new NormalizationOptions());
            }
        }

        return null;
    }

    public static class ImageProperties {
        public enum ColorSpaceType {
            UNKNOWN(0),
            RGB(1),
            GRAYSCALE(2);

            private final int id;

            ColorSpaceType(int id) {
                this.id = id;
            }

            public static ColorSpaceType fromByte(byte id) {
                for (ColorSpaceType type : values()) {
                    if (type.id == id) {
                        return type;
                    }
                }
                return UNKNOWN;
            }
        }

        public final ColorSpaceType colorSpaceType;

        public ImageProperties(ColorSpaceType colorSpaceType) {
            this.colorSpaceType = colorSpaceType;
        }
    }
}
