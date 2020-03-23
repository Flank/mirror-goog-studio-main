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

package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass;

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames;
import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.InjectorUtils;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.TensorInfo;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;

/** Injector to inject output class. */
public class OutputsClassInjector implements CodeInjector<TypeSpec.Builder, List<TensorInfo>> {

    @Override
    public void inject(TypeSpec.Builder classBuilder, List<TensorInfo> tensorInfos) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(MlkitNames.OUTPUTS);
        builder.addModifiers(Modifier.PUBLIC);

        // Add necessary fields
        for (TensorInfo tensorInfo : tensorInfos) {
            FieldSpec fieldSpec =
                    FieldSpec.builder(CodeUtils.getParameterType(tensorInfo), tensorInfo.getName())
                            .addModifiers(Modifier.PRIVATE)
                            .build();
            builder.addField(fieldSpec);
        }

        // Add constructor
        MethodSpec.Builder constructorBuilder =
                MethodSpec.constructorBuilder()
                        .addParameter(ClassNames.MODEL, "model")
                        .addModifiers(Modifier.PRIVATE);
        int index = 0;
        for (TensorInfo tensorInfo : tensorInfos) {
            constructorBuilder.addStatement(
                    "this.$L = TensorBuffer.createFixedSize(model.getOutputTensorShape($L), $T.$L)",
                    tensorInfo.getName(),
                    index++,
                    ClassNames.DATA_TYPE,
                    CodeUtils.getDataType(tensorInfo.getDataType()));
        }
        builder.addMethod(constructorBuilder.build());

        // Add getter methods for each param
        for (TensorInfo tensorInfo : tensorInfos) {
            InjectorUtils.getGetterMethodInjector(tensorInfo).inject(builder, tensorInfo);
        }

        // Add getBuffer method for inner usage
        buildGetBufferMethod(builder, tensorInfos);

        classBuilder.addType(builder.build());
    }

    private void buildGetBufferMethod(TypeSpec.Builder classBuilder, List<TensorInfo> tensorInfos) {
        TypeName mapType =
                ParameterizedTypeName.get(ClassNames.MAP, ClassNames.INTEGER, ClassNames.OBJECT);
        MethodSpec.Builder getterBuilder =
                MethodSpec.methodBuilder("getBuffer")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(mapType)
                        .addAnnotation(ClassNames.NON_NULL)
                        .addStatement("$T outputs = new $T<>()", mapType, ClassNames.HASH_MAP);

        int index = 0;
        for (TensorInfo tensorInfo : tensorInfos) {
            getterBuilder.addStatement(
                    "outputs.put($L, $L.getBuffer())", index, tensorInfo.getName());
            index++;
        }
        getterBuilder.addStatement("return outputs");

        classBuilder.addMethod(getterBuilder.build());
    }
}
