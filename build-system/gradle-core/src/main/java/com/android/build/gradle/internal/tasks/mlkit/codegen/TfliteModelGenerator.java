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
import com.android.tools.mlkit.MetadataExtractor;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.ModelParsingException;
import com.android.tools.mlkit.TensorInfo;
import com.google.common.base.CaseFormat;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.lang.model.element.Modifier;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/** Generator to generate code for tflite model */
public class TfliteModelGenerator implements ModelGenerator {

    private static final String FIELD_MODEL = "model";
    private static final String FIELD_METADATA_EXTRACTOR = "extractor";

    private final Logger logger;
    private final String localModelPath;
    private final MetadataExtractor extractor;
    private final ModelInfo modelInfo;
    private final String className;
    private final String packageName;

    public TfliteModelGenerator(File modelFile, String packageName, String localModelPath)
            throws ModelParsingException {
        this.extractor = ModelUtils.createMetadataExtractor(modelFile);
        this.localModelPath = localModelPath;
        this.modelInfo = ModelInfo.buildFrom(extractor);
        this.packageName = packageName;
        this.logger = Logging.getLogger(this.getClass());
        className =
                CaseFormat.LOWER_UNDERSCORE.to(
                        CaseFormat.UPPER_CAMEL, FilenameUtils.removeExtension(modelFile.getName()));
    }

    @Override
    public void generateBuildClass(DirectoryProperty outputDirProperty) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addJavadoc(modelInfo.getModelDescription());
        buildFields(classBuilder);
        buildConstructor(classBuilder);
        buildCreateInputsMethod(classBuilder);
        buildGetAssociatedFileMethod(classBuilder);
        buildRunMethod(classBuilder);
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
                        .build();
        classBuilder.addField(model);
    }

    private void buildGetAssociatedFileMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("getAssociatedFile")
                        .addParameter(ClassNames.CONTEXT, "context")
                        .addParameter(String.class, "fileName")
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
        InjectorUtils.getInputsClassInjector().inject(classBuilder, modelInfo.getInputs());
    }

    private void buildConstructor(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder constructorBuilder =
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassNames.CONTEXT, "context")
                        .addException(ClassNames.IO_EXCEPTION)
                        .addStatement(
                                "$L = new $T.Builder(context, $S).build()",
                                FIELD_MODEL,
                                ClassNames.MODEL,
                                localModelPath);

        // Init preprocessor
        for (TensorInfo tensorInfo : modelInfo.getInputs()) {
            CodeBlockInjector preprocessorInjector =
                    InjectorUtils.getInputProcessorInjector(tensorInfo);
            preprocessorInjector.inject(constructorBuilder, tensorInfo);
        }

        // Init associated file and postprocessor
        for (TensorInfo tensorInfo : modelInfo.getOutputs()) {
            CodeBlockInjector postprocessorInjector =
                    InjectorUtils.getOutputProcessorInjector(tensorInfo);
            postprocessorInjector.inject(constructorBuilder, tensorInfo);

            CodeBlockInjector codeBlockInjector = InjectorUtils.getAssociatedFileInjector();
            codeBlockInjector.inject(constructorBuilder, tensorInfo);
        }

        classBuilder.addMethod(constructorBuilder.build());
    }

    private void buildRunMethod(TypeSpec.Builder classBuilder) {
        TypeName outputType = ClassName.get(packageName, className).nestedClass(MlkitNames.OUTPUTS);
        TypeName inputType = ClassName.get(packageName, className).nestedClass(MlkitNames.INPUTS);
        String localInputs = "inputs";
        String localOutputs = "outputs";

        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("run")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(inputType, localInputs)
                        .returns(outputType);

        methodBuilder.addStatement("$T $L = new $T()", outputType, localOutputs, outputType);
        methodBuilder.addStatement(
                "$L.run($L.getBuffer(), $L.getBuffer())", FIELD_MODEL, localInputs, localOutputs);
        methodBuilder.addStatement("return $L", localOutputs);

        classBuilder.addMethod(methodBuilder.build());
    }

    private void buildCreateInputsMethod(TypeSpec.Builder classBuilder) {
        TypeName inputType = ClassName.get(packageName, className).nestedClass(MlkitNames.INPUTS);
        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("createInputs")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(inputType)
                        .addStatement("return new $L()", MlkitNames.INPUTS);

        classBuilder.addMethod(methodBuilder.build());
    }
}
