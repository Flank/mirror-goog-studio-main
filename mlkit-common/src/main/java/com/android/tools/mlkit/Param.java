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

import org.tensorflow.lite.support.metadata.schema.AssociatedFile;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/**
 * Stores necessary data for each single input or output. For tflite model, this class stores
 * necessary data for input or output tensor.
 */
public class Param {
    public enum DataType {
        FLOAT32(0),
        INT32(2),
        BYTE(3),
        INT64(4);

        private int id;

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

        private FileType(int id) {
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

        private ContentType(int id) {
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

    private Param() {}

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

    public static class Builder {
        private String name;
        private int[] shape;
        private DataType dataType;
        private String fileName;
        private FileType fileType = FileType.UNKNOWN;
        private Source source;
        private ContentType contentType = ContentType.UNKNOWN;
        private String description;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setShape(int[] shape) {
            this.shape = shape;
            return this;
        }

        public Builder setSource(Source source) {
            this.source = source;
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

        public Param build() {
            Param param = new Param();
            param.name = name;
            param.shape = shape;
            param.dataType = dataType;
            param.fileName = fileName;
            param.fileType = fileType;
            param.source = source;
            param.contentType = contentType;
            param.description = description;

            return param;
        }
    }

    public static Param parseFrom(MetadataExtractor extractor, Source source, int index) {
        Builder builder = new Builder();
        if (source == Source.INPUT) {
            builder.setShape(extractor.getInputTensorShape(0, index));
            builder.setDataType(DataType.fromByte(extractor.getInputTensorType(0, index)));
        } else {
            builder.setShape(extractor.getOutputTensorShape(0, index));
            builder.setDataType(DataType.fromByte(extractor.getOutputTensorType(0, index)));
        }

        ModelMetadata metadata = extractor.getModelMetaData();
        TensorMetadata tensorMetadata =
                source == Source.INPUT
                        ? metadata.subgraphMetadata(0).inputTensorMetadata(index)
                        : metadata.subgraphMetadata(0).outputTensorMetadata(index);

        AssociatedFile file = tensorMetadata.associatedFiles(0);
        if (file != null) {
            builder.setFileName(file.name());
            builder.setFileType(FileType.fromByte(file.type()));
        }

        builder.setContentType(ContentType.fromByte(tensorMetadata.contentType()));
        builder.setName(tensorMetadata.name() == null ? "data" + index : tensorMetadata.name());
        builder.setDescription(tensorMetadata.description());
        builder.setSource(source);

        return builder.build();
    }
}
