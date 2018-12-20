/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.MoreTruth.assertThatZip;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * test for packaging of asset files.
 */
public class NativeSoPackagingFromJarTest {
    private static final String LIB_X86_LIBHELLO_SO = "lib/x86/libhello.so";
    private static final String COM_FOO_FOO_CLASS = "com/foo/Foo.class";

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private static GradleTestProject appProject;
    private static GradleTestProject libProject;


    @BeforeClass
    public static void setUp() throws Exception {
        appProject = project.getSubproject("app");

        // rewrite settings.gradle to remove un-needed modules
        Files.asCharSink(new File(project.getTestDir(), "settings.gradle"), Charsets.UTF_8)
                .write("include \"app\"\ninclude \"library\"\n");

        // setup dependencies.
        TestFileUtils.appendToFile(appProject.getBuildFile(),
"\ndependencies {\n" +
"  compile files(\"libs/foo.jar\")\n" +
"}\n");

        libProject = project.getSubproject("library");

        TestFileUtils.appendToFile(libProject.getBuildFile(),
"\ndependencies {\n" +
"  compile files(\"libs/bar.jar\")\n" +
"}\n");


        File appDir = appProject.getTestDir();
        createJarWithNativeLib(new File(appDir, "libs"), "foo.jar", false);

        File libDir = libProject.getTestDir();
        createJarWithNativeLib(new File(libDir, "libs"), "bar.jar", true);
    }

    private static void createJarWithNativeLib(
            @NonNull File folder, @NonNull String fileName, boolean includeClass) throws Exception {
        FileUtils.mkdirs(folder);
        File jarFile = new File(folder, fileName);

        try (FileOutputStream fos = new FileOutputStream(jarFile);
                JarOutputStream jarOutputStream = new JarOutputStream(
                        new BufferedOutputStream(fos))) {
            jarOutputStream.putNextEntry(new JarEntry(LIB_X86_LIBHELLO_SO));
            jarOutputStream.write("hello".getBytes());
            jarOutputStream.closeEntry();

            if (includeClass) {
                jarOutputStream.putNextEntry(new JarEntry(COM_FOO_FOO_CLASS));
                jarOutputStream.write(getDummyClassByteCode());
                jarOutputStream.closeEntry();
            }
        }
    }

    @Test
    public void testAppPackaging() throws Exception {
        project.executor().run("app:assembleDebug");
        checkApk(appProject, "libhello.so", "hello");
    }

    @Test
    public void testLibraryPackaging() throws Exception {
        project.executor().run("library:assembleDebug");
        checkAar(libProject, "libhello.so", "hello");

        // also check that the bar.jar is also present as a local jar with a the class
        // but not the so file.
        // first extract bar.jar from the apk.
        Aar aar = libProject.getAar("debug");
        File barJar = ZipHelper.extractFile(aar, "libs/bar.jar");

        assertThatZip(barJar).contains(COM_FOO_FOO_CLASS);
        assertThatZip(barJar).doesNotContain(LIB_X86_LIBHELLO_SO);
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        Apk apk = project.getApk("debug");
        check(assertThatApk(apk), "lib", filename, content);
        PackagingTests.checkZipAlign(apk.getFile().toFile());
    }

    /**
     * check an aat has (or not) the given asset file name.
     *
     * If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(TruthHelper.assertThat(project.getAar("debug")), "jni", filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String folderName,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        if (content != null) {
            subject.containsFileWithContent(folderName + "/x86/" + filename, content);
        } else {
            subject.doesNotContain(folderName + "/x86/" + filename);
        }
    }

    /**
     * Creates a class and returns the byte[] with the class
     * @return
     */
    private static byte[] getDummyClassByteCode() {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, "com/foo/Foo", null, "java/lang/Object", null);

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "aaa", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "test/Aaa", "bbb", "()V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "bbb", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "ccc", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();

        cw.visitEnd();

        return cw.toByteArray();
    }
}
