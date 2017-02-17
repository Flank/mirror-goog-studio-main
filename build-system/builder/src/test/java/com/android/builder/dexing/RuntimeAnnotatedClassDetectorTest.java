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

package com.android.builder.dexing;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.multidex.MainDexListBuilder;
import com.google.common.truth.Truth;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RuntimeAnnotatedClassDetectorTest {

    @NonNull private final Class<?> classUnderTest;
    private final boolean expected;

    @Parameterized.Parameters(name = "assert_hasRuntimeAnnotations({0})=={1}")
    public static Collection<Object> getCases() {
        return Arrays.asList(
                new Object[][] {
                    {ExampleClasses.PlainClass.class, false},
                    {ExampleClasses.AnnotatedClass.class, false},
                    {ExampleClasses.AnnotatedField.class, false},
                    {ExampleClasses.AnnotatedMethod.class, false},
                    {ExampleClasses.AnnotatedConstructor.class, false},
                    {ExampleClasses.RuntimeAnnotatedClass.class, true},
                    {ExampleClasses.RuntimeAnnotatedField.class, true},
                    {ExampleClasses.RuntimeAnnotatedMethod.class, true},
                    {ExampleClasses.RuntimeAnnotatedConstructor.class, true},
                });
    }

    public RuntimeAnnotatedClassDetectorTest(@NonNull Class<?> classUnderTest, boolean expected) {
        this.classUnderTest = classUnderTest;
        this.expected = expected;
    }

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void run() throws Exception {
        byte[] classBytes = ExampleClasses.getBytes(classUnderTest);

        Truth.assertThat(RuntimeAnnotatedClassDetector.hasRuntimeAnnotations(classBytes))
                .named("hasRuntimeAnnotations(" + classUnderTest.getSimpleName() + ")")
                .isEqualTo(expected);

        assertThat(mainDexListKeeps(classBytes, ExampleClasses.getRelativeFilePath(classUnderTest)))
                .named("mainDexListKeeps(" + classUnderTest.getSimpleName() + ")")
                .isEqualTo(expected);
    }

    private boolean mainDexListKeeps(@NonNull byte[] classBytes, @NonNull String resourcePath)
            throws IOException {
        Path folder = temporaryFolder.newFolder(classUnderTest.getSimpleName()).toPath();
        Path jar = folder.resolve("classes.jar");
        try (JarOutputStream jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jar)))) {
            jarOutputStream.putNextEntry(new ZipEntry(resourcePath));
            jarOutputStream.write(classBytes);
            jarOutputStream.closeEntry();
        }
        Path rootsJar = folder.resolve("entrypoints.jar");
        //noinspection EmptyTryBlock : Empty jar
        try (Closeable ignored =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(rootsJar)))) {}
        Set<String> mainDexList =
                new MainDexListBuilder(true, rootsJar.toString(), jar.toString()).getMainDexList();
        boolean kept = mainDexList.contains(resourcePath);
        assertThat(mainDexList).named("Dx main dex list sanity").hasSize(kept ? 1 : 0);
        return kept;
    }
}
