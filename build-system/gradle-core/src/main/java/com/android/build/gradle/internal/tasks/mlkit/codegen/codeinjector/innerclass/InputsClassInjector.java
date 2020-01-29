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
import com.android.tools.mlkit.Param;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import javax.lang.model.element.Modifier;

/** Injector to inject input inner class */
public class InputsClassInjector implements CodeInjector<TypeSpec.Builder, List<Param>> {

    @Override
    public void inject(TypeSpec.Builder classBuilder, List<Param> params) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(MlkitNames.INPUTS);
        builder.addModifiers(Modifier.PUBLIC);

        // Add necessary fields
        for (Param param : params) {
            FieldSpec fieldSpec =
                    FieldSpec.builder(CodeUtils.getParameterType(param), param.getName())
                            .addModifiers(Modifier.PRIVATE)
                            .build();
            builder.addField(fieldSpec);
        }

        // Add constructor
        MethodSpec.Builder constructorBuilder =
                MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        for (Param param : params) {
            InjectorUtils.getTensorInitInjector(param).inject(constructorBuilder, param);
        }
        builder.addMethod(constructorBuilder.build());

        // Add load methods for params
        for (Param param : params) {
            InjectorUtils.getLoadMethodInjector(param).inject(builder, param);
        }

        // Add a getBuffer method
        buildGetMethod(builder, params);

        classBuilder.addType(builder.build());
    }

    private void buildGetMethod(TypeSpec.Builder classBuilder, List<Param> params) {
        String[] array = new String[params.size()];
        for (int i = 0; i < params.size(); i++) {
            array[i] = params.get(i).getName() + ".getBuffer()";
        }

        MethodSpec.Builder getterBuilder =
                MethodSpec.methodBuilder("getBuffer")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(ArrayTypeName.of(Object.class))
                        .addStatement("return $L", CodeUtils.getObjectArrayString(array));

        classBuilder.addMethod(getterBuilder.build());
    }
}
