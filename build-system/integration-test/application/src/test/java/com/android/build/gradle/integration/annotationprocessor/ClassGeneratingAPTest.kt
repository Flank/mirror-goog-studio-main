/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.BuildFileBuilder
import com.android.build.gradle.integration.common.fixture.app.JavaSourceFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.FileSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Tests that files are generated correctly when an annotation processor wants to generate some
 * files in the class output directory (not the source output directory)---see bug 139327207.
 */
@RunWith(FilterableParameterized::class)
class ClassGeneratingAPTest(withSeparateAP: Boolean) {

    companion object {

        @Parameterized.Parameters(name = "separateAP_{0}")
        @JvmStatic
        fun parameters() = arrayOf(true, false)

        private const val APP_MODULE = ":app"
        private const val ANNOTATIONS_MODULE = ":annotations"
        private const val PROCESSOR_MODULE = ":processor"

        private const val ANNOTATED_PACKAGE = "com.example.annotated"
        private const val SAMPLE_CLASS_WITH_ANNOTATION = "SampleClassWithAnnotation"

        private const val GENERATED_PACKAGE = "com.example.generated"
        private const val GENERATED_RESOURCE_FILE = "SampleGenerateResource.txt"

        private const val ANNOTATION_PACKAGE = "com.example.annotations"
        private const val SAMPLE_ANNOTATION = "SampleAnnotation"

        private const val PROCESSOR_PACKAGE = "com.example.processor"
        private const val CLASS_GENERATING_PROCESSOR = "ClassGeneratingProcessor"

        private const val CLEAN_TASK = "$APP_MODULE:clean"
        private const val COMPILE_TASK = "$APP_MODULE:compileDebugJavaWithJavac"
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(setUpTestProject())
        .addGradleProperties(
            "${BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING.propertyName}=$withSeparateAP"
        )
        .create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, setUpApp())
            .subproject(ANNOTATIONS_MODULE, setUpAnnotationLib())
            .subproject(PROCESSOR_MODULE, setUpProcessorLib())
            .build()
    }

    private fun setUpApp(): GradleProject {
        val app = MinimalSubProject.app("com.example.app")

        app.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = app.plugin
                compileSdkVersion = DEFAULT_COMPILE_SDK_VERSION
                addDependency("compileOnly", "project('$ANNOTATIONS_MODULE')")
                addDependency("annotationProcessor", "project('$PROCESSOR_MODULE')")
                build()
            }
        )

        // Add a class that has an annotation
        val packagePath = ANNOTATED_PACKAGE.replace('.', '/')
        app.withFile(
            "src/main/java/$packagePath/$SAMPLE_CLASS_WITH_ANNOTATION.java",
            with(JavaSourceFileBuilder(ANNOTATED_PACKAGE)) {
                addClass(
                    """
                    @$ANNOTATION_PACKAGE.$SAMPLE_ANNOTATION
                    public class $SAMPLE_CLASS_WITH_ANNOTATION {
                    }
                    """.trimIndent()
                )
                build()
            })

        return app
    }

    private fun setUpAnnotationLib(): MinimalSubProject {
        val annotationLib = MinimalSubProject.javaLibrary()

        val packagePath = ANNOTATION_PACKAGE.replace('.', '/')
        annotationLib.withFile(
            "src/main/java/$packagePath/$SAMPLE_ANNOTATION.java",
            with(JavaSourceFileBuilder(ANNOTATION_PACKAGE)) {
                addClass(
                    """
                    public @interface $SAMPLE_ANNOTATION {
                    }
                    """.trimIndent()
                )
                build()
            })

        return annotationLib
    }

    private fun setUpProcessorLib(): MinimalSubProject {
        val processorLib = MinimalSubProject.javaLibrary()

        processorLib.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = processorLib.plugin
                addDependency(dependency = "project('$ANNOTATIONS_MODULE')")
                build()
            }
        )

        val packagePath = PROCESSOR_PACKAGE.replace('.', '/')
        processorLib.withFile(
            "src/main/java/$packagePath/$CLASS_GENERATING_PROCESSOR.java",
            with(JavaSourceFileBuilder(PROCESSOR_PACKAGE)) {
                addClassGeneratingAnnotationProcessor(this)
                build()
            })

        processorLib.withFile(
            "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
            """
            $PROCESSOR_PACKAGE.$CLASS_GENERATING_PROCESSOR
            """.trimIndent()
        )

        return processorLib
    }

    /**
     * Adds an annotation processor that generates some files in the class output directory (not the
     * source output directory).
     */
    private fun addClassGeneratingAnnotationProcessor(builder: JavaSourceFileBuilder) {
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
            "javax.lang.model.element.TypeElement",
            "javax.tools.FileObject",
            "javax.tools.StandardLocation"
        )
        builder.addClass(
            """
            public class $CLASS_GENERATING_PROCESSOR extends AbstractProcessor {

                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    Set<String> set = new HashSet<>(1);
                    set.add("$ANNOTATION_PACKAGE.$SAMPLE_ANNOTATION");
                    return Collections.unmodifiableSet(set);
                }

                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    TypeElement annotation = processingEnv.getElementUtils().getTypeElement("$ANNOTATION_PACKAGE.$SAMPLE_ANNOTATION");
                    if (!annotations.contains(annotation)) {
                        return false;
                    }
                    try {
                        generateResource(annotation, roundEnv);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }

                private void generateResource(TypeElement annotation, RoundEnvironment roundEnv) throws IOException {
                    FileObject generatedFile = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "$GENERATED_PACKAGE", "$GENERATED_RESOURCE_FILE");
                    try (Writer writer = generatedFile.openWriter()) {
                        writer.write("Sample generated content");
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `check that files are generated in the class output directory`() {
        project.executor().run(CLEAN_TASK, COMPILE_TASK)

        val generatedResourceFile = File(
            "${JAVAC.getOutputDir(project.getSubproject(APP_MODULE).buildDir)}/debug/classes/" +
                    "${GENERATED_PACKAGE.replace('.', '/')}/$GENERATED_RESOURCE_FILE"
        )
        assertThat(generatedResourceFile).exists()
    }
}
