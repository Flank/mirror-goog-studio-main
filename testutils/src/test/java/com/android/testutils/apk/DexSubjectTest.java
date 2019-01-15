/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.testutils.apk;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.testutils.truth.DexSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for the {@link com.android.testutils.truth.DexSubject}. */
public class DexSubjectTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkContains() throws IOException {
        DexSubject subject = getSubject();

        subject.containsClasses("Lcom/example/Foo;");
    }

    @Test
    public void checkDoesNotContain() throws IOException {
        DexSubject subject = getSubject();

        subject.doesNotContainClasses("Lcom/example/Unexpected;");
    }

    @Test
    public void checkContainsClassesIn() throws IOException {
        DexSubject subject = getSubject();

        subject.containsClassesIn(ImmutableList.of("Lcom/example/Foo;"));
    }

    @Test
    public void checkContainsExactly() throws IOException {
        DexSubject subject = getSubject();

        subject.containsExactlyClassesIn(ImmutableList.of("Lcom/example/Foo;"));
    }

    @Test
    public void checkContainsFailureMessage() throws IOException {
        DexSubject subject = getSubject();

        try {
            subject.containsClasses("Lcom/example/NotContained;");
        } catch (AssertionError e) {
            String message = e.getMessage();
            Truth.assertThat(message)
                    .isEqualTo(
                            "Not true that <dex file> contains classes "
                                    + "<[Lcom/example/NotContained;]>. "
                                    + "It is missing <[Lcom/example/NotContained;]>");
        }
    }

    @Test
    public void checkContainsExactlyFailureMessage() throws IOException {
        DexSubject subject = getSubject();

        try {
            subject.containsExactlyClassesIn(ImmutableList.of("Lcom/example/NotContained;"));
        } catch (AssertionError e) {
            String message = e.getMessage();
            Truth.assertThat(message)
                    .isEqualTo(
                            "Not true that <dex file> contains exactly "
                                    + "<[Lcom/example/NotContained;]>. It is missing "
                                    + "<[Lcom/example/NotContained;]> and has unexpected items "
                                    + "<[Lcom/example/Foo;]>");
        }
    }

    private DexSubject getSubject() throws IOException {
        Path dexPath = temporaryFolder.newFile("dex.dex").toPath();
        Files.write(
                dexPath,
                TestDataCreator.dexFile("com.example.Foo"),
                StandardOpenOption.TRUNCATE_EXISTING);

        Dex dex = new Dex(dexPath);

        return assertThat(dex);
    }
}
