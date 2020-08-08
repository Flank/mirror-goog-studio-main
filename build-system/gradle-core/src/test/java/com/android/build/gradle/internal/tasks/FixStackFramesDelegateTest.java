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

import static com.android.testutils.truth.ZipFileSubject.assertThat;
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
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor;
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService;
import com.android.ide.common.resources.FileStatus;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.File;
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
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
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
            ImmutableSet.of(TestUtils.getPlatformFile("android.jar"));

    private WorkerExecutor executor;

    private AndroidVariantTask task;

    private Provider<ClassesHierarchyBuildService> classesHierarchyBuildServiceProvider;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File output;

    @Before
    public void setUp() throws IOException {
        output = tmp.newFolder("out");

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
    public void testFramesAreFixed() throws IOException, ClassNotFoundException {
        Path jar = getJarWithBrokenClasses("input.jar", ImmutableList.of("test/A"));

        Set<File> classesToFix = ImmutableSet.of(jar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(ANDROID_JAR, classesToFix, classesToFix, output);

        delegate.doFullRun(executor, task, classesHierarchyBuildServiceProvider);

        assertAllClassesAreValid(singleOutput().toPath());
    }

    @Test
    public void testIncrementalBuilds() throws IOException {
        Path jar = getJarWithBrokenClasses("input.jar", ImmutableList.of("test/A"));

        Set<File> classesToFix = ImmutableSet.of(jar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(ANDROID_JAR, classesToFix, classesToFix, output);

        delegate.doIncrementalRun(
                executor, ImmutableMap.of(), task, classesHierarchyBuildServiceProvider);

        assertThat(output.list()).named("output artifacts").hasLength(0);

        delegate.doIncrementalRun(
                executor,
                ImmutableMap.of(jar.toFile(), FileStatus.NEW),
                task,
                classesHierarchyBuildServiceProvider);

        assertThat(output.list()).named("output artifacts").hasLength(1);

        classesHierarchyBuildServiceProvider.get().close();
        FileUtils.delete(jar.toFile());
        delegate.doIncrementalRun(
                executor,
                ImmutableMap.of(jar.toFile(), FileStatus.REMOVED),
                task,
                classesHierarchyBuildServiceProvider);

        assertThat(output.list()).named("output artifacts").hasLength(0);
    }

    @Test
    public void testResolvingType() throws Exception {
        Path jar = getJarWithBrokenClasses("input.jar", ImmutableMap.of("test/A", "test/Base"));

        Set<File> classesToFix = ImmutableSet.of(jar.toFile());

        Path referencedJar = tmp.getRoot().toPath().resolve("ref_input.jar");
        TestInputsGenerator.jarWithEmptyClasses(referencedJar, ImmutableList.of("test/Base"));

        Set<File> referencedClasses = ImmutableSet.of(referencedJar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(ANDROID_JAR, classesToFix, referencedClasses, output);

        delegate.doFullRun(executor, task, classesHierarchyBuildServiceProvider);

        assertThat(output.list()).named("output artifacts").hasLength(1);
        assertAllClassesAreValid(singleOutput().toPath(), referencedJar);
    }

    @Test
    public void testUnresolvedType() throws Exception {
        Path jar = getJarWithBrokenClasses("input.jar", ImmutableMap.of("test/A", "test/Base"));

        Set<File> classesToFix = ImmutableSet.of(jar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(ANDROID_JAR, classesToFix, ImmutableSet.of(), output);

        delegate.doFullRun(executor, task, classesHierarchyBuildServiceProvider);

        assertThat(readZipEntry(jar, "test/A.class"))
                .isEqualTo(readZipEntry(singleOutput().toPath(), "test/A.class"));
    }

    @Test
    public void testOnlyClassesProcessed() throws Exception {
        Path jar = tmp.getRoot().toPath().resolve("input.jar");

        try (ZipOutputStream outputZip =
                new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(jar)))) {
            ZipEntry outEntry = new ZipEntry("LICENSE");
            byte[] newEntryContent = {0x10, 0x20, 0x30};
            CRC32 crc32 = new CRC32();
            crc32.update(newEntryContent);
            outEntry.setCrc(crc32.getValue());
            outEntry.setMethod(ZipEntry.STORED);
            outEntry.setSize(newEntryContent.length);
            outEntry.setCompressedSize(newEntryContent.length);

            outputZip.putNextEntry(outEntry);
            outputZip.write(newEntryContent);
            outputZip.closeEntry();
        }

        Set<File> classesToFix = ImmutableSet.of(jar.toFile());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(ANDROID_JAR, classesToFix, ImmutableSet.of(), output);

        delegate.doFullRun(executor, task, classesHierarchyBuildServiceProvider);

        try (Zip it = new Zip(singleOutput())) {
            assertThat(it).doesNotContain("LICENSE");
        }
    }

    @Test
    public void testNonIncrementalClearsOutput() throws IOException {
        Files.write(output.toPath().resolve("to-be-removed"), "".getBytes());

        FixStackFramesDelegate delegate =
                new FixStackFramesDelegate(
                        ANDROID_JAR, ImmutableSet.of(), ImmutableSet.of(), output);

        delegate.doFullRun(executor, task, classesHierarchyBuildServiceProvider);

        assertThat(output.list()).named("output artifacts").hasLength(0);
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

    @NonNull
    private static byte[] readZipEntry(@NonNull Path path, @NonNull String entryName)
            throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile());
                InputStream original = zip.getInputStream(new ZipEntry(entryName))) {
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

    @NonNull
    private Path getJarWithBrokenClasses(
            @NonNull String jarName, @NonNull Map<String, String> nameToSuperName)
            throws IOException {
        Path jar = tmp.getRoot().toPath().resolve(jarName);
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

    private File singleOutput() {
        File[] outputs = output.listFiles();
        assertThat(outputs).isNotNull();
        assertThat(outputs).hasLength(1);
        return outputs[0];
    }
}
