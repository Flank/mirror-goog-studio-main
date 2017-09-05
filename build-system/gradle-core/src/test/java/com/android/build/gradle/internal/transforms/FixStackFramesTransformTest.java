/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

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
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.builder.utils.FileCache;
import com.android.testutils.TestInputsGenerator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Testing stack frame fixing in {@link
 * com.android.build.gradle.internal.transforms.FixStackFramesTransform}.
 */
public class FixStackFramesTransformTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private TestTransformOutputProvider outputProvider;
    private Path output;

    @Before
    public void setUp() throws IOException {
        output = tmp.newFolder("out").toPath();
        outputProvider = new TestTransformOutputProvider(output);
    }

    @Test
    public void testFramesAreFixed()
            throws IOException, TransformException, InterruptedException, ClassNotFoundException {
        Path jar = getJarWithBrokenClasses("input", ImmutableList.of("test/A"));

        JarInput jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput input = TransformTestHelper.inputBuilder().addInput(jarInput).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .addReferenceInput(input)
                        .setTransformOutputProvider(outputProvider)
                        .build();

        FixStackFramesTransform transform =
                new FixStackFramesTransform(ImmutableList::of, "", null);
        transform.transform(invocation);

        assertAllClassesAreValid(output.resolve("input.jar"));
    }

    @Test
    public void testJarCaching()
            throws IOException, TransformException, InterruptedException, ClassNotFoundException {
        Path jar = getJarWithBrokenClasses("input", ImmutableList.of("test/A"));

        JarInput jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput input = TransformTestHelper.inputBuilder().addInput(jarInput).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .addReferenceInput(input)
                        .setTransformOutputProvider(outputProvider)
                        .build();

        FileCache cache = FileCache.getInstanceWithSingleProcessLocking(tmp.newFolder());
        FixStackFramesTransform transform =
                new FixStackFramesTransform(ImmutableList::of, "", cache);
        transform.transform(invocation);
        assertThat(cache.getCacheDirectory().list()).hasLength(1);

        transform.transform(invocation);
        assertThat(cache.getCacheDirectory().list()).hasLength(1);
    }

    @Test
    public void testIncrementalBuilds()
            throws IOException, TransformException, InterruptedException {
        Path jar = getJarWithBrokenClasses("input", ImmutableList.of("test/A"));

        JarInput jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setStatus(Status.NOTCHANGED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput input = TransformTestHelper.inputBuilder().addInput(jarInput).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .addReferenceInput(input)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();

        FixStackFramesTransform transform =
                new FixStackFramesTransform(ImmutableList::of, "", null);
        transform.transform(invocation);
        assertThat(output.toFile().list()).named("output artifacts").hasLength(0);

        jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setStatus(Status.ADDED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        input = TransformTestHelper.inputBuilder().addInput(jarInput).build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .addReferenceInput(input)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        transform.transform(invocation);
        assertThat(output.toFile().list()).named("output artifacts").hasLength(1);

        jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setStatus(Status.REMOVED)
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        input = TransformTestHelper.inputBuilder().addInput(jarInput).build();
        invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .addReferenceInput(input)
                        .setTransformOutputProvider(outputProvider)
                        .setIncremental(true)
                        .build();
        transform.transform(invocation);
        assertThat(output.toFile().list()).named("output artifacts").hasLength(0);
    }

    @Test
    public void testResolvingType() throws Exception {
        Path jar = getJarWithBrokenClasses("input", ImmutableMap.of("test/A", "test/Base"));
        JarInput jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput input = TransformTestHelper.inputBuilder().addInput(jarInput).build();

        Path referencedJar = tmp.getRoot().toPath().resolve("ref_input");
        TestInputsGenerator.jarWithEmptyClasses(referencedJar, ImmutableList.of("test/Base"));
        JarInput refJarInput =
                TransformTestHelper.jarBuilder(referencedJar.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput refInput = TransformTestHelper.inputBuilder().addInput(refJarInput).build();
        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .addReferenceInput(refInput)
                        .setTransformOutputProvider(outputProvider)
                        .build();

        FixStackFramesTransform transform =
                new FixStackFramesTransform(ImmutableList::of, "", null);
        transform.transform(invocation);

        assertThat(output.toFile().list()).named("output artifacts").hasLength(1);
        assertAllClassesAreValid(output.resolve("input.jar"), referencedJar);
    }

    @Test
    public void testUnresolvedType() throws Exception {
        Path jar = getJarWithBrokenClasses("input", ImmutableMap.of("test/A", "test/Base"));
        JarInput jarInput =
                TransformTestHelper.jarBuilder(jar.toFile())
                        .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .build();
        TransformInput input = TransformTestHelper.inputBuilder().addInput(jarInput).build();

        TransformInvocation invocation =
                TransformTestHelper.invocationBuilder()
                        .setInputs(ImmutableSet.of(input))
                        .setTransformOutputProvider(outputProvider)
                        .build();

        FixStackFramesTransform transform =
                new FixStackFramesTransform(ImmutableList::of, "", null);
        transform.transform(invocation);

        assertThat(readZipEntry(jar, "test/A.class"))
                .isEqualTo(readZipEntry(output.resolve("input.jar"), "test/A.class"));
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
}
