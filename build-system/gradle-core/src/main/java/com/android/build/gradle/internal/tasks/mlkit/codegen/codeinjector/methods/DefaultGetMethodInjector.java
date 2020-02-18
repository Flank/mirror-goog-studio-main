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

import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils;
import com.android.tools.mlkit.TensorInfo;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

/**
 * Injector to inject default implementation for getter method, which returns {@link
 * java.nio.ByteBuffer} and assume data type has method {@code getBuffer}.
 */
public class DefaultGetMethodInjector extends MethodInjector {

    @Override
    public void inject(TypeSpec.Builder classBuilder, TensorInfo tensorInfo) {
        MethodSpec methodSpec =
                MethodSpec.methodBuilder("get" + CodeUtils.getUpperCamelName(tensorInfo.getName()))
                        .addModifiers(Modifier.PUBLIC)
                        .returns(CodeUtils.getOutputParameterType(tensorInfo))
                        .addStatement("return $L.getBuffer()", tensorInfo.getName())
                        .build();
        classBuilder.addMethod(methodSpec);
    }
}
