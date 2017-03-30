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
import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.apkzlib.zip.ZFile;
import com.android.testutils.apk.Dex;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    interface TestStaticAndDefault {
        default void noBody() {}

        static void staticMethod() {}

        static void staticMethodTwo(TestStaticAndDefault t) {
            t.noBody();
            staticMethod();
        }
    }

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "input_{0}_output_{1}")
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

        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, ImmutableList.of());
        }
    }

    @Test
    public void checkInputClassesMoreThanThreads() throws Exception {
        Collection<String> classesInInput = ImmutableList.of("A", "B", "C", "D", "E", "F");
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);

        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, classesInInput);
        }
    }

    @Test
    public void checkDexArchiveIncrementallyUpdated() throws Exception {
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
    public void checkRemovingDexEntries() throws Exception {
        Collection<String> classesInInput = ImmutableList.of("A", "B", "C");
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);

        // remove the file, we close it to make sure it is written to disk
        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            dexArchive.removeFile(Paths.get(PACKAGE + "/B.dex"));
        }

        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, ImmutableList.of("A", "C"));
        }
    }

    @Test
    public void checkRemovingAllEntries() throws Exception {
        Collection<String> classesInInput = ImmutableList.of("A", "B", "C");
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(input, output);

        // remove the file, we close it to make sure it is written to disk
        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            dexArchive.removeFile(Paths.get(PACKAGE + "/A.dex"));
            dexArchive.removeFile(Paths.get(PACKAGE + "/B.dex"));
            dexArchive.removeFile(Paths.get(PACKAGE + "/C.dex"));
        }

        try (DexArchive dexArchive = DexArchives.fromInput(output)) {
            assertArchiveIsValid(dexArchive, ImmutableList.of());
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
    public void checkInvalidOutput() throws Exception {
        Collection<String> classesInInput = ImmutableList.of("A");
        Path input = writeToInput(classesInInput);
        Path output = createOutput();
        Files.write(output, Lists.newArrayList("invalid output"));
        try {
            DexArchiveTestUtil.convertClassesToDexArchive(input, output);
            fail();
        } catch (DexArchiveBuilder.DexBuilderException | IOException e) {
            // it should fail
        }
    }

    @Test
    public void checkDexEntriesRenaming() {
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("A.dex")))
                .isEqualTo(Paths.get("A.class"));
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("A$a.dex")))
                .isEqualTo(Paths.get("A$a.class"));
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("/A.dex")))
                .isEqualTo(Paths.get("/A.class"));
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("a/A.dex")))
                .isEqualTo(Paths.get("a/A.class"));
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("a/.dex/A.dex")))
                .isEqualTo(Paths.get("a/.dex/A.class"));
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("a\\.dex\\A.dex")))
                .isEqualTo(Paths.get("a\\.dex\\A.class"));
        assertThat(DexArchiveEntry.withClassExtension(Paths.get("a\\A.dex")))
                .isEqualTo(Paths.get("a\\A.class"));

        try {
            DexArchiveEntry.withClassExtension(Paths.get("Failure.txt"));
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
    public void checkStaticAndDefaultInterfaceMethods() throws Exception {
        Assume.assumeTrue(inputFormat == ClassesInputFormat.DIR);
        Assume.assumeTrue(outputFormat == DexArchiveFormat.DIR);

        Path classesDir = temporaryFolder.getRoot().toPath().resolve("classes");
        String path =
                TestStaticAndDefault.class.getName().replace('.', '/') + SdkConstants.DOT_CLASS;
        Path outClassFile = classesDir.resolve(path);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            Files.createDirectories(outClassFile.getParent());
            Files.write(outClassFile, ByteStreams.toByteArray(in));
        }

        Path output = createOutput();
        DexArchiveTestUtil.convertClassesToDexArchive(classesDir, output, 24);

        Path dexFile =
                Iterators.getOnlyElement(
                        Files.walk(output).filter(Files::isRegularFile).iterator());
        String dexClassName = "L" + path.replaceAll("\\.class$", ";");
        Dex dex = new Dex(dexFile);
        assertThat(dex).containsClass(dexClassName);

        try {
            DexArchiveTestUtil.convertClassesToDexArchive(classesDir, output, 0);
            fail("Default and static interface method should require min sdk 24.");
        } catch (DexArchiveBuilder.DexBuilderException ignored) {
            Truth.assertThat(Throwables.getStackTraceAsString(ignored))
                    .contains(
                            "default or static interface method used without "
                                    + "--min-sdk-version >= 24");
        }
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

    private Path createOutput() {
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
        if (outputFormat == DexArchiveFormat.JAR) {
            try (ZFile jarFile = new ZFile(dexArchive.getRootPath().toFile())) {
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
                            .map(Path::toString)
                            .map(DexArchiveBuilderTest::getClassNameWithoutPackage)
                            .collect(Collectors.toSet());
        }
        Truth.assertThat(classesInArchive).containsExactlyElementsIn(classNames);

        for (DexArchiveEntry entry : dexArchive.getFiles()) {
            byte[] dexClass = entry.getDexFileContent();
            Dex dex = new Dex(dexClass, entry.getRelativePathInArchive().toString());

            String className =
                    getClassNameWithoutPackage(entry.getRelativePathInArchive().toString());
            assertThat(dex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(className));
        }
    }

    private static String getClassNameWithoutPackage(@NonNull String dexEntryPath) {
        return dexEntryPath.replaceAll(
                ".*" + PACKAGE + Pattern.quote(File.separator) + "(.*)\\.dex", "$1");
    }
}
