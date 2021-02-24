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

import static com.android.builder.dexing.DexArchiveTestUtil.PACKAGE;
import static com.android.testutils.TestClassesGenerator.rewriteToVersion;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.testutils.apk.Dex;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for the {@link DexArchiveBuilder} that processes the class files and outputs dex archives.
 * It tests all possible combinations of class input formats and dex output formats.
 */
@RunWith(Parameterized.class)
public class DexArchiveBuilderTest {

    enum ClassesInputFormat {
        DIR,
        JAR
    }

    enum DexArchiveFormat {
        DIR,
        JAR
    }

    static class ClassWithAssertions {
        public static void foo() {
            assert 1 > System.currentTimeMillis();
        }
    }

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}_{1}")
    public static Collection<Object[]> setups() {
        return ImmutableList.of(
                new Object[] {ClassesInputFormat.DIR, DexArchiveFormat.DIR},
                new Object[] {ClassesInputFormat.DIR, DexArchiveFormat.JAR},
                new Object[] {ClassesInputFormat.JAR, DexArchiveFormat.DIR},
                new Object[] {ClassesInputFormat.JAR, DexArchiveFormat.JAR});
    }

    @NonNull private final ClassesInputFormat inputFormat;
    @NonNull private final DexArchiveFormat outputFormat;

    public DexArchiveBuilderTest(
            @NonNull ClassesInputFormat inputFormat, @NonNull DexArchiveFormat outputFormat) {
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
    }

    @Test
    public void checkInputIsProcessed() throws Exception {
        Collection<String> classesInInput = ImmutableList.of("A", "B", "C");
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);

        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, classesInInput);
        }
    }

    @Test
    public void checkEmptyInput() throws Exception {
        Path emptyInput = writeToInput(ImmutableList.of());
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(emptyInput, output);

        if (outputFormat == DexArchiveFormat.JAR) {
            assertThat(output).doesNotExist();
        } else {
            assertThat(Files.list(output).count()).isEqualTo(0);
        }
    }

    @Test
    public void checkDexArchiveIncrementallyUpdated() throws Exception {
        Assume.assumeTrue(outputFormat == DexArchiveFormat.DIR);
        Collection<String> classesInInput = ImmutableList.of("A", "B", "C");
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);

        // add new file
        writeToInput(ImmutableList.of("D"));

        // trigger conversion again
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);
        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, ImmutableList.of("A", "B", "C", "D"));
        }

        // add another file
        writeToInput(ImmutableList.of("F"));

        // trigger conversion again
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);
        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, ImmutableList.of("A", "B", "C", "D", "F"));
        }
    }

    @Test
    public void checkManyClasses() throws Exception {
        Collection<String> classesInInput = Lists.newArrayList();
        for (int i = 0; i < 1000; i++) {
            classesInInput.add("A" + i);
        }
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);

        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, classesInInput);
        }
    }

    @Test
    public void checkDexEntriesRenaming() {
        assertThat(DexArchiveEntry.withClassExtension("A.dex")).isEqualTo("A.class");
        assertThat(DexArchiveEntry.withClassExtension("A$a.dex")).isEqualTo("A$a.class");
        assertThat(DexArchiveEntry.withClassExtension("/A.dex")).isEqualTo("/A.class");
        assertThat(DexArchiveEntry.withClassExtension("a/A.dex")).isEqualTo("a/A.class");
        assertThat(DexArchiveEntry.withClassExtension("a/.dex/A.dex")).isEqualTo("a/.dex/A.class");
        assertThat(DexArchiveEntry.withClassExtension("a\\.dex\\A.dex"))
                .isEqualTo("a\\.dex\\A.class");
        assertThat(DexArchiveEntry.withClassExtension("a\\A.dex")).isEqualTo("a\\A.class");

        try {
            DexArchiveEntry.withClassExtension("Failure.txt");
            fail();
        } catch (IllegalStateException e) {
            // should throw
        }
    }

    @Test
    public void checkWindowsPathsDoesNotFail() throws Exception {
        Collection<String> classesInInput = ImmutableList.of("A", "B", "C");

        FileSystem fs = Jimfs.newFileSystem(Configuration.windows());
        Path input = fs.getPath("tmp\\input");
        Files.createDirectories(input);
        DexArchiveTestUtil.createClasses(input, classesInInput);

        Path output = fs.getPath("tmp\\output");
        Files.createDirectories(output);
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);
    }

    @Test
    public void checkChecksumInfoExists() throws Exception {
        // We are going to build a dex with debug output mode.
        Path input = writeToInput(ImmutableList.of("A"));
        ClassFileInput cfInput = ClassFileInputs.fromPath(input);
        CRC32 crc = new CRC32();
        crc.update(cfInput.entries((x, y) -> true).findFirst().get().readAllBytes());
        String crcHexMatcher = ".*" + Long.toHexString(crc.getValue()) + ".*";
        Path output = createOutput();

        // DexArchiveTestUtil always do debug build which should contains the checksums.
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);
        Dex dex = null;

        // Look into the string contend of the dex file. It should have at least one string
        // that has the CRC in it.
        if (outputFormat == DexArchiveFormat.JAR) {
            Path jar =
                    Iterators.getOnlyElement(
                            Files.walk(output).filter(Files::isRegularFile).iterator());
            try (ZFile zf = ZFile.openReadOnly(jar.toFile())) {
                dex = new Dex(zf.entries().iterator().next().read(), "input.dex");
            }
        } else {
            Path dexFile =
                    Iterators.getOnlyElement(
                            Files.walk(output).filter(Files::isRegularFile).iterator());
            dex = new Dex(dexFile);
        }
        assertThat(dex).containsString(crcHexMatcher);
    }

    @Test
    public void checkDebugInfoExists() throws Exception {
        Assume.assumeTrue(inputFormat == ClassesInputFormat.DIR);
        Assume.assumeTrue(outputFormat == DexArchiveFormat.DIR);
        class DebugInfoClass {

            private void noBody() {}

            private void debugInfoMethod() {
                int x = 10;
            }

            private void anotherMethod() {
                int y = 10;
                int x = 1000;
                debugInfoMethod();
            }
        }
        Path classesDir = temporaryFolder.getRoot().toPath().resolve("classes");
        String path = DebugInfoClass.class.getName().replace('.', '/') + SdkConstants.DOT_CLASS;
        Path outClassFile = classesDir.resolve(path);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            Files.createDirectories(outClassFile.getParent());
            Files.write(outClassFile, rewriteToVersion(51, in));
        }

        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(classesDir, output);

        Path dexFile =
                Iterators.getOnlyElement(
                        Files.walk(output).filter(Files::isRegularFile).iterator());
        String dexClassName = "L" + path.replaceAll("\\.class$", ";");
        Dex dex = new Dex(dexFile);
        assertThat(dex).containsClass(dexClassName).that().hasMethodWithLineInfoCount("noBody", 1);

        assertThat(dex)
                .containsClass(dexClassName)
                .that()
                .hasMethodWithLineInfoCount("debugInfoMethod", 2);

        assertThat(dex)
                .containsClass(dexClassName)
                .that()
                .hasMethodWithLineInfoCount("anotherMethod", 4);
    }

    @Test
    public void testAssertionsForDebugBuilds() throws Exception {
        Assume.assumeTrue(inputFormat == ClassesInputFormat.DIR);
        Assume.assumeTrue(outputFormat == DexArchiveFormat.DIR);

        Path classesDir = temporaryFolder.getRoot().toPath().resolve("classes");
        String path =
                ClassWithAssertions.class.getName().replace('.', '/') + SdkConstants.DOT_CLASS;
        Path outClassFile = classesDir.resolve(path);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            Files.createDirectories(outClassFile.getParent());
            Files.write(outClassFile, ByteStreams.toByteArray(in));
        }

        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(classesDir, output, 24, true);

        Path dexFile =
                Iterators.getOnlyElement(
                        Files.walk(output).filter(Files::isRegularFile).iterator());
        String dexClassName = "L" + path.replaceAll("\\.class$", ";");
        assertThat(new Dex(dexFile))
                .containsClass(dexClassName)
                .that()
                .hasMethodThatInvokes("foo", "Ljava/lang/AssertionError;-><init>()V");

        // now build for release
        FileUtils.cleanOutputDir(output.toFile());
        DexArchiveTestUtil.convertClassesToDexArchive(classesDir, output, 24, false);
        assertThat(new Dex(dexFile))
                .containsClass(dexClassName)
                .that()
                .hasMethodThatDoesNotInvoke("foo", "Ljava/lang/AssertionError;-><init>()V");
    }

    @NonNull
    private Path writeToInput(@NonNull Collection<String> classesInInput) throws Exception {
        Path input;
        if (inputFormat == ClassesInputFormat.JAR) {
            input = temporaryFolder.getRoot().toPath().resolve("input.jar");
        } else {
            input = temporaryFolder.getRoot().toPath().resolve("input");
        }
        DexArchiveTestUtil.createClasses(input, classesInInput);
        return input;
    }

    private Path createOutput() throws IOException {
        if (outputFormat == DexArchiveFormat.DIR) {
            return temporaryFolder.getRoot().toPath().resolve("output");
        } else {
            return temporaryFolder.getRoot().toPath().resolve("output.jar");
        }
    }

    private void assertArchiveIsValid(
            @NonNull DexArchive dexArchive, @NonNull Collection<String> classNames)
            throws IOException {
        Set<String> classesInArchive;
        File output = new File(dexArchive.getRootPath().toString());
        if (!output.exists()) {
            assertThat(classNames).isEmpty();
            return;
        }
        if (outputFormat == DexArchiveFormat.JAR) {
            try (ZFile jarFile = ZFile.openReadOnly(dexArchive.getRootPath().toFile())) {
                classesInArchive =
                        jarFile.entries()
                                .stream()
                                .map(e -> e.getCentralDirectoryHeader().getName())
                                .map(DexArchiveBuilderTest::getClassNameWithoutPackage)
                                .collect(Collectors.toSet());
            }
        } else {
            classesInArchive =
                    Files.walk(dexArchive.getRootPath())
                            .filter(Files::isRegularFile)
                            .map(PathUtils::toSystemIndependentPath)
                            .map(DexArchiveBuilderTest::getClassNameWithoutPackage)
                            .collect(Collectors.toSet());
        }
        assertThat(classesInArchive).containsExactlyElementsIn(classNames);

        for (DexArchiveEntry entry : dexArchive.getSortedDexArchiveEntries()) {
            byte[] dexClass = entry.getDexFileContent();
            Dex dex = new Dex(dexClass, entry.getRelativePathInArchive());

            String className = getClassNameWithoutPackage(entry.getRelativePathInArchive());
            assertThat(dex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(className));
        }
    }

    private static String getClassNameWithoutPackage(@NonNull String dexEntryPath) {
        return dexEntryPath.replaceAll(".*" + PACKAGE + "/(.*)\\.dex", "$1");
    }
}
