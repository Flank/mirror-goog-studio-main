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
import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.FN_R_CLASS_JAR;
import static com.android.SdkConstants.FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.LIBS_FOLDER;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that returns the content of an extracted AAR folder. */
public class AarTransform extends ArtifactTransform {
    @NonNull private final ArtifactType targetType;
    private final boolean sharedLibSupport;

    @Inject
    public AarTransform(@NonNull ArtifactType targetType, boolean sharedLibSupport) {
        this.targetType = targetType;
        this.sharedLibSupport = sharedLibSupport;
    }

    @NonNull
    public static ArtifactType[] getTransformTargets() {
        return new ArtifactType[] {
            ArtifactType.CLASSES,
            ArtifactType.SHARED_CLASSES,
            ArtifactType.JAVA_RES,
            ArtifactType.SHARED_JAVA_RES,
            ArtifactType.JAR,
            ArtifactType.MANIFEST,
            ArtifactType.ANDROID_RES,
            ArtifactType.ASSETS,
            ArtifactType.SHARED_ASSETS,
            ArtifactType.JNI,
            ArtifactType.SHARED_JNI,
            ArtifactType.AIDL,
            ArtifactType.RENDERSCRIPT,
            ArtifactType.PROGUARD_RULES,
            ArtifactType.LINT,
            ArtifactType.ANNOTATIONS,
            ArtifactType.PUBLIC_RES,
            ArtifactType.SYMBOL_LIST,
            ArtifactType.DATA_BINDING_ARTIFACT,
            ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
            ArtifactType.RES_STATIC_LIBRARY,
            ArtifactType.RES_SHARED_STATIC_LIBRARY,
        };
    }

    @Override
    @NonNull
    public List<File> transform(@NonNull File input) {
        switch (targetType) {
            case CLASSES:
            case JAVA_RES:
            case JAR:
                // even though resources are supposed to only be in the main jar of the AAR, this
                // is not necessarily enforced by all build systems generating AAR so it's safer to
                // read all jars from the manifest.
                // For shared libraries, these are provided via SHARED_CLASSES and SHARED_JAVA_RES.
                return isShared(input) ? Collections.emptyList() : getJars(input);
            case SHARED_CLASSES:
            case SHARED_JAVA_RES:
                return isShared(input) ? getJars(input) : Collections.emptyList();
            case LINT:
                return listIfExists(FileUtils.join(input, FD_JARS, FN_LINT_JAR));
            case MANIFEST:
                if (isShared(input)) {
                    // Return both the manifest and the extra snippet for the shared library.
                    return listIfExists(
                            Stream.of(
                                    new File(input, FN_ANDROID_MANIFEST_XML),
                                    new File(input, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML)));
                } else {
                    return listIfExists(new File(input, FN_ANDROID_MANIFEST_XML));
                }
            case ANDROID_RES:
                return listIfExists(new File(input, FD_RES));
            case ASSETS:
                return listIfExists(new File(input, FD_ASSETS));
            case JNI:
                return listIfExists(new File(input, FD_JNI));
            case AIDL:
                return listIfExists(new File(input, FD_AIDL));
            case RENDERSCRIPT:
                return listIfExists(new File(input, FD_RENDERSCRIPT));
            case PROGUARD_RULES:
                return listIfExists(new File(input, FN_PROGUARD_TXT));
            case ANNOTATIONS:
                return listIfExists(new File(input, FN_ANNOTATIONS_ZIP));
            case PUBLIC_RES:
                return listIfExists(new File(input, FN_PUBLIC_TXT));
            case SYMBOL_LIST:
                return listIfExists(new File(input, FN_RESOURCE_TEXT));
            case RES_STATIC_LIBRARY:
                return isShared(input)
                        ? Collections.emptyList()
                        : listIfExists(new File(input, FN_RESOURCE_STATIC_LIBRARY));
            case RES_SHARED_STATIC_LIBRARY:
                return isShared(input)
                        ? listIfExists(
                                new File(input, SdkConstants.FN_RESOURCE_SHARED_STATIC_LIBRARY))
                        : Collections.emptyList();
            case COMPILE_ONLY_NAMESPACED_R_CLASS_JAR:
                return listIfExists(new File(input, FN_R_CLASS_JAR));
            case DATA_BINDING_ARTIFACT:
                return listIfExists(
                        new File(input, DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR));
            default:
                throw new RuntimeException("Unsupported type in AarTransform: " + targetType);
        }
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

    private boolean isShared(@NonNull File explodedAar) {
        return sharedLibSupport
                && new File(explodedAar, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML).exists();
    }

    @NonNull
    private static List<File> listIfExists(@NonNull File file) {
        return file.exists() ? Collections.singletonList(file) : Collections.emptyList();
    }

    @NonNull
    private static List<File> listIfExists(@NonNull Stream<File> files) {
        return files.filter(File::exists).collect(Collectors.toList());
    }
}
