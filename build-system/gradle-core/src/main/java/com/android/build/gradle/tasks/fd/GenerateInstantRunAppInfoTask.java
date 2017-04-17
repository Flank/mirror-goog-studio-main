/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.tasks.fd;

import static com.android.SdkConstants.ATTR_PACKAGE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.utils.XmlUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Reads the merged manifest file and creates an AppInfo class listing the applicationId and
 * application classes (if any).
 */
public class GenerateInstantRunAppInfoTask extends BaseTask {

    private File outputFile;
    private FileCollection mergedManifests;
    private InstantRunBuildContext buildcontext;
    private SplitScope splitScope;

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    @InputFiles
    public FileCollection getMergedManifests() {
        return mergedManifests;
    }

    @Input
    public long getSecretToken() {
        return buildcontext.getSecretToken();
    }

    @TaskAction
    public void generateInfoTask() throws IOException {
        BuildOutputs.load(VariantScope.TaskOutputType.MERGED_MANIFESTS, mergedManifests);
        Optional<BuildOutput> mainSplitOutput =
                splitScope
                        .getOutputs(VariantScope.TaskOutputType.MERGED_MANIFESTS)
                        .stream()
                        .filter(
                                splitOutput ->
                                        splitOutput.getApkInfo().getType()
                                                        == VariantOutput.OutputType.FULL_SPLIT
                                                || splitOutput.getApkInfo().getType()
                                                        == VariantOutput.OutputType.MAIN)
                        .findFirst();

        if (!mainSplitOutput.isPresent()) {
            throw new RuntimeException("Cannot find main merged manifest.");
        }

        File manifestFile = mainSplitOutput.get().getOutputFile();

        // In manifest merging we stash away and replace the application id/class, and
        // here in a packaging task we inject runtime libraries.
        if (manifestFile.exists()) {
            try {
                // FIX ME : get the package from somewhere else.
                Document document = XmlUtils.parseUtfXmlFile(manifestFile, true);
                Element root = document.getDocumentElement();
                if (root != null) {
                    String applicationId = root.getAttribute(ATTR_PACKAGE);
                    if (!applicationId.isEmpty()) {
                        // Must be *after* extractLibrary() to replace dummy version
                        writeAppInfoClass(applicationId, getSecretToken());
                    }
                }
            } catch (ParserConfigurationException | IOException | SAXException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            }
        }
    }

    void writeAppInfoClass(
            @NonNull String applicationId,
            long token)
            throws IOException {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        String appInfoOwner = "com/android/tools/fd/runtime/AppInfo";
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);

        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationId", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "token", "J", null, null);
        fv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "L" + appInfoOwner + ";", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(applicationId);
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationId", "Ljava/lang/String;");
        if (token != 0L) {
            mv.visitLdcInsn(token);
        } else {
            mv.visitInsn(LCONST_0);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "token", "J");

        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();

        try (JarOutputStream outputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(getOutputFile())))) {
            outputStream.putNextEntry(new ZipEntry("com/android/tools/fd/runtime/AppInfo.class"));
            outputStream.write(bytes);
            outputStream.closeEntry();
        }
    }

    public static class ConfigAction implements TaskConfigAction<GenerateInstantRunAppInfoTask> {
        @NonNull
        private final InstantRunVariantScope variantScope;
        @NonNull
        private final TransformVariantScope transformVariantScope;
        @NonNull private final FileCollection manifests;

        public ConfigAction(
                @NonNull TransformVariantScope transformVariantScope,
                @NonNull InstantRunVariantScope variantScope,
                @NonNull FileCollection manifests) {
            this.transformVariantScope = transformVariantScope;
            this.variantScope = variantScope;
            this.manifests = manifests;
        }

        @NonNull
        @Override
        public String getName() {
            return transformVariantScope.getTaskName("generate", "InstantRunAppInfo");
        }

        @NonNull
        @Override
        public Class<GenerateInstantRunAppInfoTask> getType() {
            return GenerateInstantRunAppInfoTask.class;
        }

        @Override
        public void execute(@NonNull GenerateInstantRunAppInfoTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            task.splitScope = transformVariantScope.getSplitScope();
            task.buildcontext = variantScope.getInstantRunBuildContext();
            task.outputFile =
                    new File(variantScope.getIncrementalApplicationSupportDir(),
                            PackageAndroidArtifact.INSTANT_RUN_PACKAGES_PREFIX + "-bootstrap.jar");

            task.mergedManifests = manifests;

        }
    }
}
