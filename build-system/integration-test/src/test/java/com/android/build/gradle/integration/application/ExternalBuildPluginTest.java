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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.apk.Dex;
import com.android.tools.ir.client.InstantRunArtifact;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest.ApkManifest;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest.Artifact;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import groovy.util.FileNameFinder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Integration test for the ExternalBuildPlugin. */
public class ExternalBuildPluginTest {

    private File manifestFile;

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestProject("externalBuildPlugin")
            .create();

    @Before
    public void setUp() throws Exception {
        IAndroidTarget target = SdkHelper.getTarget(23);
        TruthHelper.assertThat(target).isNotNull();

        ApkManifest.Builder apkManifest =
                ApkManifest.newBuilder()
                        .setAndroidSdk(ExternalBuildApkManifest.AndroidSdk.newBuilder()
                                .setAndroidJar(
                                    target.getFile(IAndroidTarget.ANDROID_JAR).getAbsolutePath())
                                // TODO: Start putting dx.jar in the proto
                                .setDx(SdkHelper.getDxJar().getAbsolutePath())
                                .setAapt(target.getBuildToolInfo().getPath(
                                        BuildToolInfo.PathId.AAPT)))
                        .setResourceApk(Artifact.newBuilder()
                                .setExecRootPath("resources.ap_"))
                        .setDebugKeystore(Artifact.newBuilder()
                                .setExecRootPath("debug.keystore"))
                        .setAndroidManifest(Artifact.newBuilder()
                                .setExecRootPath("AndroidManifest.xml"));

        List<String> jarFiles = new FileNameFinder().getFileNames(
                mProject.getTestDir().getAbsolutePath(), "**/*.jar");
        Path projectPath = mProject.getTestDir().toPath();
        for (String jarFile : jarFiles) {
            Path jarFilePath = new File(jarFile).toPath();
            Path relativePath = projectPath.relativize(jarFilePath);
            apkManifest.addJars(Artifact.newBuilder()
                    .setExecRootPath(relativePath.toString())
                    .setHash(ByteString.copyFromUtf8(String.valueOf(jarFile.hashCode()))));
        }

        manifestFile = mProject.file("apk_manifest.tmp");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(manifestFile))) {
            CodedOutputStream cos =
                    CodedOutputStream.newInstance(os);
            apkManifest.build().writeTo(cos);
            cos.flush();
        }
    }

    @Test
    public void testBuild() throws Exception {
        FileUtils.write(mProject.getBuildFile(), ""
+ "apply from: \"../commonHeader.gradle\"\n"
+ "buildscript {\n "
+ "  apply from: \"../commonBuildScript.gradle\"\n"
+ "}\n"
+ "\n"
+ "apply plugin: 'base'\n"
+ "apply plugin: 'com.android.external.build'\n"
+ "\n"
+ "externalBuild {\n"
+ "  executionRoot = $/" + mProject.getTestDir().getAbsolutePath() +"/$\n"
+ "  buildManifestPath = $/" + manifestFile.getAbsolutePath() + "/$\n"
+ "}\n");

        mProject.executor().withInstantRun(new AndroidVersion(23, null)).run("clean", "process");

        InstantRunBuildInfo info = loadBuildInfo();

        TruthHelper.assertThat(info.getVerifierStatus())
                .isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD.toString());

        InstantRunTestUtils.getCompiledColdSwapChange(info.getArtifacts());

        // now perform a hot swap test.
        File mainClasses = new File(mProject.getTestDir(), "jars/main/classes.jar");
        TruthHelper.assertThat(mainClasses.exists()).isTrue();

        File originalFile = new File(mainClasses.getParentFile(), "original_classes.jar");
        TruthHelper.assertThat(mainClasses.renameTo(originalFile)).isTrue();

        try (JarFile inputJar = new JarFile(originalFile);
             JarOutputStream jarOutputFile = new JarOutputStream(new BufferedOutputStream(
                new FileOutputStream(new File(mainClasses.getParentFile(), "classes.jar"))))) {
            Enumeration<JarEntry> entries = inputJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry element = entries.nextElement();
                try (InputStream inputStream = new BufferedInputStream(
                        inputJar.getInputStream(element))) {
                    if (!element.isDirectory()) {
                        jarOutputFile.putNextEntry(new ZipEntry(element.getName()));
                        try {
                            if (element.getName().contains("MainActivity.class")) {
                                // perform hot swap change
                                byte[] classBytes = new byte[(int) element.getSize()];
                                ByteStreams.readFully(inputStream, classBytes);
                                classBytes = hotswapChange(classBytes);
                                jarOutputFile.write(classBytes);
                            } else {
                                ByteStreams.copy(inputStream, jarOutputFile);
                            }
                        } finally {
                            jarOutputFile.closeEntry();
                        }
                    }
                }
            }
        }

        mProject.executor().withInstantRun(new AndroidVersion(23, null)).run("process");

        info = loadBuildInfo();
        assertThat(info.getVerifierStatus())
                .isEqualTo(InstantRunVerifierStatus.COMPATIBLE.toString());
        assertThat(info.getArtifacts()).hasSize(1);

        InstantRunArtifact hotSwap = info.getArtifacts().get(0);
        assertThat(hotSwap.type).isEqualTo(InstantRunArtifactType.RELOAD_DEX);

        Dex dex = new Dex(hotSwap.file);
        assertThat(dex).containsClass("Lcom/android/tools/ir/runtime/AppPatchesLoaderImpl;");
        assertThat(dex).containsClass("Lcom/example/jedo/blazeapp/MainActivity$override;");
    }

    @NonNull
    private InstantRunBuildInfo loadBuildInfo() throws Exception {
        File buildInfoFile =
                new File(mProject.getTestDir(), "build/build-info/debug/build-info.xml");
        TruthHelper.assertThat(buildInfoFile).exists();
        InstantRunBuildInfo buildInfo =
                InstantRunBuildInfo.get(Files.toString(buildInfoFile, Charsets.UTF_8));
        assertNotNull(buildInfo);
        return buildInfo;
    }

    private static byte[] hotswapChange(byte[] inputClass) {
        ClassReader cr = new ClassReader(inputClass);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitCode() {
                        // add a useless logging to the method.
                        mv.visitFieldInsn(Opcodes.GETSTATIC,
                                "java/lang/System",
                                "out",
                                "Ljava/io/PrintStream;");
                        mv.visitLdcInsn("test changed !");
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                "java/io/PrintStream",
                                "println",
                                "(Ljava/lang/String;)V",
                                false);
                        super.visitCode();
                    }
                };
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

}



