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

package com.android.build.gradle.internal.tasks.mlkit.codegen;

import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.InjectorUtils;
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.ModelParsingException;
import com.android.tools.mlkit.TensorInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/** Generator to generate code for tflite model */
public class TfliteModelGenerator implements ModelGenerator {

    private static final String FIELD_MODEL = "model";

    private final Logger logger;
    private final String localModelPath;
    private final ModelInfo modelInfo;
    private final String className;
    private final String packageName;

    public TfliteModelGenerator(File modelFile, String packageName, String localModelPath)
            throws ModelParsingException {
        this.localModelPath = localModelPath;
        this.modelInfo = ModelInfo.buildFrom(ModelUtils.createMetadataExtractor(modelFile));
        this.packageName = packageName;
        this.logger = Logging.getLogger(this.getClass());
        className = MlkitNames.computeModelClassName(modelFile);
    }

    @Override
    public void generateBuildClass(DirectoryProperty outputDirProperty) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        if (modelInfo.isMetadataExisted()) {
            classBuilder.addJavadoc(modelInfo.getModelDescription());
        } else {
            classBuilder.addJavadoc(
                    "This model doesn't have metadata, so no javadoc can be generated.");
        }
        buildFields(classBuilder);
        buildConstructor(classBuilder);
        buildStaticNewInstanceMethod(classBuilder);
        buildGetAssociatedFileMethod(classBuilder);
        buildProcessMethod(classBuilder);
        buildInnerClass(classBuilder);

        // Final steps
        try {
            JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
            javaFile.writeTo(outputDirProperty.getAsFile().get());
        } catch (IOException e) {
            logger.debug("Failed to write mlkit generated java file");
        }
    }

    private void buildFields(TypeSpec.Builder classBuilder) {
        for (TensorInfo tensorInfo : modelInfo.getInputs()) {
            InjectorUtils.getFieldInjector().inject(classBuilder, tensorInfo);
        }

        for (TensorInfo tensorInfo : modelInfo.getOutputs()) {
            InjectorUtils.getFieldInjector().inject(classBuilder, tensorInfo);
        }

        FieldSpec model =
                FieldSpec.builder(ClassNames.MODEL, FIELD_MODEL)
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .addAnnotation(ClassNames.NON_NULL)
                        .build();
        classBuilder.addField(model);
    }

    private void buildGetAssociatedFileMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("getAssociatedFile")
                        .addParameter(ClassNames.CONTEXT, "context")
                        .addParameter(String.class, "fileName")
                        .addModifiers(Modifier.PRIVATE)
                        .addException(IOException.class)
                        .returns(InputStream.class);

        methodBuilder
                .addStatement(
                        "$T inputStream = context.getAssets().open($S)",
                        InputStream.class,
                        localModelPath)
                .addStatement(
                        "$T zipFile = new $T(new $T($T.toByteArray(inputStream)))",
                        ClassNames.ZIP_FILE,
                        ClassNames.ZIP_FILE,
                        ClassNames.SEEKABLE_IN_MEMORY_BYTE_CHANNEL,
                        ClassNames.IO_UTILS)
                .addStatement("return zipFile.getRawInputStream(zipFile.getEntry(fileName))");

        classBuilder.addMethod(methodBuilder.build());
    }

    private void buildInnerClass(TypeSpec.Builder classBuilder) {
        InjectorUtils.getOutputsClassInjector().inject(classBuilder, modelInfo.getOutputs());
    }

    private void buildConstructor(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder constructorBuilder =
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(ClassNames.CONTEXT, "context")
                        .addException(ClassNames.IO_EXCEPTION)
                        .addStatement(
                                "$L = new $T.Builder(context, $S).build()",
                                FIELD_MODEL,
                                ClassNames.MODEL,
                                localModelPath);

        // Init preprocessor
        for (TensorInfo tensorInfo : modelInfo.getInputs()) {
            if (tensorInfo.isMetadataExisted()) {
                CodeBlockInjector preprocessorInjector =
                        InjectorUtils.getInputProcessorInjector(tensorInfo);
                preprocessorInjector.inject(constructorBuilder, tensorInfo);
            }
        }

        // Init associated file and postprocessor
        for (TensorInfo tensorInfo : modelInfo.getOutputs()) {
            if (tensorInfo.isMetadataExisted()) {
                CodeBlockInjector postprocessorInjector =
                        InjectorUtils.getOutputProcessorInjector(tensorInfo);
                postprocessorInjector.inject(constructorBuilder, tensorInfo);

                CodeBlockInjector codeBlockInjector = InjectorUtils.getAssociatedFileInjector();
                codeBlockInjector.inject(constructorBuilder, tensorInfo);
            }
        }

        classBuilder.addMethod(constructorBuilder.build());
    }

    private void buildProcessMethod(TypeSpec.Builder classBuilder) {
        TypeName outputType = ClassName.get(packageName, className).nestedClass(MlkitNames.OUTPUTS);
        String localOutputs = "outputs";

        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("process")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(ClassNames.NON_NULL)
                        .returns(outputType);
        List<String> byteBufferList = new ArrayList<>();
        for (TensorInfo tensorInfo : modelInfo.getInputs()) {
            String processedTypeName = CodeUtils.getProcessedTypeName(tensorInfo);
            ParameterSpec parameterSpec =
                    ParameterSpec.builder(
                                    CodeUtils.getParameterType(tensorInfo), tensorInfo.getName())
                            .addAnnotation(ClassNames.NON_NULL)
                            .build();
            methodBuilder.addParameter(parameterSpec);
            byteBufferList.add(processedTypeName + ".getBuffer()");
        }

        for (TensorInfo tensorInfo : modelInfo.getInputs()) {
            InjectorUtils.getProcessInjector(tensorInfo).inject(methodBuilder, tensorInfo);
        }
        methodBuilder.addStatement("$T $L = new $T(model)", outputType, localOutputs, outputType);
        methodBuilder.addStatement(
                "$L.run($L, $L.getBuffer())",
                FIELD_MODEL,
                CodeUtils.getObjectArrayString(byteBufferList.toArray(new String[0])),
                localOutputs);
        methodBuilder.addStatement("return $L", localOutputs);

        classBuilder.addMethod(methodBuilder.build());
    }

    private void buildStaticNewInstanceMethod(TypeSpec.Builder classBuilder) {
        TypeName returnType = ClassName.get(packageName, className);
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("newInstance")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(ClassNames.CONTEXT, "context")
                        .addException(ClassNames.IO_EXCEPTION)
                        .addAnnotation(ClassNames.NON_NULL)
                        .returns(returnType)
                        .addStatement("return new $T(context)", returnType);

        classBuilder.addMethod(methodBuilder.build());
    }
}
