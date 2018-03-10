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

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Truth support for validating File.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")  // Functions do not return.
public class FileSubject extends Subject<FileSubject, File> {

    public static final SubjectFactory<FileSubject, File> FACTORY =
            new SubjectFactory<FileSubject, File>() {
                @Override
                public FileSubject getSubject(FailureStrategy fs, File that) {
                    return new FileSubject(fs, that);
                }
            };

    public FileSubject(@NonNull FailureStrategy failureStrategy, @Nullable File subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public static FileSubject assertThat(@Nullable File file) {
        return assert_().about(FileSubject.FACTORY).that(file);
    }

    public void hasName(String name) {
        check().that(getSubject().getName()).named(getDisplaySubject()).isEqualTo(name);
    }

    public void exists() {
        if (!getSubject().exists()) {
            fail("exists");
        }
    }

    public void doesNotExist() {
        if (getSubject().exists()) {
            fail("does not exist");
        }
    }

    public void isFile() {
        if (!getSubject().isFile()) {
            fail("is a file");
        }
    }

    public void isDirectory() {
        if (!getSubject().isDirectory()) {
            fail("is a directory");
        }
    }

    public void contains(String expectedContent) {
        containsAllOf(expectedContent);
    }

    public void containsAllOf(String... expectedContents) {
        isFile();

        try {
            String contents = Files.toString(getSubject(), Charsets.UTF_8);
            for (String expectedContent : expectedContents) {
                if (!contents.contains(expectedContent)) {
                    failWithBadResults("contains", expectedContent, "is", contents);
                }
            }
        } catch (IOException e) {
            failWithRawMessage("Unable to read %s", getSubject());
        }
    }

    public void contains(byte[] expectedContents) {
        isFile();

        try {
            byte[] contents = Files.toByteArray(getSubject());
            if (!Arrays.equals(contents, expectedContents)) {
                failWithBadResults(
                        "contains",
                        "byte[" + expectedContents.length + "]",
                        "is",
                        "byte[" + contents.length + "]");
            }
        } catch (IOException e) {
            failWithRawMessage("Unable to read %s", getSubject());
        }
    }

    public void doesNotContain(String expectedContent) {
        isFile();

        try {
            String contents = Files.toString(getSubject(), Charsets.UTF_8);
            if (contents.contains(expectedContent)) {
                failWithBadResults("does not contains", expectedContent, "is", contents);
            }
        } catch (IOException e) {
            failWithRawMessage("Unable to read %s", getSubject());
        }
    }

    public void hasContents(String expectedContents) {
        contains(expectedContents.getBytes(Charsets.UTF_8));
    }

    public void isEmpty() {
        hasContents("");
    }

    public void wasModifiedAt(long timestamp) {
        long lastModified = getSubject().lastModified();
        if (getSubject().lastModified() != timestamp) {
            failWithBadResults("was not modified at", timestamp, "was modified at", lastModified);
        }
    }

    public void isNewerThan(long timestamp) {
        long lastModified = getSubject().lastModified();
        if (getSubject().lastModified() <= timestamp) {
            failWithBadResults("is newer than", timestamp, "was modified at", lastModified);
        }
    }

    public void isNewerThan(File other) {
        isNewerThan(other.lastModified());
    }

    public void isNewerThanOrSameAs(long otherTimestamp) {
        long thisTimestamp = getSubject().lastModified();
        if (getSubject().lastModified() < otherTimestamp) {
            failWithBadResults(
                    "is newer than or same as", otherTimestamp, "was modified at", thisTimestamp);
        }
    }

    public void isNewerThanOrSameAs(File other) {
        isNewerThanOrSameAs(other.lastModified());
    }

    public void contentWithUnixLineSeparatorsIsExactly(String expected) throws IOException {
        String actual = FileUtils.loadFileWithUnixLineSeparators(getSubject());
        new StringSubject(failureStrategy, actual).isEqualTo(expected);
    }

    public void containsFile(String fileName) {
        isDirectory();
        if (!FileUtils.find(getSubject(), fileName).isPresent()) {
            fail("Directory ", getSubject(), " does not contain ", fileName);
        }
    }

    public void doesNotContainFile(String fileName) {
        isDirectory();
        if (FileUtils.find(getSubject(), fileName).isPresent()) {
            fail("Directory ", getSubject(), " contains ", fileName);
        }
    }
}
