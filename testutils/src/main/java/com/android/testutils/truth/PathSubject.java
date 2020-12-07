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

import static com.google.common.truth.Fact.simpleFact;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Joiner;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

/**
 * Truth support for validating java.nio.file.Path.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")  // Functions do not return.
public class PathSubject extends Subject<PathSubject, Path> {
    private static final Joiner LINE_JOINER = Joiner.on('\n');

    public static Subject.Factory<PathSubject, Path> paths() {
        return PathSubject::new;
    }

    public PathSubject(@NonNull FailureMetadata failureMetadata, @Nullable Path subject) {
        super(failureMetadata, subject);
    }

    public void isEqualTo(@Nullable File expected) {
        super.isEqualTo(expected == null ? null : expected.toPath());
    }

    public void isNotEqualTo(@Nullable File unexpected) {
        super.isNotEqualTo(unexpected == null ? null : unexpected.toPath());
    }

    public static PathSubject assertThat(@Nullable Path path) {
        return Truth.assertAbout(paths()).that(path);
    }

    public static PathSubject assertThat(@Nullable File file) {
        return assertThat(file == null ? null : file.toPath());
    }

    public void hasName(@NonNull String name) {
        check().that(actual().getFileName().toString()).named(actualAsString()).isEqualTo(name);
    }

    public void exists() {
        if (!Files.exists(actual())) {
            failWithoutActual(simpleFact(actual() + " expected to exist"));
        }
    }

    public void doesNotExist() {
        if (!Files.notExists(actual())) {
            failWithoutActual(simpleFact(actual() + " is not expected to exist"));
        }
    }

    public void isFile() {
        if (!Files.isRegularFile(actual())) {
            failWithoutActual(simpleFact(actual() + " expected to be a regular file"));
        }
    }

    public void isDirectory() {
        if (!Files.isDirectory(actual())) {
            failWithoutActual(simpleFact(actual() + " expected to be a directory"));
        }
    }

    public void isReadable() {
        if (!Files.isReadable(actual())) {
            failWithoutActual(simpleFact(actual() + " expected to be readable"));
        }
    }

    public void isWritable() {
        if (!Files.isWritable(actual())) {
            failWithoutActual(simpleFact(actual() + " expected to be writable"));
        }
    }

    public void isExecutable() {
        if (!Files.isExecutable(actual())) {
            failWithoutActual(simpleFact(actual() + " expected to be executable"));
        }
    }

    public void hasContents(@NonNull byte[] expectedContents) {
        isFile();

        try {
            byte[] contents = Files.readAllBytes(actual());
            if (!Arrays.equals(contents, expectedContents)) {
                failWithBadResults(
                        "contains",
                        "byte[" + expectedContents.length + "]",
                        "is",
                        "byte[" + contents.length + "]");
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to read " + actual()));
        }
    }

    public void hasContents(@NonNull String... expectedContents) throws IOException {
        isFile();

        try {
            List<String> contents = Files.readAllLines(actual());
            if (!Arrays.asList(expectedContents).equals(contents)
                    && !(expectedContents.length == 1
                            && contents.size() != 1
                            && expectedContents[0].equals(LINE_JOINER.join(contents)))) {
                failWithBadResults(
                        "contains",
                        LINE_JOINER.join(expectedContents),
                        "is",
                        LINE_JOINER.join(contents));
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to read " + actual()));
        }
    }

    public void contentWithUnixLineSeparatorsIsExactly(@NonNull String expected) {
        isFile();

        try {
            String contents = LINE_JOINER.join(Files.readAllLines(actual()));
            Truth.assertThat(contents).isEqualTo(expected);
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to read " + actual()));
        }
    }

    public void contains(@NonNull String expectedContent) {
        containsAllOf(expectedContent);
    }

    public void containsAllOf(@NonNull String... expectedContents) {
        isFile();

        try {
            String contents = new String(Files.readAllBytes(actual()), UTF_8);
            for (String expectedContent : expectedContents) {
                if (!contents.contains(expectedContent)) {
                    failWithBadResults("contains", expectedContent, "is", contents);
                }
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to read " + actual()));
        }
    }

    public void doesNotContain(@NonNull String expectedContent) {
        isFile();

        try {
            String contents = new String(Files.readAllBytes(actual()), UTF_8);
            if (contents.contains(expectedContent)) {
                failWithBadResults("does not contains", expectedContent, "is", contents);
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to read " + actual()));
        }
    }

    public void wasModifiedAt(@NonNull FileTime expectedTime) {
        exists();

        try {
            FileTime actualTime = Files.getLastModifiedTime(actual());
            if (!actualTime.equals(expectedTime)) {
                failWithBadResults(
                        "was last modified at", expectedTime, "was last modified at", actualTime);
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to obtain last modified time of " + actual()));
        }
    }

    public void wasModifiedAt(long expectedTimeMillis) {
        exists();

        try {
            long actualTimeMillis = Files.getLastModifiedTime(actual()).toMillis();
            if (actualTimeMillis != expectedTimeMillis) {
                failWithBadResults(
                        "was last modified at", FileTime.fromMillis(expectedTimeMillis),
                        "was last modified at", FileTime.fromMillis(actualTimeMillis));
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to obtain last modified time of " + actual()));
        }
    }

    public void isNewerThan(@NonNull FileTime expectedTime) {
        exists();

        try {
            FileTime actualTime = Files.getLastModifiedTime(actual());
            if (actualTime.compareTo(expectedTime) <= 0) {
                failWithBadResults(
                        "was modified after", expectedTime, "was last modified at", actualTime);
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to obtain last modified time of " + actual()));
        }
    }

    public void isNewerThan(long expectedTimeMillis) {
        exists();

        try {
            long actualTimeMillis = Files.getLastModifiedTime(actual()).toMillis();
            if (actualTimeMillis < expectedTimeMillis) {
                failWithBadResults(
                        "was modified after", FileTime.fromMillis(expectedTimeMillis),
                        "was last modified at", FileTime.fromMillis(actualTimeMillis));
            }
        } catch (IOException e) {
            failWithoutActual(simpleFact("Unable to obtain last modified time of " + actual()));
        }
    }

    /** Asserts that the directory tree contains a file with the given name. */
    public void containsFile(@NonNull String fileName) {
        isDirectory();

        Path found =  findInDirectoryTree(fileName);
        if (found == null) {
            failWithoutActual(
                    simpleFact("Directory tree with root at " + actual() +
                            " is expected to contain " + fileName));
        }
    }

    /** Asserts that the directory tree does not contains a file with the given name. */
    public void doesNotContainFile(@NonNull String fileName) {
        isDirectory();

        Path found =  findInDirectoryTree(fileName);
        if (found != null) {
            failWithoutActual(
                    simpleFact("Directory tree with root at " + actual() +
                            " is not expected to contain " + found));

        }
    }

    @Nullable
    private Path findInDirectoryTree(@NonNull String fileName) {
        Path[] found = { null };
        try {
            Files.walkFileTree(actual(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(fileName)) {
                        found[0] = file;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            failWithoutActual(simpleFact("Failed to read directory tree " + actual() + " " +
                    e.getMessage()));
        }
        return found[0];
    }
}
