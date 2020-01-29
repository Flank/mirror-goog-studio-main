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

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames;
import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils;
import com.android.tools.mlkit.Param;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/** Inject fields based on {@link Param} */
public class FieldInjector implements CodeInjector<TypeSpec.Builder, Param> {

    @Override
    public void inject(TypeSpec.Builder classBuilder, Param param) {
        if (param.getFileName() != null) {
            FieldSpec fieldName =
                    FieldSpec.builder(
                                    ClassNames.LIST_OF_STRING,
                                    CodeUtils.getFileName(param.getFileName()))
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .build();
            classBuilder.addField(fieldName);
        }

        // Add preprocessor and postprocessor fields.
        if (param.getContentType() == Param.ContentType.IMAGE) {
            FieldSpec fieldName =
                    FieldSpec.builder(ClassNames.IMAGE_PROCESSOR, CodeUtils.getProcessorName(param))
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .build();
            classBuilder.addField(fieldName);
        } else {
            FieldSpec fieldName =
                    FieldSpec.builder(
                                    ClassNames.TENSOR_PROCESSOR, CodeUtils.getProcessorName(param))
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .build();
            classBuilder.addField(fieldName);
        }
    }
}
