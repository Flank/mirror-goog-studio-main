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

package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor;

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames;
import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector;
import com.android.tools.mlkit.MetadataExtractor;
import com.android.tools.mlkit.Param;
import com.squareup.javapoet.MethodSpec;

/**
 * Injector to init a image preprocessor, which does image resize, normalization, quantization and
 * cast.
 */
public class ImagePreprocessorInitInjector extends CodeBlockInjector {

    @Override
    public void inject(MethodSpec.Builder methodBuilder, Param param) {
        methodBuilder.addCode(
                "$T.Builder $L = new $T.Builder()\n",
                ClassNames.IMAGE_PROCESSOR,
                CodeUtils.getProcessorBuilderName(param),
                ClassNames.IMAGE_PROCESSOR);

        methodBuilder.addCode(
                "  .add(new $T($L, $L, $T.NEAREST_NEIGHBOR))\n",
                ClassNames.RESIZE_OP,
                param.getShape()[1],
                param.getShape()[2],
                ClassNames.RESIZE_METHOD);

        MetadataExtractor.NormalizationParams normalizationParams = param.getNormalizationParams();
        methodBuilder.addCode(
                "  .add(new $T($L, $L))\n",
                ClassNames.NORMALIZE_OP,
                CodeUtils.getFloatArrayString(normalizationParams.getMean()),
                CodeUtils.getFloatArrayString(normalizationParams.getStd()));

        MetadataExtractor.QuantizationParams quantizationParams = param.getQuantizationParams();
        methodBuilder.addCode(
                "  .add(new $T($Lf, $Lf))\n",
                ClassNames.QUANTIZE_OP,
                quantizationParams.getZeroPoint(),
                quantizationParams.getScale());

        methodBuilder.addCode(
                "  .add(new $T($T.$L));\n",
                ClassNames.CAST_OP,
                ClassNames.DATA_TYPE,
                CodeUtils.getDataType(param.getDataType()));

        methodBuilder.addStatement(
                "$L = $L.build()",
                CodeUtils.getProcessorName(param),
                CodeUtils.getProcessorBuilderName(param));
    }
}
