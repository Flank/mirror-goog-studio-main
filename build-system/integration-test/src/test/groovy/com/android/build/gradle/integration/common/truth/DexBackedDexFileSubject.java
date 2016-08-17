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

package com.android.build.gradle.integration.common.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.truth.IndirectSubject;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.io.IOException;
import java.util.Set;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class DexBackedDexFileSubject extends Subject<DexBackedDexFileSubject, DexBackedDexFile>
        implements DexSubject {

    public static final SubjectFactory<DexBackedDexFileSubject, DexBackedDexFile> FACTORY =
            new SubjectFactory<DexBackedDexFileSubject, DexBackedDexFile>() {
                @Override
                public DexBackedDexFileSubject getSubject(
                        @NonNull FailureStrategy fs,
                        @Nullable DexBackedDexFile that) {
                    return new DexBackedDexFileSubject(fs, that);
                }
            };

    private DexBackedDexFileSubject(@NonNull FailureStrategy fs, @Nullable DexBackedDexFile that) {
        super(fs, that);
    }

    @Override
    public IndirectSubject<DexClassSubject> hasClass(@NonNull String className)
            throws ProcessException, IOException {
        if (!className.startsWith("L") || !className.endsWith(";")) {
            throw new IllegalArgumentException(
                    "class name must be in the format L" + "com/foo/Main;");
        }

        if (assertSubjectIsNonNull()) {
            Set<? extends DexBackedClassDef> classes = getSubject().getClasses();

            for (DexBackedClassDef clazz : classes) {
                if (clazz.getType().equals(className)) {
                    return () -> DexClassSubject.FACTORY.getSubject(failureStrategy, clazz);
                }
            }
            fail("contains class", className);
        }
        return () -> DexClassSubject.FACTORY.getSubject(failureStrategy, null);
    }


    private boolean assertSubjectIsNonNull() {
        if (getSubject() == null) {
            fail("Cannot assert about the contents of a dex file that does not exist.");
            return false;
        }
        return true;
    }
}
