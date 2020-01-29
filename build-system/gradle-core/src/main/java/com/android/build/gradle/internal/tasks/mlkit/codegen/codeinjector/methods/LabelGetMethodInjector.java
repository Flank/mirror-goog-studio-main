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

package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods;

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames;
import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils;
import com.android.tools.mlkit.Param;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/** Inject a get method to get label. */
public class LabelGetMethodInjector extends MethodInjector {

    @Override
    public void inject(TypeSpec.Builder classBuilder, Param param) {
        MethodSpec methodSpec =
                MethodSpec.methodBuilder("get" + CodeUtils.getUpperCamelName(param.getName()))
                        .addModifiers(Modifier.PUBLIC)
                        .returns(CodeUtils.getOutputParameterType(param))
                        .addStatement(
                                "return new $T($L, $L.process($L)).getMapWithFloatValue()",
                                ClassNames.TENSOR_LABEL,
                                CodeUtils.getFileName(param.getFileName()),
                                CodeUtils.getProcessorName(param),
                                param.getName())
                        .build();
        classBuilder.addMethod(methodSpec);
    }
}
