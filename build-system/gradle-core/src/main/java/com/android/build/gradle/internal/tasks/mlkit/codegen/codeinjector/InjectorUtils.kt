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

package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector;

import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.AssociatedFileInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultPostprocessorInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultPreprocessorInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultProcessInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImagePostprocessorInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImagePreprocessorInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImageProcessInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass.OutputsClassInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.DefaultGetMethodInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.LabelGetMethodInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.MethodInjector;
import com.android.tools.mlkit.TensorInfo;

/** Utils to select injector based on {@link TensorInfo} */
public class InjectorUtils {

    public static FieldInjector getFieldInjector() {
        return new FieldInjector();
    }

    public static OutputsClassInjector getOutputsClassInjector() {
        return new OutputsClassInjector();
    }

    public static AssociatedFileInjector getAssociatedFileInjector() {
        return new AssociatedFileInjector();
    }

    public static MethodInjector getGetterMethodInjector(TensorInfo tensorInfo) {
        if (tensorInfo.getFileType() == TensorInfo.FileType.TENSOR_AXIS_LABELS) {
            return new LabelGetMethodInjector();
        } else {
            return new DefaultGetMethodInjector();
        }
    }

    public static CodeBlockInjector getInputProcessorInjector(TensorInfo tensorInfo) {
        if (tensorInfo.getContentType() == TensorInfo.ContentType.IMAGE) {
            return new ImagePreprocessorInitInjector();
        } else {
            return new DefaultPreprocessorInitInjector();
        }
    }

    public static CodeBlockInjector getProcessInjector(TensorInfo tensorInfo) {
        if (tensorInfo.getContentType() == TensorInfo.ContentType.IMAGE) {
            return new ImageProcessInjector();
        } else {
            return new DefaultProcessInjector();
        }
    }

    public static CodeBlockInjector getOutputProcessorInjector(TensorInfo tensorInfo) {
        if (tensorInfo.getContentType() == TensorInfo.ContentType.IMAGE) {
            return new ImagePostprocessorInitInjector();
        } else {
            return new DefaultPostprocessorInitInjector();
        }
    }
}
