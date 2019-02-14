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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TEST_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.fixture.app.LayoutFileBuilder
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.FileSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val ANNOTATION_PACKAGE = "com.example.proguardannotation"
private const val ANNOTATION = "AddProguardRule"
private const val ANNOTATION_PROCESSOR = "${ANNOTATION}Processor"

private const val ANNOTAION_MODULE = ":annotation_proguard"

private const val MAIN_ACTIVITY_PACKAGE = "com.example.app"
private const val MAIN_ACTIVITY = "MainActivity"

private const val LIB_PACKAGE = "com.example.lib"

private const val LAYOUT_NAME = "layout_name"

private const val FAKE_CLASS_PATH = "my.imaginary.class"
private const val KEEP_RULE = "-keep class $FAKE_CLASS_PATH"

@RunWith(Parameterized::class)
class MergeGeneratedProguardFilesTest(
    annotateMainActivity: Boolean,
    annotateDependency: Boolean) {
    private val annotationPackagePath = ANNOTATION_PACKAGE.replace('.', '/')
    private val mainPackagePath = MAIN_ACTIVITY_PACKAGE.replace('.', '/')
    private val libPath = LIB_PACKAGE.replace('.', '/')

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "main annotated: {0}, library annotated: {1}")
        fun getConfigurations(): Collection<Array<Boolean>> =
            listOf(
                arrayOf(true, false),
                arrayOf(false, true)
            )
    }

    private val app =
        MinimalSubProject.app(MAIN_ACTIVITY_PACKAGE)
            .withFile(
                "src/main/res/layout/$LAYOUT_NAME.xml",
                with(LayoutFileBuilder()) {
                    build()
                }
            )
            .withFile(
                "src/main/AndroidManifest.xml",
                with(ManifestFileBuilder(MAIN_ACTIVITY_PACKAGE)) {
                    addApplicationTag(MAIN_ACTIVITY)
                    build()
                })
            .withFile(
                "src/main/java/$mainPackagePath/$MAIN_ACTIVITY.java",
                """
                package $MAIN_ACTIVITY_PACKAGE;
                import android.support.v7.app.AppCompatActivity;
                import android.os.Bundle;
                import $LIB_PACKAGE.Dummy;

                ${if(annotateMainActivity) "@$ANNOTATION_PACKAGE.$ANNOTATION" else ""}
                public class $MAIN_ACTIVITY extends AppCompatActivity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.$LAYOUT_NAME);
                        new Dummy();
                    }
                }
                """.trimIndent())
            .withFile(
                "build.gradle",
                """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}

                    defaultConfig {
                        minSdkVersion 23
                    }
                    buildTypes {
                        debug {
                            minifyEnabled true // Enable R8 for debug builds
                            proguardFiles android.getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
                        }
                    }
                }

                dependencies {
                    implementation 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
                    implementation 'com.android.support.constraint:constraint-layout:$TEST_CONSTRAINT_LAYOUT_VERSION'
                    implementation project(':lib')
                    ${if(annotateMainActivity) "compileOnly project('$ANNOTAION_MODULE')" else ""}
                    ${if(annotateMainActivity) "annotationProcessor project('$ANNOTAION_MODULE')" else ""}
                }
                """.trimIndent())
    private val proguardRuleGenerator =
        MinimalSubProject.javaLibrary()
            .withFile(
                "src/main/java/$annotationPackagePath/$ANNOTATION.java",
                """
                package $ANNOTATION_PACKAGE;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.SOURCE)
                @Target(ElementType.TYPE)
                public @interface $ANNOTATION {
                }
                """.trimIndent())
            .withFile(
                "src/main/java/$annotationPackagePath/$ANNOTATION_PROCESSOR.java",
                """
                package $ANNOTATION_PACKAGE;

                import static javax.tools.StandardLocation.CLASS_OUTPUT;

                import java.io.IOException;
                import java.io.Writer;
                import java.util.Collections;
                import java.util.HashSet;
                import java.util.Set;
                import javax.annotation.processing.AbstractProcessor;
                import javax.annotation.processing.RoundEnvironment;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.TypeElement;

                public class $ANNOTATION_PROCESSOR extends AbstractProcessor {

                    @Override
                    public Set<String> getSupportedAnnotationTypes() {
                        Set<String> set = new HashSet<>(1);
                        set.add($ANNOTATION.class.getCanonicalName());
                        return Collections.unmodifiableSet(set);
                    }

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        try (Writer writer = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", "META-INF/proguard/proguard.txt").openWriter()) {
                            writer.write("$KEEP_RULE");
                        } catch (IOException e) {
                        }
                        return false;
                    }
                }
                """.trimIndent())
            .withFile(
                "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
                """
                $ANNOTATION_PACKAGE.$ANNOTATION_PROCESSOR
                """.trimIndent())
    val lib =
        MinimalSubProject.lib(LIB_PACKAGE)
            .withFile(
                "src/main/java/$libPath/Dummy.java",
                """
                package $LIB_PACKAGE;

                ${if(annotateDependency) "@$ANNOTATION_PACKAGE.$ANNOTATION" else ""}
                public class Dummy {
                }
                """.trimIndent())
            .appendToBuild(
                """
                dependencies {
                    ${if(annotateDependency) "compileOnly project('$ANNOTAION_MODULE')" else ""}
                    ${if(annotateDependency) "annotationProcessor project('$ANNOTAION_MODULE')" else ""}
                }
                """)

    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(ANNOTAION_MODULE, proguardRuleGenerator)
                .subproject(":lib", lib)
                .build()
        ).create()

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testGeneratedProGuardFilesPropagatedToR8() {

        val configFile = tmp.newFile()

        project.getSubproject("app")
            .file("proguard-rules.pro")
            .writeText("-printconfiguration ${configFile.absolutePath}")

        project.execute("clean", ":app:assembleDebug")

        assertThat(configFile).contains(KEEP_RULE)
    }
}