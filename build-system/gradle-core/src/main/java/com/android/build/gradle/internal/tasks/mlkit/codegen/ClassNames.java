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

package com.android.build.gradle.internal.tasks.mlkit.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;

/** Stores class names used by code generator */
public class ClassNames {
    // Basic type
    public static final ClassName STRING = ClassName.get("java.lang", "String");
    public static final ClassName LIST = ClassName.get("java.util", "List");
    public static final ClassName BITMAP = ClassName.get("android.graphics", "Bitmap");
    public static final ClassName BYTE_BUFFER = ClassName.get("java.nio", "ByteBuffer");
    public static final ClassName CONTEXT = ClassName.get("android.content", "Context");
    public static final ClassName IO_EXCEPTION = ClassName.get("java.io", "IOException");
    public static final ClassName MAP = ClassName.get("java.util", "Map");
    public static final ClassName FLOAT = ClassName.get("java.lang", "Float");
    public static final ClassName INTEGER = ClassName.get("java.lang", "Integer");
    public static final ClassName OBJECT = ClassName.get("java.lang", "Object");
    public static final ClassName HASH_MAP = ClassName.get("java.util", "HashMap");
    public static final ClassName INPUT_STREAM = ClassName.get("java.io", "InputStream");
    public static final ClassName ZIP_ARCHIVE_ENTRY =
            ClassName.get("org.apache.commons.compress.archivers.zip", "ZipArchiveEntry");
    public static final ClassName ZIP_FILE =
            ClassName.get("org.apache.commons.compress.archivers.zip", "ZipFile");
    public static final ClassName IO_UTILS =
            ClassName.get("org.apache.commons.compress.utils", "IOUtils");
    public static final ClassName SEEKABLE_IN_MEMORY_BYTE_CHANNEL =
            ClassName.get("org.apache.commons.compress.utils", "SeekableInMemoryByteChannel");
    public static final ParameterizedTypeName LIST_OF_STRING =
            ParameterizedTypeName.get(LIST, STRING);

    // ML model related type
    public static final ClassName DATA_TYPE = ClassName.get("org.tensorflow.lite", "DataType");
    public static final ClassName FILE_UTIL =
            ClassName.get("org.tensorflow.lite.support.common", "FileUtil");
    public static final ClassName TENSOR_PROCESSOR =
            ClassName.get("org.tensorflow.lite.support.common", "TensorProcessor");
    public static final ClassName CAST_OP =
            ClassName.get("org.tensorflow.lite.support.common.ops", "CastOp");
    public static final ClassName DEQUANTIZE_OP =
            ClassName.get("org.tensorflow.lite.support.common.ops", "DequantizeOp");
    public static final ClassName NORMALIZE_OP =
            ClassName.get("org.tensorflow.lite.support.common.ops", "NormalizeOp");
    public static final ClassName QUANTIZE_OP =
            ClassName.get("org.tensorflow.lite.support.common.ops", "QuantizeOp");
    public static final ClassName IMAGE_PROCESSOR =
            ClassName.get("org.tensorflow.lite.support.image", "ImageProcessor");
    public static final ClassName TENSOR_IMAGE =
            ClassName.get("org.tensorflow.lite.support.image", "TensorImage");
    public static final ClassName RESIZE_OP =
            ClassName.get("org.tensorflow.lite.support.image.ops", "ResizeOp");
    public static final ClassName RESIZE_METHOD =
            ClassName.get("org.tensorflow.lite.support.image.ops.ResizeOp", "ResizeMethod");
    public static final ClassName TENSOR_LABEL =
            ClassName.get("org.tensorflow.lite.support.label", "TensorLabel");
    public static final ClassName METADATA_EXTRACTOR =
            ClassName.get("org.tensorflow.lite.support.metadata", "MetadataExtractor");
    public static final ClassName MODEL =
            ClassName.get("org.tensorflow.lite.support.model", "Model");
    public static final ClassName TENSOR_BUFFER =
            ClassName.get("org.tensorflow.lite.support.tensorbuffer", "TensorBuffer");
}
