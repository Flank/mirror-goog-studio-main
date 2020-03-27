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

@file:JvmName("InjectorUtils")

package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector

import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.AssociatedFileInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultPostprocessorInitInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultPreprocessorInitInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultProcessInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImagePostprocessorInitInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImagePreprocessorInitInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImageProcessInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass.OutputsClassInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.DefaultGetMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.ImageGetMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.LabelGetMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.MethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.isRGBImage
import com.android.tools.mlkit.TensorInfo

fun getFieldInjector(): FieldInjector {
    return FieldInjector()
}

fun getOutputsClassInjector(): OutputsClassInjector {
    return OutputsClassInjector()
}

fun getAssociatedFileInjector(): AssociatedFileInjector {
    return AssociatedFileInjector()
}

fun getGetterMethodInjector(tensorInfo: TensorInfo): MethodInjector {
    return when {
        isRGBImage(tensorInfo) -> ImageGetMethodInjector()
        tensorInfo.fileType == TensorInfo.FileType.TENSOR_AXIS_LABELS -> LabelGetMethodInjector()
        else -> DefaultGetMethodInjector()
    }
}

fun getInputProcessorInjector(tensorInfo: TensorInfo): CodeBlockInjector {
    return if (isRGBImage(tensorInfo)) {
        ImagePreprocessorInitInjector()
    } else {
        DefaultPreprocessorInitInjector()
    }
}

fun getProcessInjector(tensorInfo: TensorInfo): CodeBlockInjector {
    return if (isRGBImage(tensorInfo)) {
        ImageProcessInjector()
    } else {
        DefaultProcessInjector()
    }
}

fun getOutputProcessorInjector(tensorInfo: TensorInfo): CodeBlockInjector {
    return if (isRGBImage(tensorInfo)) {
        ImagePostprocessorInitInjector()
    } else {
        DefaultPostprocessorInitInjector()
    }
}