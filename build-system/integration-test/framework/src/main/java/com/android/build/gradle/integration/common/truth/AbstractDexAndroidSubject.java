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

import static com.android.testutils.truth.DexClassSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.DexAndroidArchive;
import com.android.testutils.truth.DexClassSubject;
import com.android.testutils.truth.DexSubject;
import com.android.testutils.truth.IndirectSubject;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import java.io.IOException;
import java.util.Optional;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

/** Truth support for apk files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class AbstractDexAndroidSubject<
                S extends AbstractDexAndroidSubject<S, T>, T extends DexAndroidArchive>
        extends AbstractAndroidSubject<S, T> {

    public AbstractDexAndroidSubject(@NonNull FailureMetadata failureMetadata, @NonNull T subject) {
        super(failureMetadata, subject);
    }

    @NonNull
    public final IndirectSubject<DexSubject> hasMainDexFile() throws IOException {
        Optional<Dex> dex = actual().getMainDexFile();
        if (!dex.isPresent()) {
            failWithoutActual(
                    Fact.simpleFact(String.format("'%s' does not contain main dex", actual())));
        }
        return () -> DexSubject.assertThat(dex.get());
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasMainClass(
            @NonNull final String expectedClassName) throws IOException {
        DexBackedClassDef dexBackedClassDef = getMainClass(expectedClassName);
        if (dexBackedClassDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> assertThat(dexBackedClassDef);
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasSecondaryClass(
            @NonNull final String expectedClassName) throws IOException {
        DexBackedClassDef dexBackedClassDef = getSecondaryClass(expectedClassName);
        if (dexBackedClassDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> assertThat(dexBackedClassDef);
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasClass(@NonNull final String expectedClassName)
            throws IOException {
        DexBackedClassDef mainClassDef = getMainClass(expectedClassName);
        DexBackedClassDef classDef =
                mainClassDef != null ? mainClassDef : getSecondaryClass(expectedClassName);
        if (classDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> assertThat(classDef);
    }

    public void hasDexVersion(int expectedDexVersion) {

        try {
            if (expectedDexVersion
                    != actual().getMainDexFile().orElseThrow(AssertionError::new).getVersion()) {
                fail("dex version", expectedDexVersion);
            }
        } catch (IOException e) {
            fail("has a dex");
        }
    }

    private DexBackedClassDef getMainClass(@NonNull String className) throws IOException {
        Optional<Dex> classesDex = actual().getMainDexFile();
        if (!classesDex.isPresent()) {
            return null;
        }
        return classesDex.get().getClasses().get(className);
    }

    private DexBackedClassDef getSecondaryClass(@NonNull String className) throws IOException {
        for (Dex dex : actual().getSecondaryDexFiles()) {
            DexBackedClassDef classDef = dex.getClasses().get(className);
            if (classDef != null) {
                return classDef;
            }
        }
        return null;
    }
}
