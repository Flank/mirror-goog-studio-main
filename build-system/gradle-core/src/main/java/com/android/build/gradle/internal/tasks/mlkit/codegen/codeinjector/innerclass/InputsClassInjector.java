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

import com.android.build.gradle.internal.tasks.mlkit.codegen.CodeUtils;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.InjectorUtils;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.TensorInfo;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;

/** Injector to inject input inner class */
public class InputsClassInjector implements CodeInjector<TypeSpec.Builder, List<TensorInfo>> {

    @Override
    public void inject(TypeSpec.Builder classBuilder, List<TensorInfo> tensorInfos) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(MlkitNames.INPUTS);
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
                MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        for (TensorInfo tensorInfo : tensorInfos) {
            InjectorUtils.getTensorInitInjector(tensorInfo).inject(constructorBuilder, tensorInfo);
        }
        builder.addMethod(constructorBuilder.build());

        // Add load methods for params
        for (TensorInfo tensorInfo : tensorInfos) {
            InjectorUtils.getLoadMethodInjector(tensorInfo).inject(builder, tensorInfo);
        }

        // Add a getBuffer method
        buildGetMethod(builder, tensorInfos);

        classBuilder.addType(builder.build());
    }

    private void buildGetMethod(TypeSpec.Builder classBuilder, List<TensorInfo> tensorInfos) {
        String[] array = new String[tensorInfos.size()];
        for (int i = 0; i < tensorInfos.size(); i++) {
            array[i] = tensorInfos.get(i).getName() + ".getBuffer()";
        }

        MethodSpec.Builder getterBuilder =
                MethodSpec.methodBuilder("getBuffer")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(ArrayTypeName.of(Object.class))
                        .addStatement("return $L", CodeUtils.getObjectArrayString(array));

        classBuilder.addMethod(getterBuilder.build());
    }
}
