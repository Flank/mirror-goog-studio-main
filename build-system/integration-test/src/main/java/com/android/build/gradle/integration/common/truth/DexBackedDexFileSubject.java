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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
    public IndirectSubject<DexClassSubject> containsClass(@NonNull String className)
            throws ProcessException, IOException {
        checkClassName(className);

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

    @Override
    public void containsClasses(@NonNull String... expected) throws ProcessException, IOException {
        for (String clazz : expected) {
            checkClassName(clazz);
        }

        if (assertSubjectIsNonNull()) {
            Set<String> actualClasses =
                    getSubject()
                            .getClasses()
                            .stream()
                            .map(DexBackedClassDef::getType)
                            .collect(Collectors.toSet());

            Sets.SetView<String> missing =
                    Sets.difference(ImmutableSet.copyOf(expected), actualClasses);

            if (!missing.isEmpty()) {
                failWithBadResults(
                        "contains classes", Arrays.toString(expected), "is missing", missing);
            }
        }
    }

    @Override
    protected String getDisplaySubject() {
        return "dex file";
    }

    private boolean assertSubjectIsNonNull() {
        if (getSubject() == null) {
            failWithRawMessage(
                    "Cannot assert about the contents of a dex file that does not exist.");
            return false;
        }
        return true;
    }


    private static void checkClassName(@NonNull String className) {
        Preconditions.checkArgument(
                className.startsWith("L") && className.endsWith(";"),
                "Class name '%s' must be in the type descriptor format, e.g. Lcom/foo/Main;",
                className);
    }
}
