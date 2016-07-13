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

package com.android.build.gradle.internal.tasks.databinding;

import com.android.annotations.NonNull;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;

import org.apache.commons.io.FileUtils;

import android.databinding.tool.DataBindingBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This transform is used to extract .bin fields from jars during a compilation.
 * <p>
 * When annotation processor runs in Jack, it cannot access original jars so data binding cannot
 * find metadata that is extracted from dependencies. With this approach, we copy all dependencies
 * into a build folder so that data binding can read bin files from that directory.
 * <p>
 * Data binding should eventually move away from current bin files and move the metadata into
 * aar.
 *
 * @see com.android.build.gradle.tasks.JackPreDexTransform
 */
public class DataBindingExportDependencyJarsTransform extends Transform {

    private final VariantScope variantScope;

    public DataBindingExportDependencyJarsTransform(VariantScope variantScope) {
        this.variantScope = variantScope;
    }

    @NonNull
    @Override
    public String getName() {
        return "dataBindingDependencyJars";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        File outFolder = variantScope.getBuildFolderForDataBindingCompiler();
        return Collections.singletonList(new File(outFolder,
                DataBindingBuilder.RESOURCE_FILES_DIR));
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        File dataBindingOutFolder = variantScope.getBuildFolderForDataBindingCompiler();
        File binOutFolder = new File(dataBindingOutFolder, DataBindingBuilder.RESOURCE_FILES_DIR);
        FileUtils.deleteDirectory(binOutFolder);
        //noinspection ResultOfMethodCallIgnored
        binOutFolder.mkdirs();
        for (TransformInput input : transformInvocation.getReferencedInputs()) {
            for (JarInput jarInput : input.getJarInputs()) {
                final File jarFile = jarInput.getFile();
                extractBinFiles(jarFile, binOutFolder);
            }
        }
    }

    private static void extractBinFiles(File jarFile, File binOutFolder) throws IOException {
        try (Closer localCloser = Closer.create()) {
            byte[] buffer = new byte[8192];
            FileInputStream fis = localCloser.register(new FileInputStream(jarFile));
            ZipInputStream zis = localCloser.register(new ZipInputStream(fis));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                boolean found = false;
                for (String ext : DataBindingBuilder.RESOURCE_FILE_EXTENSIONS) {
                    if (name.endsWith(ext)) {
                        found = true;
                    }
                }
                if (!found) {
                    continue;
                }
                File out = new File(binOutFolder, name);
                //noinspection ResultOfMethodCallIgnored
                out.getParentFile().mkdirs();
                FileOutputStream fos = localCloser.register(new FileOutputStream(out));
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    fos.write(buffer, 0, count);
                }
                zis.closeEntry();
            }
        }
    }
}
