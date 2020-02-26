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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.testutils.apk.AndroidArchive;
import com.android.testutils.truth.AbstractZipSubject;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Base Truth support for android archives (aar and apk) */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public abstract class AbstractAndroidSubject<
                S extends AbstractAndroidSubject<S, T>, T extends AndroidArchive>
        extends AbstractZipSubject<S, T> {

    public AbstractAndroidSubject(@NonNull FailureMetadata failureMetadata, @NonNull T subject) {
        super(failureMetadata, subject);
    }

    protected static boolean isClassName(@NonNull String className) {
        return AndroidArchive.CLASS_FORMAT.matcher(className).matches();
    }

    public final void containsClass(@NonNull String className) {
        try {
            if (!actual().containsClass(className)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' does not contain class '%s'.\n",
                                        actualAsString(), className)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final void containsMainClass(@NonNull String className) {
        try {
            if (!actual().containsMainClass(className)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' does not contain main class '%s'",
                                        actualAsString(), className)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final void containsSecondaryClass(@NonNull String className) {
        try {
            if (!actual().containsSecondaryClass(className)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' does not contain secondary class '%s'",
                                        actualAsString(), className)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public final void contains(@NonNull String path) {
        checkArgument(!isClassName(path), "Use containsClass to check for classes.");
        if (actual().getEntry(path) == null) {
            failWithoutActual(
                    Fact.simpleFact(String.format("'%s' does not contain '%s'", actual(), path)));
        }
    }

    @Override
    public final void doesNotContain(@NonNull String path) {
        checkArgument(!isClassName(path), "Use doesNotContainClass to check for classes.");
        if (actual().getEntry(path) != null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("'%s' unexpectedly contains '%s'", actual(), path)));
        }
    }

    public final void containsFile(@NonNull String fileName) {
        if (actual().getEntries(Pattern.compile("(.*/)?" + fileName)).isEmpty()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("'%s' does not contain file '%s'", actual(), fileName)));
        }
    }

    public final void doesNotContainFile(@NonNull String fileName) {
        if (!actual().getEntries(Pattern.compile("(.*/)?" + fileName)).isEmpty()) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "'%s' unexpectedly contains file '%s'", actual(), fileName)));
        }
    }

    public final void doesNotContainClass(@NonNull String className) {
        try {
            if (actual().containsClass(className)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' unexpectedly contains class '%s'",
                                        actualAsString(), className)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final void doesNotContainMainClass(@NonNull String className) {
        try {
            if (actual().containsMainClass(className)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' unexpectedly contains main class '%s'",
                                        actualAsString(), className)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final void doesNotContainSecondaryClass(@NonNull String className) {
        try {
            if (actual().containsSecondaryClass(className)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' unexpectedly contains secondary class '%s'",
                                        actualAsString(), className)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsResource(@NonNull String name) {
        if (actual().getResource(name) == null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "'%s' does not contain resource '%s'",
                                    actualAsString(), name)));
        }
    }

    public final void doesNotContainResource(@NonNull String name) {
        if (actual().getResource(name) != null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "'%s' unexpectedly contains resource '%s'",
                                    actualAsString(), name)));
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsJavaResource(@NonNull String name) {
        try {
            if (actual().getJavaResource(name) == null) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' does not contain Java resource '%s'",
                                        actualAsString(), name)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final void doesNotContainJavaResource(@NonNull String name) {
        try {
            if (actual().getJavaResource(name) != null) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "'%s' unexpectedly contains Java resource '%s'",
                                        actualAsString(), name)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Asserts the subject contains a java resource at the given path with the specified String
     * content.
     *
     * <p>Content is trimmed when compared.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsJavaResourceWithContent(
            @NonNull String path, @NonNull String expected) {
        try {
            Path resource = actual().getJavaResource(path);
            if (resource == null) {
                failWithoutActual(
                        Fact.simpleFact("Resource " + path + " does not exist in " + actual()));
                return;
            }
            String actual = Files.readAllLines(resource).stream().collect(Collectors.joining("\n"));
            if (!expected.equals(actual)) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "Resource %s in %s does not have expected contents. Expected '%s' actual '%s'",
                                        path, actual(), expected, actual)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Asserts the subject contains a java resource at the given path with the specified byte array
     * content.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public final void containsJavaResourceWithContent(
            @NonNull String path, @NonNull byte[] expected) {
        try {
            Path resource = actual().getJavaResource(path);
            if (resource == null) {
                failWithoutActual(
                        Fact.simpleFact("Resource " + path + " does not exist in " + actual()));
                return;
            }

            byte[] actual = Files.readAllBytes(resource);
            if (!Arrays.equals(expected, actual)) {
                failWithBadResults(
                        "[" + path + "] has contents",
                        Arrays.toString(expected),
                        "contains",
                        Arrays.toString(actual));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected String actualCustomStringRepresentation() {
        return (internalCustomName() == null)
                ? actual().toString()
                : "\"" + internalCustomName() + "\" <" + actual().toString() + ">";
    }
}
