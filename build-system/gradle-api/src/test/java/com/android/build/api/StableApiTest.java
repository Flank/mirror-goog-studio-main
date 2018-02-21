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

package com.android.build.api;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test that tries to ensure that our public API remains stable.
 */
public class StableApiTest {

    private static final URL STABLE_API_URL =
            Resources.getResource(StableApiTest.class, "stable-api.txt");

    private static final URL INCUBATING_API_URL =
            Resources.getResource(StableApiTest.class, "incubating-api.txt");

    private static final String INCUBATING_ANNOTATION = "@org.gradle.api.Incubating()";

    @Test
    public void stableApiElements() throws Exception {
        List<String> apiElements = getStableApiElements();

        // Compare the two as strings, to get a nice diff UI in the IDE.
        Iterable<String> expectedApiElements =
                Splitter.on("\n")
                        .omitEmptyStrings()
                        .split(Resources.toString(STABLE_API_URL, Charsets.UTF_8));

        try {
            assertThat(apiElements).containsExactlyElementsIn(expectedApiElements);
        } catch (AssertionError e) {
            throw new AssertionError(
                    "Stable API has changed, either revert the API change or re-run StableApiUpdater from the IDE to update the API file.",
                    e);
        }
    }

    static List<String> getStableApiElements() throws IOException {
        return getApiElements(
                incubatingClass -> !incubatingClass,
                (incubatingClass, incubatingMember) -> !incubatingClass && !incubatingMember);
    }

    @Test
    public void incubatingApiElements() throws Exception {
        List<String> apiElements = getIncubatingApiElements();

        // Compare the two as strings, to get a nice diff UI in the IDE.
        Iterable<String> expectedApiElements =
                Splitter.on("\n")
                        .omitEmptyStrings()
                        .split(Resources.toString(INCUBATING_API_URL, Charsets.UTF_8));

        try {
            assertThat(apiElements).containsExactlyElementsIn(expectedApiElements);
        } catch (AssertionError e) {
            throw new AssertionError(
                    "Incubating API has changed, either revert the API change or re-run StableApiUpdater from the IDE to update the API file.",
                    e);
        }
    }

    static List<String> getIncubatingApiElements() throws IOException {
        return getApiElements(
                incubatingClass -> incubatingClass,
                (incubatingClass, incubatingMember) -> incubatingClass || incubatingMember);
    }

    @Test
    public void apiListHash() throws Exception {
        // ATTENTION REVIEWER: if this needs to be changed, please make sure changes to api-list.txt
        // are backwards compatible.
        assertEquals(
                "37de2b6a3e8907d266459e4825325fa7de39256f22bfa27d6da701da67d829e9",
                Hashing.sha256()
                        .hashString(
                                Resources.toString(STABLE_API_URL, Charsets.UTF_8)
                                        .replace(System.lineSeparator(), "\n"),
                                Charsets.UTF_8)
                        .toString());
    }

    private static List<String> getApiElements(
            @NonNull Predicate<Boolean> classFilter,
            @NonNull BiFunction<Boolean, Boolean, Boolean> memberFilter)
            throws IOException {
        ImmutableSet<ClassPath.ClassInfo> allClasses =
                ClassPath.from(Transform.class.getClassLoader())
                        .getTopLevelClassesRecursive("com.android.build.api");

        return allClasses
                .stream()
                .filter(
                        classInfo ->
                                !classInfo.getSimpleName().endsWith("Test")
                                        && !classInfo.getSimpleName().equals("StableApiUpdater"))
                .flatMap(classInfo -> getApiElements(classInfo.load(), classFilter, memberFilter))
                .sorted()
                .collect(Collectors.toList());
    }

    private static Stream<String> getApiElements(
            @NonNull Class<?> klass,
            @NonNull Predicate<Boolean> classFilter,
            @NonNull BiFunction<Boolean, Boolean, Boolean> memberFilter) {
        if (!Modifier.isPublic(klass.getModifiers()) || isKotlinMedata(klass)) {
            return Stream.empty();
        }

        boolean incubatingClass = isIncubating(klass);

        for (Field field : klass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())) {
                Assert.fail(
                        String.format(
                                "Public instance field %s exposed in class %s.",
                                field.getName(),
                                klass.getName()));
            }
        }

        // streams for all the fields.
        Stream<Stream<String>> streams =
                Stream.of(
                        // Constructors:
                        Stream.of(klass.getDeclaredConstructors())
                                .map(Invokable::from)
                                .filter(StableApiTest::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter.apply(
                                                        incubatingClass, isIncubating(invokable)))
                                .map(StableApiTest::getApiElement)
                                .filter(Objects::nonNull),
                        // Methods:
                        Stream.of(klass.getDeclaredMethods())
                                .map(Invokable::from)
                                .filter(StableApiTest::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter.apply(
                                                        incubatingClass, isIncubating(invokable)))
                                .map(StableApiTest::getApiElement)
                                .filter(Objects::nonNull),

                        // Finally, all inner classes:
                        Stream.of(klass.getDeclaredClasses())
                                .flatMap(
                                        it ->
                                                StableApiTest.getApiElements(
                                                        it, classFilter, memberFilter)));

        List<String> values = streams.flatMap(Function.identity()).collect(Collectors.toList());

        if (classFilter.test(incubatingClass)) {
            values = new ArrayList<>(values);
            values.add(klass.getName());
        }

        return values.stream();
    }

    private static boolean isPublic(Invokable<?, ?> invokable) {
        return invokable.isPublic();
    }

    private static boolean isIncubating(@NonNull AnnotatedElement element) {
        Annotation[] annotations = element.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.toString().equals(INCUBATING_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    private static Boolean isKotlinMedata(@NonNull Class<?> theClass) {
        return theClass.getName().endsWith("$DefaultImpls");
    }

    private static String getApiElement(Invokable<?, ?> invokable) {
        String className = invokable.getDeclaringClass().getName();
        String parameters =
                invokable
                        .getParameters()
                        .stream()
                        .map(Parameter::getType)
                        .map(StableApiTest::typeToString)
                        .collect(Collectors.joining(", "));
        String descriptor = typeToString(invokable.getReturnType()) + " (" + parameters + ")";

        String name = invokable.getName();

        // ignore some weird annotations method generated by Kotlin because
        // they are not generated/seen when building with Bazel
        if (name.endsWith("$annotations")) {
            return null;
        }

        if (name.equals(className)) {
            name = "<init>";
        }

        String thrownExceptions = "";
        ImmutableList<TypeToken<? extends Throwable>> exceptionTypes =
                invokable.getExceptionTypes();
        if (!exceptionTypes.isEmpty()) {
            thrownExceptions =
                    exceptionTypes
                            .stream()
                            .map(StableApiTest::typeToString)
                            .collect(Collectors.joining(", ", " throws ", ""));
        }

        return String.format("%s.%s: %s%s", className, name, descriptor, thrownExceptions);
    }

    private static String typeToString(TypeToken<?> typeToken) {
        if (typeToken.isArray()) {
            return typeToString(typeToken.getComponentType()) + "[]";
        } else {
            return typeToken.toString();
        }
    }
}
