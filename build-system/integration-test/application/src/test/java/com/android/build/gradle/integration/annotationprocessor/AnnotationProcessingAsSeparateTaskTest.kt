/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.annotationprocessor

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TEST_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.BuildFileBuilder
import com.android.build.gradle.integration.common.fixture.app.JavaSourceFileBuilder
import com.android.build.gradle.integration.common.fixture.app.LayoutFileBuilder
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for annotation processing when it is done in a separate task from the compile
 * task.
 */
class AnnotationProcessingAsSeparateTaskTest {

    companion object {

        private const val APP_MODULE = ":app"
        private const val ANNOTATIONS_MODULE = ":annotations"
        private const val PROCESSOR_MODULE = ":processor"

        private const val ANNOTATION_PACKAGE = "com.example.annotations"
        private const val ANNOTATION_1 = "Annotation1"

        private const val PROCESSOR_PACKAGE = "com.example.processor"
        private const val PROCESSOR_1 = "Processor1"

        private const val MAIN_ACTIVITY_PACKAGE = "com.example.app"
        private const val MAIN_ACTIVITY = "MainActivity"

        private const val ANNOTATED_PACKAGE = "com.example.annotated"
        private const val ANNOTATED_CLASS_1 = "AnnotatedClass1"
        private const val ANNOTATED_CLASS_2 = "AnnotatedClass2"

        private const val NOT_ANNOTATED_PACKAGE = "com.example.notannotated"
        private const val NOT_ANNOTATED_CLASS_1 = "NotAnnotatedClass1"

        private const val GENERATED_PACKAGE = "com.example.generated"
        private const val GENERATED_CLASS_1 = "GeneratedClass1"
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(setUpTestProject()).create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, setUpApp())
            .subproject(ANNOTATIONS_MODULE, setUpAnnotationLib())
            .subproject(PROCESSOR_MODULE, setUpProcessorLib())
            .build()
    }

    private fun setUpApp(): MinimalSubProject {
        val packagePath = MAIN_ACTIVITY_PACKAGE.replace('.', '/')
        val layoutName = "activity_main"
        val helloTextId = "helloTextId"

        val app = MinimalSubProject.app(MAIN_ACTIVITY_PACKAGE)
        app.withFile(
            "src/main/res/layout/$layoutName.xml",
            with(LayoutFileBuilder()) {
                addTextView(helloTextId)
                build()
            }
        )
        app.withFile(
            "src/main/AndroidManifest.xml",
            with(ManifestFileBuilder(MAIN_ACTIVITY_PACKAGE)) {
                addApplicationTag(MAIN_ACTIVITY)
                build()
            })
        app.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = app.plugin
                compileSdkVersion = DEFAULT_COMPILE_SDK_VERSION
                minSdkVersion = "23"
                addDependency(
                    dependency = "'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'")
                addDependency(
                    dependency = "'com.android.support.constraint:constraint-layout:" +
                            "$TEST_CONSTRAINT_LAYOUT_VERSION'")
                addDependency("compileOnly", "project('$ANNOTATIONS_MODULE')")
                addDependency("annotationProcessor", "project('$PROCESSOR_MODULE')")
                build()
            }
        )

        // Add classes that have annotations
        val annotatedPackagePath = ANNOTATED_PACKAGE.replace('.', '/')
        app.withFile(
            "src/main/java/$annotatedPackagePath/$ANNOTATED_CLASS_1.java",
            with(JavaSourceFileBuilder(ANNOTATED_PACKAGE)) {
                addClass(
                    """
                    @$ANNOTATION_PACKAGE.$ANNOTATION_1
                    public class $ANNOTATED_CLASS_1 {
                    }
                    """.trimIndent()
                )
                build()
            })
        app.withFile(
            "src/main/java/$annotatedPackagePath/$ANNOTATED_CLASS_2.java",
            with(JavaSourceFileBuilder(ANNOTATED_PACKAGE)) {
                addClass(
                    """
                    @$ANNOTATION_PACKAGE.$ANNOTATION_1
                    public class $ANNOTATED_CLASS_2 {
                    }
                    """.trimIndent()
                )
                build()
            })

        // Add classes that do not have annotations
        val notAnnotatedPackagePath = NOT_ANNOTATED_PACKAGE.replace('.', '/')
        app.withFile(
            "src/main/java/$notAnnotatedPackagePath/$NOT_ANNOTATED_CLASS_1.java",
            with(JavaSourceFileBuilder(NOT_ANNOTATED_PACKAGE)) {
                addClass(
                    """
                    public class $NOT_ANNOTATED_CLASS_1 {
                    }
                    """.trimIndent()
                )
                build()
            })

        // Add the main activity class that references the generated class
        app.withFile(
            "src/main/java/$packagePath/$MAIN_ACTIVITY.java",
            with(JavaSourceFileBuilder(MAIN_ACTIVITY_PACKAGE)) {
                addImports("android.support.v7.app.AppCompatActivity", "android.os.Bundle")
                addClass(
                    """
                    public class $MAIN_ACTIVITY extends AppCompatActivity {

                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                            setContentView(R.layout.$layoutName);

                            android.widget.TextView textView = findViewById(R.id.$helloTextId);
                            String text = new $GENERATED_PACKAGE.$GENERATED_CLASS_1().toString();
                            textView.setText(text);
                        }
                    }
                    """.trimIndent()
                )
                build()
            })

        return app
    }

    private fun setUpAnnotationLib(): MinimalSubProject {
        val packagePath = ANNOTATION_PACKAGE.replace('.', '/')

        val annotationLib = MinimalSubProject.javaLibrary()
        annotationLib.withFile(
            "src/main/java/$packagePath/$ANNOTATION_1.java",
            with(JavaSourceFileBuilder(ANNOTATION_PACKAGE)) {
                addClass(
                    """
                    public @interface $ANNOTATION_1 {
                    }""".trimIndent()
                )
                build()
            })
        return annotationLib
    }

    private fun setUpProcessorLib(): MinimalSubProject {
        val packagePath = PROCESSOR_PACKAGE.replace('.', '/')

        val processorLib = MinimalSubProject.javaLibrary()
        processorLib.withFile(
            "src/main/java/$packagePath/$PROCESSOR_1.java",
            with(JavaSourceFileBuilder(PROCESSOR_PACKAGE)) {
                addAggregatingProcessorClass(
                    this,
                    PROCESSOR_1,
                    "$ANNOTATION_PACKAGE.$ANNOTATION_1",
                    GENERATED_PACKAGE,
                    GENERATED_CLASS_1
                )
                build()
            })
        processorLib.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = processorLib.plugin
                addDependency(dependency = "project('$ANNOTATIONS_MODULE')")
                build()
            }
        )
        processorLib.withFile(
            "src/main/resources/META-INF/gradle/incremental.annotation.processors",
            "$PROCESSOR_PACKAGE.$PROCESSOR_1,aggregating"
        )
        processorLib.withFile(
            "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
            "$PROCESSOR_PACKAGE.$PROCESSOR_1"
        )
        return processorLib
    }

    /**
     * Adds an aggregating annotation processor that generates a registry of all the classes
     * annotated with the given annotation.
     *
     * <p>See https://docs.gradle.org/current/userguide/java_plugin.html
     * #sec:incremental_annotation_processing
     */
    private fun addAggregatingProcessorClass(
        builder: JavaSourceFileBuilder,
        processorName: String,
        annotationFullName: String,
        generatedPackage: String,
        generatedClass: String
    ) {
        builder.addImports(
            "java.io.IOException",
            "java.io.UncheckedIOException",
            "java.io.Writer",
            "java.util.Collections",
            "java.util.HashSet",
            "java.util.Set",
            "javax.annotation.processing.AbstractProcessor",
            "javax.annotation.processing.RoundEnvironment",
            "javax.lang.model.SourceVersion",
            "javax.lang.model.element.Element",
            "javax.lang.model.element.TypeElement",
            "javax.tools.JavaFileObject"
        )
        builder.addClass(
            """
            public class $processorName extends AbstractProcessor {

                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    Set<String> set = new HashSet<>(1);
                    set.add($annotationFullName.class.getName());
                    return Collections.unmodifiableSet(set);
                }

                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    TypeElement annotation = processingEnv.getElementUtils().getTypeElement($annotationFullName.class.getName());
                    if (!annotations.contains(annotation)) {
                        return false;
                    }
                    try {
                        generateSourceCode(annotation, roundEnv);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }

                private void generateSourceCode(TypeElement annotation, RoundEnvironment roundEnv) throws IOException {
                    JavaFileObject generatedFile = processingEnv.getFiler().createSourceFile("$generatedPackage.$generatedClass");
                    try (Writer writer = generatedFile.openWriter()) {
                        writer.write("package com.example.generated;\n");
                        writer.write("\n");
                        writer.write("public class $generatedClass {\n");
                        writer.write("\n");
                        writer.write("\t@Override");
                        writer.write("\tpublic String toString() {");
                        writer.write("\n");
                        writer.write("\t\tStringBuilder greetings = new StringBuilder();\n");
                        writer.write("\t\tgreetings.append(\"Hello. This message comes from generated code! \");\n");
                        writer.write("\t\tgreetings.append(\"The following types are annotated with @" + $annotationFullName.class.getSimpleName()  + ": \");\n");

                        Set<? extends Element> annotatedTypes = roundEnv.getElementsAnnotatedWith(annotation);
                        for (Element annotateType : annotatedTypes) {
                           writer.write("\t\tgreetings.append(" + ((TypeElement) annotateType).getQualifiedName() + ".class.getName());\n");
                           writer.write("\t\tgreetings.append(\"; \");\n");
                        }

                        writer.write("\t\treturn greetings.toString();\n");
                        writer.write("\t}\n");
                        writer.write("}\n");
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test assemble task`() {
        project.executor().run("assembleDebug")
    }
}
