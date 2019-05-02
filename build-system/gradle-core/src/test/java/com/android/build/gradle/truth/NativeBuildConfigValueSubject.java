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

package com.android.build.gradle.truth;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFolderValue;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Truth support for validating {@link NativeBuildConfigValue}.
 * - Maps are compared by key (so order doesn't matter)
 * - Lists are compared ordinally (order matters) unless the list is in UNORDERED_LISTS
 * - Fields are discovered reflectively so that new fields are caught.
 */
public class NativeBuildConfigValueSubject
        extends Subject<NativeBuildConfigValueSubject, NativeBuildConfigValue> {
    private static final ImmutableSet<String> UNORDERED_LISTS = ImmutableSet.of(
            "/cFileExtensions",
            "/cppFileExtensions");

    public static Subject.Factory<NativeBuildConfigValueSubject, NativeBuildConfigValue>
            nativebuildConfigValues() {
        return NativeBuildConfigValueSubject::new;
    }

    public NativeBuildConfigValueSubject(
            @NonNull FailureMetadata failureMetadata, @Nullable NativeBuildConfigValue subject) {
        super(failureMetadata, subject);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isEqualTo(NativeBuildConfigValue other) {

        try {
            assertEqual("", actual(), other);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal Access", e);
        }
    }

    private void assertEqual(
            String levelDescription,
            Object actual,
            Object expected) throws IllegalAccessException {
        if (expected == null) {
            check().that(actual).named(levelDescription).isNull();
            return;
        }

        check().that(actual).named(levelDescription).isNotNull();

        if (expected instanceof List) {
            List actualList = (List) actual;
            List expectedList = (List) expected;
            check().that(actualList.size())
                    .named(levelDescription + ".size")
                    .isEqualTo(expectedList.size());

            if (UNORDERED_LISTS.contains(levelDescription)) {
                actualList = Lists.newArrayList(actualList);
                expectedList = Lists.newArrayList(expectedList);
                Collections.sort(actualList);
                Collections.sort(expectedList);
            }

            for (int i = 0; i < actualList.size(); ++i) {
                assertEqual(levelDescription + "[" + i + "]", actualList.get(i),
                        expectedList.get(i));
            }
            return;
        }

        if (expected instanceof Map) {
            check().that(actual).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked") Map<String, ?> actualMap = (Map) actual;
            @SuppressWarnings("unchecked") Map<String, ?> expectedMap = (Map) expected;
            Set<String> actualKeys = actualMap.keySet();
            Set<String> expectedKeys = expectedMap.keySet();
            check().that(actualKeys)
                .named(levelDescription + ".keys")
                .containsAllIn(expectedKeys);
            for (Object key : actualMap.keySet()) {
                //noinspection SuspiciousMethodCalls,SuspiciousMethodCalls
                assertEqual(levelDescription + "[" + key + "]",
                        actualMap.get(key), expectedMap.get(key));
            }
            return;
        }

        if (expected instanceof NativeBuildConfigValue
                || expected instanceof NativeLibraryValue
                || expected instanceof NativeSourceFileValue
                || expected instanceof NativeSourceFolderValue
                || expected instanceof NativeToolchainValue) {
            check().that(actual).isInstanceOf(expected.getClass());
            for (Field field : actual.getClass().getFields()) {
                Object fieldActual = field.get(actual);
                Object fieldExpected = field.get(expected);
                assertEqual(levelDescription + "/" + field.getName(),
                        fieldActual, fieldExpected);
            }
            return;
        }

        if (expected instanceof File) {
            check().that(actual).isInstanceOf(expected.getClass());
            check().that(((File) actual).getPath().replace('\\', '/'))
                    .named(levelDescription)
                    .isEqualTo(((File) expected).getPath().replace('\\', '/'));
        } else if (expected instanceof String) {
            check().that(actual).isInstanceOf(expected.getClass());
            check().that(((String) actual).replace('\\', '/'))
                    .named(levelDescription)
                    .isEqualTo(((String) expected).replace('\\', '/'));
        } else {
            check().that(actual).named(levelDescription).isEqualTo(expected);
        }
    }

    @NonNull
    private Set<String> getIntermediatesNames() {
        Set<String> names = Sets.newHashSet();
        checkNotNull(actual().libraries);
        for (NativeLibraryValue library : actual().libraries.values()) {
            if (library.output != null) {
                names.add(library.output.toString());
            }
        }
        return names;
    }

    @NonNull
    private Set<String> getLibraryNames() {
        Set<String> names = Sets.newHashSet();
        checkNotNull(actual().libraries);
        for (String library : actual().libraries.keySet()) {
            names.add(library);
        }
        return names;
    }

    @NonNull
    private Set<String> getSourceFileNames() {
        Set<String> names = Sets.newHashSet();
        checkNotNull(actual().libraries);
        for (NativeLibraryValue library : actual().libraries.values()) {
            if (library.files == null) {
                continue;
            }
            for (NativeSourceFileValue file : library.files) {
                if (file.src == null) {
                    continue;
                }
                names.add(file.src.getPath());
            }
        }
        return names;
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    private void hasExactOutputFiles(String... baseName) {
        Set<String> intermediateNames = getIntermediatesNames();
        Set<String> expected = Sets.newHashSet(baseName);
        Truth.assertThat(intermediateNames).containsExactlyElementsIn(expected);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactLibraryOutputs(String... baseName) {
        hasExactOutputFiles(baseName);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactLibrariesNamed(String... targets) {
        Set<String> intermediateNames = getLibraryNames();
        Set<String> expected = Sets.newHashSet(targets);
        Truth.assertThat(intermediateNames).containsExactlyElementsIn(expected);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasUniqueLibraryNames() {
        Set<String> names = Sets.newHashSet();
        Set<String> duplicates = Sets.newHashSet();
        checkNotNull(actual().libraries);
        for (String library : actual().libraries.keySet()) {
            if (names.contains(library)) {
                duplicates.add(library);
            }
            names.add(library);
        }

        if (!duplicates.isEmpty()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that %s libraries have unique names. It had duplications %s",
                                    actualAsString(), duplicates)));
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasExactSourceFileNames(String... fileNames) {
        Set<String> intermediateNames = getSourceFileNames();
        Set<String> expected = Sets.newHashSet(fileNames);
        Set<String> expectedNotFound = Sets.newHashSet();
        expectedNotFound.addAll(expected);
        expectedNotFound.removeAll(intermediateNames);
        if (!expectedNotFound.isEmpty()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that %s source files was %s. Set %s was missing %s",
                                    actualAsString(),
                                    expected,
                                    intermediateNames,
                                    expectedNotFound)));
        }

        Set<String> foundNotExpected = Sets.newHashSet();
        foundNotExpected.addAll(intermediateNames);
        foundNotExpected.removeAll(expected);
        if (!foundNotExpected.isEmpty()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that %s source files was %s. It had extras %s",
                                    actualAsString(), expected, foundNotExpected)));
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasSourceFileNames(String... fileNames) {
        Set<String> intermediateNames = getSourceFileNames();
        Set<String> expected = Sets.newHashSet(fileNames);
        Set<String> expectedNotFound = Sets.newHashSet();
        expectedNotFound.addAll(expected);
        expectedNotFound.removeAll(intermediateNames);
        if (!expectedNotFound.isEmpty()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Not true that %s source files contained %s. Set %s was missing %s",
                                    actualAsString(),
                                    expected,
                                    intermediateNames,
                                    expectedNotFound)));
        }
    }
}
