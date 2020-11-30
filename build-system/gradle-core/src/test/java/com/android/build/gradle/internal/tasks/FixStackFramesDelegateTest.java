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

package com.android.build.gradle.internal.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.INTEGER;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.fixtures.ExecutionMode;
import com.android.build.gradle.internal.fixtures.FakeFileChange;
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor;
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.work.ChangeType;
import org.gradle.workers.WorkerExecutor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Testing stack frame fixing in {@link FixStackFramesDelegate}. */
public class FixStackFramesDelegateTest {

    private static final Set<File> ANDROID_JAR =
            ImmutableSet.of(TestUtils.resolvePlatformPath("android.jar").toFile());

    private WorkerExecutor executor;

    private AndroidVariantTask task;

    private Provider<ClassesHierarchyBuildService> classesHierarchyBuildServiceProvider;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File jarsInputDir;

    private File classesInputDir;

    private File jarsOutputDir;

    private File classesOutputDir;

    @Before
    public void setUp() throws IOException {
        jarsInputDir = tmp.newFolder("jar-in");
        classesInputDir = tmp.newFolder("classes-in");
        jarsOutputDir = tmp.newFolder("jar-out");
        classesOutputDir = tmp.newFolder("classes-out");

        Project project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build();
        ObjectFactory objectFactory = project.getObjects();
        task = project.getTasks().register("taskName", AndroidVariantTask.class).get();
        task.getAnalyticsService().set(new FakeNoOpAnalyticsService());
        task.setVariantName("test");
        executor =
                new FakeGradleWorkExecutor(
                        objectFactory,
                        tmp.newFolder(),
                        Collections.emptyList(),
                        ExecutionMode.RUNNING);
        new ClassesHierarchyBuildService.RegistrationAction(project).execute();
        classesHierarchyBuildServiceProvider =
                BuildServicesKt.getBuildService(
                        project.getGradle().getSharedServices(),
                        ClassesHierarchyBuildService.class);
    }

    @Test
    public void testFramesAreFixedForJarClasses() throws IOException, ClassNotFoundException {
        getJarWithBrokenClasses("input.jar", ImmutableList.of("test/A"));

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        ImmutableSet.of(),
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doFullRun();
        assertAllClassesAreValid(singleJar().toPath());
    }

    @Test
    public void testFramesAreFixedForDirClasses() throws IOException, ClassNotFoundException {
        addBrokenClassesToDir(ImmutableList.of("test/A"));

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        ImmutableSet.of(),
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doFullRun();
        assertAllClassesInDirAreValid(classesOutputDir, ImmutableList.of("test.A"));
    }

    @Test
    public void testIncrementalBuildsForJarClasses() throws IOException {
        Path inputFile = getJarWithBrokenClasses("input.jar", ImmutableList.of("test/A"));

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        ImmutableSet.of(),
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doIncrementalRun(ImmutableList.of(), ImmutableList.of());

        assertThat(jarsOutputDir.list()).named("output artifacts").hasLength(0);
        delegate.doIncrementalRun(
                ImmutableList.of(new FakeFileChange(inputFile.toFile(), ChangeType.ADDED)),
                ImmutableList.of());
        assertThat(jarsOutputDir.list()).named("output artifacts").hasLength(1);

        classesHierarchyBuildServiceProvider.get().close();
        FileUtils.delete(inputFile.toFile());

        delegate.doIncrementalRun(
                ImmutableList.of(new FakeFileChange(inputFile.toFile(), ChangeType.REMOVED)),
                ImmutableList.of());
        assertThat(jarsOutputDir.list()).named("output artifacts").hasLength(0);
    }

    @Test
    public void testIncrementalBuildsForDirClasses() throws IOException {
        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        ImmutableSet.of(),
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doIncrementalRun(ImmutableList.of(), ImmutableList.of());

        assertThat(classesOutputDir.toPath().resolve("test/A.class").toFile().exists()).isFalse();

        addBrokenClassesToDir(ImmutableList.of("test/A"));
        File inputFile = FileUtils.join(classesInputDir, "test", "A.class");

        delegate.doIncrementalRun(
                ImmutableList.of(),
                ImmutableList.of(new FakeFileChange(inputFile, ChangeType.ADDED)));
        assertThat(classesOutputDir.toPath().resolve("test/A.class").toFile().exists()).isTrue();

        classesHierarchyBuildServiceProvider.get().close();
        FileUtils.delete(inputFile);

        delegate.doIncrementalRun(
                ImmutableList.of(),
                ImmutableList.of(new FakeFileChange(inputFile, ChangeType.REMOVED)));
        assertThat(classesOutputDir.toPath().resolve("test/A.class").toFile().exists()).isFalse();
    }

