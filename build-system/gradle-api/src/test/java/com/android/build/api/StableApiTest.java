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

import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
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
import java.util.Set;
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
        Set<String> apiElements =
                getApiElements(
                        incubatingClass -> !incubatingClass,
                        (incubatingClass, incubatingMember) ->
                                !incubatingClass && !incubatingMember);

        assertSame(STABLE_API_URL, apiElements);
    }

    @Test
    public void incubatingApiElements() throws Exception {
        Set<String> apiElements =
                getApiElements(
                        incubatingClass -> incubatingClass,
                        (incubatingClass, incubatingMember) -> incubatingClass || incubatingMember);

        assertSame(INCUBATING_API_URL, apiElements);
    }

    @Test
    public void apiListHash() throws Exception {
        // ATTENTION REVIEWER: if this needs to be changed, please make sure changes to api-list.txt
        // are backwards compatible.
        assertThat(hashResourceFile(STABLE_API_URL))
                .named("Stable API file hash")
                .isEqualTo("2293875af8a6b0700f099823ea4556d90fab3578");
        assertThat(hashResourceFile(INCUBATING_API_URL))
                .named("Stable API file hash")
                .isEqualTo("c5d78185d6116a2707b310d68420a2cf831bc67c");
    }

    @NonNull
    private static String hashResourceFile(@NonNull URL url) throws IOException {
        return Hashing.sha1()
                .hashString(
                        Resources.toString(url, Charsets.UTF_8)
                                .replace(System.lineSeparator(), "\n"),
                        Charsets.UTF_8)
                .toString();
    }

    @NonNull
    private static Set<String> getApiElements(
            @NonNull Predicate<Boolean> classFilter,
            @NonNull BiFunction<Boolean, Boolean, Boolean> memberFilter)
            throws IOException {
        ImmutableSet<ClassPath.ClassInfo> allClasses =
                ClassPath.from(Transform.class.getClassLoader())
                        .getTopLevelClassesRecursive("com.android.build.api");

        return allClasses
                .stream()
                .filter(classInfo -> !classInfo.getSimpleName().endsWith("Test"))
                .flatMap(classInfo -> getApiElements(classInfo.load(), classFilter, memberFilter))
                .collect(Collectors.toSet());
    }

    @NonNull
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
                                .filter(Invokable::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter.apply(
                                                        incubatingClass, isIncubating(invokable)))
                                .map(StableApiTest::getApiElement)
                                .filter(Objects::nonNull),
                        // Methods:
                        Stream.of(klass.getDeclaredMethods())
                                .map(Invokable::from)
                                .filter(Invokable::isPublic)
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

    private static final String TERMINAL_GREEN = "\u001B[32m";
    private static final String TERMINAL_RED = "\u001B[31m";
    private static final String TERMINAL_RESET = "\u001B[0m";

    private static void assertSame(@NonNull URL url, @NonNull Set<String> actual)
            throws IOException {

        // Compare the two as strings, to get a nice diff UI in the IDE.
        Set<String> expected =
                Streams.stream(
                                Splitter.on(System.lineSeparator())
                                        .omitEmptyStrings()
                                        .split(Resources.toString(url, Charsets.UTF_8)))
                        .collect(Collectors.toSet());
        if (expected.equals(actual)) {
            return;
        }

        Set<String> added = Sets.difference(actual, expected);
        Set<String> removed = Sets.difference(expected, actual);

        String fileName = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);

        System.out.format(
                "API file %1$s must be updated when API changes are made\n"
                        + "%2$d addition%3$s only present in code,  %4$d removal%5$s only present in API list.\n\n",
                fileName,
                added.size(),
                added.size() == 1 ? "" : "s",
                removed.size(),
                removed.size() == 1 ? "" : "s");

        // Pretty diff
        System.out.format("Changes: %n");
        added.stream()
                .sorted()
                .forEach(item -> System.out.format("%1$s+%2$s%n", TERMINAL_GREEN, item));
        removed.stream()
                .sorted()
                .forEach(item -> System.out.format("%1$s-%2$s%n", TERMINAL_RED, item));
        System.out.println(TERMINAL_RESET);

        // Print new file contents to terminal for easy copy-paste.
        System.out.format(
                "Replace the content of %1$s with the below, or revert the API changes.%n"
                        + "----------------------------------------%n"
                        + "%2$s"
                        + "\n----------------------------------------\n",
                fileName, actual.stream().sorted().collect(Collectors.joining("\n")));

        throw new AssertionError("API file is not up to date");
    }
}
