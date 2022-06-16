/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.api

import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Tests that run both an old deprecated transform and a new variant API classes access and ensure
 * that both are compatible with each other.
 *
 * The classes access runs first and adds a file to the compiled classes.
 * The transform runs later and also adds a file to the classes streams.
 *
 * The test verifies that both classes are present in the resulting APK/AAR files.
 */
@RunWith(Parameterized::class)
class TransformsAndClassesAccessTest(val plugin: String) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun plugin() = listOf("com.android.application", "com.android.library")
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(KotlinHelloWorldApp.forPlugin(plugin))
        .create()

    @Test
    fun testTransformsAndClassesAccessAreCompatible() {

        File(project.projectDir, "buildSrc").also { buildSrc ->
            File(buildSrc, "src/main/java/com/example/test/customPlugin").also { pluginSrc ->
                pluginSrc.mkdirs()
                File(pluginSrc, "CustomPlugin.java").writeText(
                    // language=Java
                    """
                    package com.example.test.customplugin;
                    import com.android.build.api.transform.Transform;
                    import java.lang.reflect.Method;
                    import org.gradle.api.Plugin;
                    import org.gradle.api.Project;
                    public class CustomPlugin implements Plugin<Project> {
                        @Override
                        public void apply(final Project target) {
                            Object androidExtension = target.getExtensions().findByName("android");
                            try {
                                Method registerTransform =
                                        androidExtension
                                                .getClass()
                                                .getMethod(
                                                    "registerTransform",
                                                     Transform.class,
                                                      Object[].class);
                                registerTransform.invoke(
                                    androidExtension,
                                     new CustomTransform(),
                                      new Object[] {}
                                );
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    """.trimIndent()
                )
                File(pluginSrc, "CustomTransform.java").writeText(
                    // language=Java
                    """
                    package com.example.test.customplugin;
                    import com.android.build.api.transform.Format;
                    import com.android.build.api.transform.JarInput;
                    import com.android.build.api.transform.QualifiedContent;
                    import com.android.build.api.transform.Transform;
                    import com.android.build.api.transform.TransformException;
                    import com.android.build.api.transform.TransformInvocation;
                    import java.io.File;
                    import java.io.IOException;
                    import java.io.InputStream;
                    import java.nio.file.FileVisitResult;
                    import java.nio.file.Files;
                    import java.nio.file.Path;
                    import java.nio.file.SimpleFileVisitor;
                    import java.nio.file.StandardCopyOption;
                    import java.nio.file.attribute.BasicFileAttributes;
                    import java.util.Set;
                    import java.util.concurrent.atomic.AtomicBoolean;
                    public class CustomTransform extends Transform {
                        public String getName() {
                            return "testTransform";
                        }
                        AtomicBoolean done = new AtomicBoolean(false);
                        public Set<QualifiedContent.ContentType> getInputTypes() {
                            return Set.of(QualifiedContent.DefaultContentType.CLASSES);
                        }
                        public Set<? super QualifiedContent.Scope> getScopes() {
                            return Set.of(QualifiedContent.Scope.PROJECT);
                        }
                        public boolean isIncremental() {
                            return true;
                        }
                        public void transform(TransformInvocation transformInvocation)
                                throws TransformException, InterruptedException, IOException {
                            System.out.println("IN TRANSFORM");
                            File outputDir =
                                    transformInvocation
                                            .getOutputProvider()
                                            .getContentLocation("main", getInputTypes(), getScopes(), Format.DIRECTORY);
                            outputDir.mkdirs();
                            Path outputPath = outputDir.toPath();
                            transformInvocation.getInputs().forEach(input -> {
                                for (JarInput jarInput : input.getJarInputs()) {
                                    Path inputJar = jarInput.getFile().toPath();
                                    Path outputJar =
                                            transformInvocation.getOutputProvider()
                                                    .getContentLocation(
                                                            jarInput.getName(),
                                                            jarInput.getContentTypes(),
                                                            jarInput.getScopes(),
                                                            Format.JAR)
                                                    .toPath();
                                    try {
                                        Files.createDirectories(outputJar.getParent());
                                        Files.copy(inputJar, outputJar);
                                    } catch(IOException ioe) {
                                        throw new RuntimeException(ioe);
                                    }
                                }
                            });
                            transformInvocation
                                    .getInputs()
                                    .stream()
                                    .flatMap(transformInput -> transformInput.getDirectoryInputs().stream())
                                    .forEach(
                                            directoryInput -> {
                                                Path sourcePath = directoryInput.getFile().toPath();
                                                try {
                                                    Files.walkFileTree(
                                                            directoryInput.getFile().toPath(),
                                                            new SimpleFileVisitor<Path>() {
                                                                public FileVisitResult visitFile(
                                                                        Path file, BasicFileAttributes attrs)
                                                                        throws IOException {
                                                                    Path relative = sourcePath.relativize(file);
                                                                    Path outputFilePath = outputPath.resolve(relative);
                                                                    outputFilePath.toFile().getParentFile().mkdirs();
                                                                    Files.copy(
                                                                            file,
                                                                            outputFilePath,
                                                                            StandardCopyOption.REPLACE_EXISTING);
                                                                    return FileVisitResult.CONTINUE;
                                                                }
                                                            });
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            });
                            // now copy the marker class to "mark" the library, only one to avoid adding the same file
                            // several times.
                            if (done.get()) {
                                return;
                            }
                            System.out.println("WRITING THE FILE");
                            done.set(true);
                            try (InputStream classBytes =
                                    getClass().getClassLoader().getResourceAsStream("Hello.class")) {
                                Files.copy(
                                        classBytes,
                                        new File(outputDir, "Hello.class").toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    """.trimIndent()
                )
            }
            File(buildSrc, "build.gradle").writeText(
                """
                plugins {
                    id 'java-library'
                }

                def commonScriptFolder = buildscript.sourceFile.parentFile.parentFile.parent
                apply from: "${'$'}commonScriptFolder/commonVersions.gradle", to: rootProject.ext
                apply from: '../../commonLocalRepo.gradle'

                dependencies {
                    compileOnly gradleApi()
                    implementation 'com.android.tools.build:gradle-api:' + rootProject.buildVersion
                    runtimeOnly 'com.android.tools.build:gradle:' + rootProject.buildVersion
                }
                """.trimIndent()
            )
            File(buildSrc, "src/main/java").also { javaSrc ->
                File(javaSrc, "Hello.java").writeText(
                    """
                        class Hello {}
                    """.trimIndent()
                )
            }
        }

        project.buildFile.writeText(
            """
            apply from: "../commonHeader.gradle"
            apply from: '../commonLocalRepo.gradle'
            buildscript { apply from: "../commonBuildScript.gradle" }

            buildscript {
                dependencies {
                    classpath("org.javassist:javassist:3.26.0-GA")
                }
            }
            apply plugin: '$plugin'
            apply plugin: com.example.test.customplugin.CustomPlugin

            android {
              namespace "com.android.tests.basic"
              compileSdkVersion rootProject.latestCompileSdk
              defaultConfig {
                minSdkVersion rootProject.latestCompileSdk
              }
            }

            dependencies {
                   api("com.google.code.gson:gson:2.8.6")
                   implementation("org.javassist:javassist:3.26.0-GA")
            }

            import com.android.build.api.artifact.ScopedArtifact
            import com.android.build.api.artifact.SingleArtifact
            import com.android.build.api.variant.ScopedArtifacts.Scope

            ${addCustomizationTask()}

            androidComponents {

                onVariants(selector().all(), { variant ->
                    ${registerCustomizationTask(ScopedArtifacts.Scope.PROJECT)}
                })
            }
            """.trimIndent())
        project.executor()
            .with(BooleanOption.LEGACY_TRANSFORM_TASK_FORCE_NON_INCREMENTAL, true)
            .run("assembleDebug")

        if (plugin.equals("com.android.application")) {
            val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
            TruthHelper.assertThatApk(apk)
                .containsClass("Lcom/android/api/tests/PROJECTInterface;");
            TruthHelper.assertThatApk(apk)
                .containsClass("LHello;");
        } else {
            project.getAar("debug") {aar ->
                TruthHelper.assertThatAar(aar)
                    .containsClass("Lcom/android/api/tests/PROJECTInterface;");
                TruthHelper.assertThatAar(aar)
                    .containsClass("LHello;");
            }
        }
    }

    private fun registerCustomizationTask(
        forScope: ScopedArtifacts.Scope,
        classNameToGenerate: String = "${forScope.name}Interface"
    ) =
        """
        TaskProvider ${forScope.name.lowercase()}ScopedTask = project.tasks.register(variant.getName() + "Modify${forScope.name}Classes", ModifyClassesTask.class) { task ->
            task.getClassNameToGenerate().set("$classNameToGenerate")
        }

        variant
            .artifacts
            .forScope(Scope.${forScope.name})
            .use(${forScope.name.lowercase()}ScopedTask)
            .toTransform(
                ScopedArtifact.CLASSES.INSTANCE,
                ModifyClassesTask::getInputJars,
                ModifyClassesTask::getInputDirectories,
                ModifyClassesTask::getOutputClasses
            )
        """

    private fun addCustomizationTask() =
        """
        import javassist.ClassPool
        import javassist.CtClass
        import java.util.jar.*
        import java.nio.file.Files
        import java.nio.file.Paths

        abstract class ModifyClassesTask extends DefaultTask {
            @OutputFile
            abstract RegularFileProperty getOutputClasses()

            @InputFiles
            abstract ListProperty<RegularFile> getInputJars()

            @InputFiles
            abstract ListProperty<Directory> getInputDirectories()

            @Input
            abstract Property<String> getClassNameToGenerate()

            @TaskAction
            void taskAction() {

                ClassPool pool = new ClassPool(ClassPool.getDefault())
                JarOutputStream jarOutput = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(
                    getOutputClasses().get().getAsFile()
                )));
                getInputJars().get().each { regularFile ->
                    System.out.println("In jar handling " + regularFile)
                    new JarFile(regularFile.getAsFile()).withCloseable { jarFile ->
                        Enumeration<JarEntry> jarEntries = jarFile.entries();
                        while(jarEntries.hasMoreElements()) {
                            JarEntry jarEntry = jarEntries.nextElement();
                            if (!jarEntry.getName().startsWith("META-INF/MANIFEST")  && !jarEntry.isDirectory()) {\
                                jarOutput.putNextEntry(new JarEntry(jarEntry.getName()))
                                jarFile.getInputStream(jarEntry).withCloseable { is ->
                                    is.transferTo(jarOutput)
                                }
                                jarOutput.closeEntry()
                            }
                        }
                    }
                }
                getInputDirectories().get().each { directory ->
                    Files.walk(directory.getAsFile().toPath()).each { source ->
                        if (source.toFile().isFile()) {
                            String fileName = directory.getAsFile().toPath().relativize(source)
                                .toString()
                                .replace(File.separatorChar, '/' as char)
                            println("Handling " + fileName)
                            jarOutput.putNextEntry(new JarEntry(fileName))
                            (new FileInputStream(source.toFile())).withCloseable { is ->
                                is.transferTo(jarOutput)
                            }
                            jarOutput.closeEntry()
                        }
                    }
                }
                CtClass interfaceClass = pool.makeInterface("com.android.api.tests." + getClassNameToGenerate().get());
                println("Adding ${'$'}interfaceClass")
                jarOutput.putNextEntry(new JarEntry("com/android/api/tests/" + getClassNameToGenerate().get() + ".class"))
                jarOutput.write(interfaceClass.toBytecode())
                jarOutput.closeEntry()

                jarOutput.close()
            }
        }
        """.trimIndent()
}
