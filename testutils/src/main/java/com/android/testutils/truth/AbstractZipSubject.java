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
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Truth support for zip files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public abstract class AbstractZipSubject<S extends Subject<S, T>, T extends Zip>
        extends Subject<S, T> {

    public AbstractZipSubject(@NonNull FailureStrategy failureStrategy, @NonNull T subject) {
        super(failureStrategy, subject);
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
    public final IterableSubject<
                    ? extends IterableSubject<?, String, List<String>>, String, List<String>>
            entries(@NonNull String conformingTo) throws IOException {
        // validate file presence
        exists();

        return check().that(
                        getSubject()
                                .getEntries(Pattern.compile(conformingTo))
                                .stream()
                                .map(Path::toString)
                                .collect(Collectors.toList()));
    }

    /**
     * Asserts the zip file contains a file with the specified String content.
     *
     * <p>Content is trimmed when compared.
     */
    public final void containsFileWithContent(@NonNull String path, @NonNull String content)
            throws IOException {
        // validate file presence
        exists();

        check().that(extractContentAsString(path).trim())
                .named(internalCustomName() + ": " + path)
                .isEqualTo(content.trim());
    }

    public final void containsFileWithMatch(@NonNull String path, @NonNull String pattern)
            throws IOException {
        // validate file presence
        exists();

        check().that(extractContentAsString(path)).containsMatch(pattern);
    }

    /** Asserts the zip file contains a file with the specified byte array content. */
    public final void containsFileWithContent(@NonNull String path, @NonNull byte[] content)
            throws IOException {
        // validate file presence
        exists();

        String subjectName =
                MoreObjects.firstNonNull(
                        internalCustomName(), getSubject().getFile().getFileName().toString());

        check().that(extractContentAsBytes(path))
                .named(path + " in " + subjectName)
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
            failWithRawMessage("No entry with path " + path);
        }
        int index = Bytes.indexOf(contents, sub.getBytes());
        if (index != -1) {
            failWithRawMessage("Found byte sequence at " + index + " in class file " + path);
        }
    }

    public final void exists() {
        if (!getSubject().exists()) {
            fail("exists");
        }
    }

    public final void doesNotExist() {
        if (getSubject().exists()) {
            failWithRawMessage("does not exist");
        }
    }

    public final void close() {
        try {
            getSubject().close();
        } catch (Exception e) {
            failWithRawMessage("Exception while closing %1$s", getSubject());
        }
    }

    protected final String extractContentAsString(@NonNull String path) {
        Path entry = getSubject().getEntry(path);
        if (entry == null) {
            failWithRawMessage("Entry " + path + " does not exist.");
            return null;
        }
        try {
            return Files.readAllLines(entry).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            failWithRawMessage(
                    "IOException when extracting %1$s from zip %2$s: %3$s",
                    path, getSubject(), e.toString());
            return null;
        }
    }

    @Nullable
    protected final byte[] extractContentAsBytes(@NonNull String path) {
        Path entry = getSubject().getEntry(path);
        if (entry == null) {
            failWithRawMessage("Entry " + path + " does not exist.");
            return null;
        }
        try {
            return Files.readAllBytes(entry);
        } catch (IOException e) {
            failWithRawMessage(
                    "IOException when extracting %1$s from zip %2$s: %3$s",
                    path, getSubject(), e.toString());
            return null;
        }
    }
}
