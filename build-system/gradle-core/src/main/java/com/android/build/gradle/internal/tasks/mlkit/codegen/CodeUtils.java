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

import com.android.tools.mlkit.TensorInfo;
import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Utility methods to generate code. */
public class CodeUtils {

    private static final Map<TensorInfo.DataType, TypeName> typeToNameMap = new HashMap<>();

    static {
        typeToNameMap.put(TensorInfo.DataType.UINT8, TypeName.BYTE);
        typeToNameMap.put(TensorInfo.DataType.FLOAT32, TypeName.FLOAT);
        typeToNameMap.put(TensorInfo.DataType.INT64, TypeName.LONG);
        typeToNameMap.put(TensorInfo.DataType.INT32, TypeName.INT);
    }

    public static TypeName getParameterType(TensorInfo tensorInfo) {
        if (tensorInfo.getSource() == TensorInfo.Source.INPUT) {
            if (tensorInfo.getContentType() == TensorInfo.ContentType.IMAGE) {
                return ClassNames.TENSOR_IMAGE;
            } else {
                return ClassNames.TENSOR_BUFFER;
            }
        } else {
            return ClassNames.TENSOR_BUFFER;
        }
    }

    public static String getFileName(String name) {
        return name.replaceAll("\\..*", "") + "Data";
    }

    public static String getProcessorName(TensorInfo tensorInfo) {
        if (tensorInfo.getSource() == TensorInfo.Source.INPUT) {
            return tensorInfo.getName() + "Processor";
        } else {
            return tensorInfo.getName() + "PostProcessor";
        }
    }

    public static String getProcessorBuilderName(TensorInfo tensorInfo) {
        return getProcessorName(tensorInfo) + "Builder";
    }

    public static String getFloatArrayString(float[] array) {
        String localArray[] = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            localArray[i] = array[i] + "f";
        }
        return getArrayString("float", localArray);
    }

    public static String getIntArrayString(int[] array) {
        return getArrayString(
                "int", Arrays.stream(array).mapToObj(String::valueOf).toArray(String[]::new));
    }

    public static String getObjectArrayString(String[] array) {
        return getArrayString("Object", array);
    }

    private static String getArrayString(String type, String[] array) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("new %s[] {", type));
        for (String dim : array) {
            builder.append(dim + ",");
        }
        builder.deleteCharAt(builder.length() - 1);
        builder.append("}");

        return builder.toString();
    }

    public static String getDataType(TensorInfo.DataType type) {
        return type.toString();
    }

    public static TypeName getOutputParameterType(TensorInfo tensorInfo) {
        if (tensorInfo.getFileType() == TensorInfo.FileType.TENSOR_AXIS_LABELS) {
            return ParameterizedTypeName.get(ClassNames.MAP, ClassNames.STRING, ClassNames.FLOAT);
        } else {
            return ClassNames.BYTE_BUFFER;
        }
    }

    public static String getUpperCamelName(String name) {
        return CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, name);
    }
}
