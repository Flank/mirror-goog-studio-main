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
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.ImagePreprocessorInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.tensor.TensorBufferInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.tensor.TensorImageInitInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass.InputsClassInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass.OutputsClassInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.DefaultGetMethodInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.DefaultLoadMethodInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.ImageLoadMethodInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.LabelGetMethodInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.MethodInjector;
import com.android.tools.mlkit.Param;

/** Utils to select injector based on {@link Param} */
public class InjectorUtils {

    public static FieldInjector getFieldInjector() {
        return new FieldInjector();
    }

    public static OutputsClassInjector getOutputsClassInjector() {
        return new OutputsClassInjector();
    }

    public static InputsClassInjector getInputsClassInjector() {
        return new InputsClassInjector();
    }

    public static AssociatedFileInjector getAssociatedFileInjector() {
        return new AssociatedFileInjector();
    }

    public static MethodInjector getGetterMethodInjector(Param param) {
        if (param.getFileType() == Param.FileType.TENSOR_AXIS_LABELS) {
            return new LabelGetMethodInjector();
        } else {
            return new DefaultGetMethodInjector();
        }
    }

    public static MethodInjector getLoadMethodInjector(Param param) {
        if (param.getContentType() == Param.ContentType.IMAGE) {
            return new ImageLoadMethodInjector();
        } else {
            return new DefaultLoadMethodInjector();
        }
    }

    public static CodeBlockInjector getTensorInitInjector(Param param) {
        if (param.getContentType() == Param.ContentType.IMAGE) {
            return new TensorImageInitInjector();
        } else {
            return new TensorBufferInitInjector();
        }
    }

    public static CodeBlockInjector getInputProcessorInjector(Param param) {
        if (param.getContentType() == Param.ContentType.IMAGE) {
            return new ImagePreprocessorInitInjector();
        } else {
            return new DefaultPreprocessorInitInjector();
        }
    }

    public static CodeBlockInjector getOutputProcessorInjector(Param param) {
        return new DefaultPostprocessorInitInjector();
    }
}
