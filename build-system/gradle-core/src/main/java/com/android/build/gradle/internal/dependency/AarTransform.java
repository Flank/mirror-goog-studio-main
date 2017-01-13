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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_AAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_AIDL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_ASSETS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_DATA_BINDING;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_EXT_ANNOTATIONS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_LINT_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_PROGUARD_RULES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_PUBLIC_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_RENDERSCRIPT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_ANDROID_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_SYMBOL;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.gradle.api.artifacts.transform.ArtifactTransformTargets;
import org.gradle.api.attributes.AttributeContainer;

/**
 */
public class AarTransform extends ExtractTransform {

    @Override
    public void configure(AttributeContainer from, ArtifactTransformTargets targets) {
        from.attribute(ARTIFACT_FORMAT, TYPE_AAR);

        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_JAR);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_MANIFEST);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_ANDROID_RES);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_ASSETS);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_JNI);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_AIDL);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_RENDERSCRIPT);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_PROGUARD_RULES);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_LINT_JAR);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_EXT_ANNOTATIONS);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_PUBLIC_RES);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_SYMBOL);
        targets.newTarget().attribute(ARTIFACT_FORMAT, TYPE_DATA_BINDING);
    }

    @Override
    public List<File> transform(File input, AttributeContainer target) {
        //System.out.println("TRANSFORMING(" + target.getAttribute(ARTIFACT_FORMAT) +  "): " + input);
        if (!input.isFile()) {
            //System.out.println("Non existing file: " + input);
            return Collections.emptyList();
        }

        File explodedAar;
        try {
            explodedAar = extractAar(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // single file case return
        File file;

        switch (target.getAttribute(ARTIFACT_FORMAT)) {
            case TYPE_JAR:
                return getJars(explodedAar);
            case TYPE_LINT_JAR:
                file = FileUtils.join(explodedAar, FD_JARS, FN_LINT_JAR);
                break;
            case TYPE_MANIFEST:
                file = new File(explodedAar, FN_ANDROID_MANIFEST_XML);
                break;
            case TYPE_ANDROID_RES:
                file = new File(explodedAar, FD_RES);
                break;
            case TYPE_ASSETS:
                file = new File(explodedAar, FD_ASSETS);
                break;
            case TYPE_JNI:
                file = new File(explodedAar, FD_JNI);
                break;
            case TYPE_AIDL:
                file = new File(explodedAar, FD_AIDL);
                break;
            case TYPE_RENDERSCRIPT:
                file = new File(explodedAar, FD_RENDERSCRIPT);
                break;
            case TYPE_PROGUARD_RULES:
                file = new File(explodedAar, FN_PROGUARD_TXT);
                break;
            case TYPE_EXT_ANNOTATIONS:
                file = new File(explodedAar, FN_ANNOTATIONS_ZIP);
                break;
            case TYPE_PUBLIC_RES:
                file = new File(explodedAar, FN_PUBLIC_TXT);
                break;
            case TYPE_SYMBOL:
                file = new File(explodedAar, FN_RESOURCE_TEXT);
                break;
            case TYPE_DATA_BINDING:
                file = new File(explodedAar, DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR);
                break;
            default:
                throw new RuntimeException("Unsupported type in AarTransform: "
                        + target.getAttribute(ARTIFACT_FORMAT));
        }

        if (file.exists()) {
            return Collections.singletonList(file);
        }

        return Collections.emptyList();
    }

    private static List<File> getJars(@NonNull File explodedAar) {
        List<File> files = Lists.newArrayList();
        File jarFolder = new File(explodedAar, FD_JARS);

        File file = FileUtils.join(jarFolder, FN_CLASSES_JAR);
        if (file.isFile()) {
            files.add(file);
        }

        // local jars
        final File localJarFolder = new File(jarFolder, LIBS_FOLDER);
        File[] jars = localJarFolder.listFiles((dir, name) -> name.endsWith(SdkConstants.DOT_JAR));

        if (jars != null) {
            files.addAll((Arrays.asList(jars)));
        }

        //System.out.println("\tJars: " + files);
        return files;
    }
}
