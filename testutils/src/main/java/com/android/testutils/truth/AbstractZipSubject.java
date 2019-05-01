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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.apk.Zip;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.Bytes;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Truth support for zip files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public abstract class AbstractZipSubject<S extends Subject<S, T>, T extends Zip>
        extends Subject<S, T> implements Closeable {

    public AbstractZipSubject(@NonNull FailureMetadata failureMetadata, @NonNull T subject) {
        super(failureMetadata, subject);
    }

    /** Asserts the zip file contains a file with the specified path. */
    public abstract void contains(@NonNull String path) throws IOException;

    /** Asserts the zip file does not contains a file with the specified path. */
    public abstract void doesNotContain(@NonNull String path) throws IOException;

    /**
     * Returns a {@link IterableSubject} of all the Zip entries which name matches the passed
     * regular expression.
     *
     * @param conformingTo a regular expression to match entries we are interested in.
     * @return a {@link IterableSubject} propositions for matching entries.
     * @throws IOException of the zip file cannot be opened.
     */
    public final IterableSubject entries(@NonNull String conformingTo) throws IOException {
        // validate file presence
        exists();

        return check().that(
                        actual().getEntries(Pattern.compile(conformingTo))
                                .stream()
                                .map(Path::toString)
                                .collect(Collectors.toList()));
    }

    /**
     * Asserts the zip file contains a file with the specified String content.
     *
     * <p>Content is trimmed when compared.
     */
    public final void containsFileWithContent(@NonNull String path, @NonNull String content) {
        // validate file presence
        exists();

        check().that(extractContentAsString(path).trim())
                .named(internalCustomName() + ": " + path)
                .isEqualTo(content.trim());
    }

    public final void containsFileWithMatch(@NonNull String path, @NonNull String pattern) {
        // validate file presence
        exists();

        check().that(extractContentAsString(path)).containsMatch(pattern);
    }

    /** Asserts the zip file contains a file with the specified byte array content. */
    public final void containsFileWithContent(@NonNull String path, @NonNull byte[] content) {
        // validate file presence
        exists();

        String subjectName =
                MoreObjects.firstNonNull(
                        internalCustomName(), actual().getFile().getFileName().toString());

        byte[] actual = extractContentAsBytes(path);

        check().that(actual)
                .named(path + " in " + subjectName + " content=" + Arrays.toString(actual))
                .isEqualTo(content);
    }

    /**
     * Asserts the zip file contains a file <b>without</b> the specified byte sequence
     * <b>anywhere</b> in the file
     */
    public final void containsFileWithoutContent(@NonNull String path, @NonNull String sub) {
        // validate file presence
        exists();

        byte[] contents = extractContentAsBytes(path);
        if (contents == null) {
            failWithoutActual(Fact.simpleFact("No entry with path " + path));
        }
        int index = Bytes.indexOf(contents, sub.getBytes());
        if (index != -1) {
            failWithoutActual(
                    Fact.simpleFact("Found byte sequence at " + index + " in class file " + path));
        }
    }

    public final void exists() {
        if (!actual().exists()) {
            Path nearestParent = actual().getFile();
            while (nearestParent != null && !Files.exists(nearestParent)) {
                nearestParent = nearestParent.getParent();
            }

            StringBuilder failure = new StringBuilder("exists");
            if (nearestParent != null) {
                failure.append(" (Nearest ancestor that exists is ")
                        .append(nearestParent)
                        .append("\n");
                try (Stream<Path> files = Files.list(nearestParent)) {
                    files.forEach(
                            path -> failure.append(" - ").append(path.getFileName()).append("\n"));
                } catch (IOException e) {
                    failure.append(e);
                }
                failure.append(")\n");
            } else {
                failure.append(" (no ancestor directories exist either)");
            }
            fail(failure.toString());
        }
    }

    public final void doesNotExist() {
        if (actual().exists()) {
            fail("does not exist");
        }
    }

    @Override
    public final void close() {
        try {
            actual().close();
        } catch (Exception e) {
            failWithoutActual(
                    Fact.simpleFact(String.format("Exception while closing %1$s", actual())));
        }
    }

    protected final String extractContentAsString(@NonNull String path) {
        Path entry = actual().getEntry(path);
        if (entry == null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "Entry %s does not exist in zip %s.",
                                    path, actual().toString())));
            return null;
        }
        try {
            return Files.readAllLines(entry).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "IOException when extracting %1$s from zip %2$s: %3$s",
                                    path, actual(), e.toString())));
            return null;
        }
    }

    @Nullable
    protected final byte[] extractContentAsBytes(@NonNull String path) {
        Path entry = actual().getEntry(path);
        if (entry == null) {
            failWithoutActual(Fact.simpleFact("Entry " + path + " does not exist."));
            return null;
        }
        try {
            return Files.readAllBytes(entry);
        } catch (IOException e) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format(
                                    "IOException when extracting %1$s from zip %2$s: %3$s",
                                    path, actual(), e.toString())));
            return null;
        }
    }
}
