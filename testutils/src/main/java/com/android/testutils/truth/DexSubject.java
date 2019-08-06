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

package com.android.testutils.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.apk.Dex;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class DexSubject extends Subject<DexSubject, Dex> {

    public static Subject.Factory<DexSubject, Dex> dexes() {
        return DexSubject::new;
    }

    private DexSubject(@NonNull FailureMetadata failureMetadata, @Nullable Dex that) {
        super(failureMetadata, that);
    }

    @NonNull
    public static DexSubject assertThat(@Nullable Dex dex) {
        return Truth.assert_().about(DexSubject.dexes()).that(dex);
    }

    @NonNull
    public static DexSubject assertThatDex(@Nullable File dex) {
        return assertThat(dex != null ? new Dex(dex.toPath()) : null);
    }

    public IndirectSubject<DexClassSubject> containsClass(@NonNull String className)
            throws IOException {
        checkClassName(className);

        if (assertSubjectIsNonNull()) {
            DexBackedClassDef classDef = actual().getClasses().get(className);
            if (classDef == null) {
                fail("contains class", className);
            }
            return () -> assertAbout(DexClassSubject.dexClasses()).that(classDef);
        }
        return () -> assertAbout(DexClassSubject.dexClasses()).that(null);
    }

    public void containsString(@NonNull String regex) throws IOException {
        if (!actual().getStrings().stream().anyMatch(str -> str.matches(regex))) {
            fail("Cannot location any dex string that matches " + regex + " in " + actual());
        }
    }

    public void containsClassesIn(@NonNull Iterable<String> expected) throws IOException {
        for (String clazz : expected) {
            checkClassName(clazz);
        }

        if (assertSubjectIsNonNull()) {
            Sets.SetView<String> missing =
                    Sets.difference(ImmutableSet.copyOf(expected), actual().getClasses().keySet());

            if (!missing.isEmpty()) {
                failWithBadResults(
                        "contains classes", Iterables.toString(expected), "is missing", missing);
            }
        }
    }

    public void containsExactlyClassesIn(@NonNull Iterable<String> expected) throws IOException {
        for (String clazz : expected) {
            checkClassName(clazz);
        }

        if (assertSubjectIsNonNull()) {
            Sets.SetView<String> missing =
                    Sets.difference(ImmutableSet.copyOf(expected), actual().getClasses().keySet());

            Sets.SetView<String> unexpectedElements =
                    Sets.difference(actual().getClasses().keySet(), ImmutableSet.copyOf(expected));

            if (!missing.isEmpty()) {
                if (!unexpectedElements.isEmpty()) {
                    failWithoutActual(
                            Fact.simpleFact(
                                    String.format(
                                            "Not true that %s %s <%s>. It is missing <%s> and has unexpected items <%s>",
                                            actualAsString(),
                                            "contains exactly",
                                            expected,
                                            Iterables.toString(missing),
                                            Iterables.toString(unexpectedElements))));
                } else {
                    failWithBadResults(
                            "contains exactly classes",
                            Iterables.toString(expected),
                            "is missing",
                            missing);
                }
            }
            if (!unexpectedElements.isEmpty()) {
                failWithBadResults(
                        "contains exactly classes",
                        Iterables.toString(expected),
                        "has unexpected",
                        unexpectedElements);
            }
        }
    }

    public void containsClasses(@NonNull String... expected) throws IOException {
        containsClassesIn(Sets.newHashSet(expected));
    }

    public void doesNotContainClasses(@NonNull String... unexpected) throws IOException {
        for (String clazz : unexpected) {
            checkClassName(clazz);
        }

        if (assertSubjectIsNonNull()) {
            Sets.SetView<String> present =
                    Sets.intersection(
                            ImmutableSet.copyOf(unexpected), actual().getClasses().keySet());

            if (!present.isEmpty()) {
                failWithBadResults(
                        "does not contains classes",
                        Arrays.toString(unexpected),
                        "contains",
                        present);
            }
        }
    }

    public void hasClassesCount(int expected) throws IOException {
        if (expected != actual().getClasses().size()) {
            failWithBadResults("does not have size", expected, "has", actual().getClasses().size());
        }
    }

    @Override
    protected String actualCustomStringRepresentation() {
        return "dex file";
    }

    private boolean assertSubjectIsNonNull() {
        if (actual() == null) {
            failWithoutActual(
                    Fact.simpleFact(
                            "Cannot assert about the contents of a dex file that does not exist."));
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
