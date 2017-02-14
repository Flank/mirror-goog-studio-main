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
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RuntimeAnnotatedClassCollectorTest {

    private static final byte[] KEPT_CLASS = new byte[] {1};
    private static final byte[] NOT_KEPT_CLASS = new byte[] {0};
    private static final byte[] NOT_A_CLASS = new byte[] {-1};

    @Parameterized.Parameters(name = "{0}")
    public static Collection<FileSystemKind> getParameters() {
        return EnumSet.allOf(FileSystemKind.class);
    }

    @NonNull private final FileSystemKind fileSystemKind;
    private final RuntimeAnnotatedClassCollector collector;
    private Path topDir;

    public RuntimeAnnotatedClassCollectorTest(@NonNull FileSystemKind fileSystemKind)
            throws InterruptedException {
        this.fileSystemKind = fileSystemKind;
        collector =
                new RuntimeAnnotatedClassCollector(
                        RuntimeAnnotatedClassCollectorTest::keepClassTest);
    }

    @Before
    public void createFileSystem() throws IOException {
        topDir = Jimfs.newFileSystem(fileSystemKind.configuration).getPath(fileSystemKind.startDir);
        Files.createDirectory(topDir);
    }

    @Test
    public void checkAllInputTypes() throws Exception {
        //Jar
        Path jar = topDir.resolve("classes.jar");
        try (JarOutputStream jarOutputStream =
                new JarOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(jar, StandardOpenOption.CREATE_NEW)))) {
            putEntry(jarOutputStream, "com/example/A.class", KEPT_CLASS);
            putEntry(jarOutputStream, "com/example/B.class", NOT_KEPT_CLASS);
            putEntry(jarOutputStream, "com/example/X.txt", NOT_A_CLASS);
        }

        assertThat(collector.collectClasses(ImmutableList.of(jar)))
                .containsExactly("com/example/A.class");

        // Directory
        Path directory = topDir.resolve("classes");

        writeFile(directory, "com/example/C.class", KEPT_CLASS);
        writeFile(directory, "com/example/D.class", NOT_KEPT_CLASS);
        writeFile(directory, "com/example/Y.txt", NOT_A_CLASS);

        assertThat(collector.collectClasses(ImmutableList.of(directory)))
                .containsExactly("com/example/C.class");

        // Both
        assertThat(collector.collectClasses(ImmutableList.of(jar, directory)))
                .containsExactly("com/example/A.class", "com/example/C.class");

        // Symlinks
        Path directorySymlink = topDir.resolve("classes_dir_symlink");
        Files.createSymbolicLink(directorySymlink, directory);
        Path jarSymlink = topDir.resolve("classes.jar_symlink");
        Files.createSymbolicLink(jarSymlink, jar);
        assertThat(collector.collectClasses(ImmutableList.of(jarSymlink, directorySymlink)))
                .containsExactly("com/example/A.class", "com/example/C.class");
    }

    @Test
    public void checkNonExistentPath() throws InterruptedException {
        //Jar
        Path jar = topDir.resolve("classes.jar");
        try {
            collector.collectClasses(ImmutableList.of(jar));
            fail("Expected to fail");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getClass()).isAssignableTo(IOException.class);
            assertThat(e.getCause().getMessage()).contains(jar.toString());
        }
    }

    @Test
    public void checkSymlinkCycle() throws InterruptedException, IOException {
        //Jar
        Path link1 = topDir.resolve("link1");
        Path link2 = topDir.resolve("link2");
        Files.createSymbolicLink(link1, link2);
        Files.createSymbolicLink(link2, link1);
        try {
            collector.collectClasses(ImmutableList.of(link1));
            fail("Expected to fail");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getClass()).isAssignableTo(IOException.class);
            assertThat(e.getCause().getMessage()).contains(link1.toString());
        }
    }

    private static void putEntry(
            @NonNull JarOutputStream jarOutputStream, @NonNull String name, @NonNull byte[] bytes)
            throws IOException {
        jarOutputStream.putNextEntry(new ZipEntry(name));
        jarOutputStream.write(bytes);
        jarOutputStream.closeEntry();
    }

    private static void writeFile(@NonNull Path root, @NonNull String name, @NonNull byte[] bytes)
            throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static boolean keepClassTest(@NonNull byte[] bytes) {
        if (Arrays.equals(bytes, KEPT_CLASS)) {
            return true;
        }
        if (Arrays.equals(bytes, NOT_KEPT_CLASS)) {
            return false;
        }
        if (Arrays.equals(bytes, NOT_A_CLASS)) {
            throw new RuntimeException("NOT_A_CLASS parsed as a class");
        }
        throw new RuntimeException("Unexpected class content");
    }

    enum FileSystemKind {
        WINDOWS(Configuration.windows(), "C:\\fake\\"),
        UNIX(Configuration.unix(), "/fake/"),
        ;

        @NonNull final Configuration configuration;
        @NonNull final String startDir;

        FileSystemKind(@NonNull Configuration configuration, @NonNull String startDir) {
            this.configuration = configuration;
            this.startDir = startDir;
        }
    }
}
