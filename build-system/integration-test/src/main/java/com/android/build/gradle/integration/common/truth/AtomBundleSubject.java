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

package com.android.build.gradle.integration.common.truth;

import static com.android.SdkConstants.FN_APK_CLASSES_N_DEX;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.truth.DexUtils;
import com.android.utils.FileUtils;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

/** Truth support for atombundle files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class AtomBundleSubject extends AbstractAndroidSubject<AtomBundleSubject> {

    public static final SubjectFactory<AtomBundleSubject, File> FACTORY =
            new SubjectFactory<AtomBundleSubject, File>() {
                @Override
                public AtomBundleSubject getSubject(
                        @NonNull FailureStrategy failureStrategy, @NonNull File subject) {
                    return new AtomBundleSubject(failureStrategy, subject);
                }
            };

    public AtomBundleSubject(@NonNull FailureStrategy failureStrategy, @NonNull File subject) {
        super(failureStrategy, subject);
    }

    @Override
    protected boolean checkForClass(
            @NonNull String expectedClassName, @NonNull ClassFileScope scope)
            throws ProcessException, IOException {
        return getDexClass(expectedClassName, scope) != null;
    }

    @Override
    protected boolean checkForJavaResource(@NonNull String resourcePath)
            throws ProcessException, IOException {
        try (ZipFile zipFile = new ZipFile(getSubject())) {
            ZipEntry zipEntry =
                    zipFile.getEntry(
                            FileUtils.toSystemIndependentPath(
                                    FileUtils.join("resources", resourcePath)));
            return zipEntry != null;
        }
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified String
     * content.
     *
     * <p>Content is trimmed when compared.
     */
    @Override
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull String content)
            throws IOException, ProcessException {
        containsFileWithContent(
                FileUtils.toSystemIndependentPath(FileUtils.join("resources", path)), content);
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified byte array
     * content.
     */
    @Override
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull byte[] content)
            throws IOException, ProcessException {
        containsFileWithContent(
                FileUtils.toSystemIndependentPath(FileUtils.join("resources", path)), content);
    }

    @Nullable
    private DexBackedClassDef getDexClass(
            @NonNull final String className, @NonNull final ClassFileScope scope)
            throws IOException {
        checkClassName(className);

        switch (scope) {
            case MAIN:
                byte[] classesDex =
                        extractContentAsByte(
                                FileUtils.toSystemIndependentPath(
                                        FileUtils.join(SdkConstants.FD_DEX, "classes.dex")));
                if (classesDex == null) {
                    return null;
                }
                return getDexClass(classesDex, className);
            case SECONDARY:
                // while dexdump supports receiving directly an apk, this doesn't work for
                // multi-dex.
                // We're going to extract all the classes<N>.dex we find until one of them
                // contains the class we're searching for.
                try (ZipFile zipFile = new ZipFile(getSubject())) {
                    int index = 2;
                    String dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    while (zipFile.getEntry(dexFileName) != null) {
                        DexBackedClassDef result =
                                extractEntryAndRunAction(
                                        FileUtils.toSystemIndependentPath(
                                                FileUtils.join(SdkConstants.FD_DEX, dexFileName)),
                                        bytes -> getDexClass(bytes, className));
                        if (result != null) {
                            return result;
                        }
                        // not found? switch to next index.
                        index++;
                        dexFileName = String.format(FN_APK_CLASSES_N_DEX, index);
                    }
                }
                return null;
            case MAIN_AND_SECONDARY:
                DexBackedClassDef dexClass = getDexClass(className, ClassFileScope.MAIN);
                if (dexClass != null) {
                    return dexClass;
                }
                return getDexClass(className, ClassFileScope.SECONDARY);
            case INSTANT_RUN:
                throw new IllegalArgumentException("No INSTANT_RUN scope for atombundles");
            default:
                throw new IllegalArgumentException("unknown class file scope " + scope);
        }
    }

    @Nullable
    private static DexBackedClassDef getDexClass(@NonNull byte[] classesDex, @NonNull String name) {
        DexBackedDexFile dexFile = DexUtils.loadDex(classesDex);
        for (DexBackedClassDef clazz : dexFile.getClasses()) {
            if (clazz.getType().equals(name)) {
                return clazz;
            }
        }
        return null;
    }
}