    @Test
    public void testResolvingTypeForJarClasses() throws Exception {
        getJarWithBrokenClasses("input.jar", ImmutableMap.of("test/A", "test/Base"));

        Path referencedJar = tmp.getRoot().toPath().resolve("ref_input.jar");
        TestInputsGenerator.jarWithEmptyClasses(referencedJar, ImmutableList.of("test/Base"));

        Set<File> referencedClasses = ImmutableSet.of(referencedJar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        referencedClasses,
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doFullRun();

        assertAllClassesAreValid(singleJar().toPath(), referencedJar);
    }

    @Test
    public void testResolvingTypeForDirClasses() throws Exception {
        addBrokenClassesToDir(ImmutableMap.of("test/A", "test/Base"));

        Path referencedJar = tmp.getRoot().toPath().resolve("ref_input.jar");
        TestInputsGenerator.jarWithEmptyClasses(referencedJar, ImmutableList.of("test/Base"));

        Set<File> referencedClasses = ImmutableSet.of(referencedJar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        referencedClasses,
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doFullRun();

        assertAllClassesInDirAreValid(classesOutputDir, ImmutableList.of("test.A"), referencedJar);
    }

    @Test
    public void testNonIncrementalClearsOutputForJarClasses() throws IOException {
        Files.write(jarsOutputDir.toPath().resolve("to-be-removed"), "".getBytes());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        ImmutableSet.of(),
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doFullRun();

        assertThat(jarsOutputDir.list()).named("output artifacts").hasLength(0);
    }

    @Test
    public void testNonIncrementalClearsOutputForDirClasses() throws IOException {
        Files.write(classesOutputDir.toPath().resolve("to-be-removed"), "".getBytes());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        classesInputDir,
                        jarsInputDir,
                        ANDROID_JAR,
                        ImmutableSet.of(),
                        classesOutputDir,
                        jarsOutputDir,
                        executor,
                        task,
                        classesHierarchyBuildServiceProvider);

        delegate.doFullRun();
        assertThat(classesOutputDir.list()).named("output artifacts").hasLength(0);
    }

    /** Loads all classes from jars, to make sure no VerifyError is thrown. */
    private static void assertAllClassesAreValid(@NonNull Path... jars)
            throws IOException, ClassNotFoundException {
        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            urls[i] = jars[i].toUri().toURL();
        }
        for (Path jar : jars) {
            try (ZipFile zipFile = new ZipFile(jar.toFile());
                    URLClassLoader classLoader = new URLClassLoader(urls, null)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();

                    String className =
                            e.getName()
                                    .substring(
                                            0,
                                            e.getName().length() - SdkConstants.DOT_CLASS.length())
                                    .replace('/', '.');
                    Class.forName(className, true, classLoader);
                }
            } catch (VerifyError e) {
                fail("Failed to fix broken stack frames. " + e.getMessage());
            }
        }
    }

    private static void assertAllClassesInDirAreValid(
            @NonNull File dir, @NonNull List<String> classes, @NonNull Path... jars)
            throws IOException, ClassNotFoundException {
        URL[] urls = new URL[jars.length + 1];
        urls[0] = dir.toURI().toURL();
        for (int i = 0; i < jars.length; i++) {
            urls[i + 1] = jars[i].toUri().toURL();
        }
        try (URLClassLoader classLoader = new URLClassLoader(urls, null)) {
            for (String className : classes) {
                Class.forName(className, true, classLoader);
            }
        } catch (VerifyError e) {
            fail("Failed to fix broken stack frames. " + e.getMessage());
        }
    }

    @NonNull
    private static byte[] readZipEntry(@NonNull Path path, @NonNull String entryName)
            throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile());
                InputStream original = zip.getInputStream(new ZipEntry(entryName))) {
            return ByteStreams.toByteArray(original);
        }
    }

    @NonNull
    private static byte[] readFile(@NonNull Path path) throws IOException {
        try (InputStream original = new FileInputStream(path.toFile())) {
            return ByteStreams.toByteArray(original);
        }
    }

    @NonNull
    private Path getJarWithBrokenClasses(@NonNull String jarName, @NonNull List<String> classes)
            throws IOException {
        return getJarWithBrokenClasses(
                jarName,
                classes.stream().collect(Collectors.toMap(c -> c, c -> "java/lang/Object")));
    }

    private void addBrokenClassesToDir(@NonNull List<String> classes) {
        addBrokenClassesToDir(
                classes.stream().collect(Collectors.toMap(c -> c, c -> "java/lang/Object")));
    }

    private void addBrokenClassesToDir(@NonNull Map<String, String> nameToSuperName) {
        nameToSuperName.forEach(
                (name, superName) -> {
                    Path classFile =
                            classesInputDir.toPath().resolve(name + SdkConstants.DOT_CLASS);
                    classFile.toFile().getParentFile().mkdirs();
                    try {
                        Files.write(classFile, getBrokenFramesClass(name, superName));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @NonNull
    private Path getJarWithBrokenClasses(
            @NonNull String jarName, @NonNull Map<String, String> nameToSuperName)
            throws IOException {
        Path jar = new File(jarsInputDir, jarName).toPath();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> c : nameToSuperName.entrySet()) {
                ZipEntry entry = new ZipEntry(c.getKey() + SdkConstants.DOT_CLASS);
                out.putNextEntry(entry);
                out.write(getBrokenFramesClass(c.getKey(), c.getValue()));
                out.closeEntry();
            }
        }
        return jar;
    }

    @NonNull
    private static byte[] getBrokenFramesClass(@NonNull String name, @NonNull String superName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, name, null, superName, null);

        // Code below is (with manually broken stack map):
        // public class name extends superName {
        //    public void foo() {
        //        Object i= null;
        //        if (i == null) {
        //            i = new superName();
        //        } else {
        //            i = new name();
        //        }
        //    }
        // }
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "foo", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ASTORE, 1);
        mv.visitVarInsn(ALOAD, 1);
        Label l0 = new Label();
        mv.visitJumpInsn(IFNONNULL, l0);
        mv.visitTypeInsn(NEW, superName);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, 1);
        Label l1 = new Label();
        mv.visitJumpInsn(GOTO, l1);
        mv.visitLabel(l0);
        // we broke the frame here, new Object[] {superName} -> new Object[] {INTEGER}
        mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {INTEGER}, 0, null);
        mv.visitTypeInsn(NEW, name);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V", false);
        mv.visitVarInsn(ASTORE, 1);
        mv.visitLabel(l1);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private File singleJar() {
        File[] outputs = jarsOutputDir.listFiles();
        assertThat(outputs).isNotNull();
        assertThat(outputs).hasLength(1);
        return outputs[0];
    }
}
