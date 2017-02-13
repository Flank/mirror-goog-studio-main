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
import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.junit.Assert.fail;

import com.android.testutils.apk.Dex;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Testing the dex archive merger. It takes one or more dex archives as input, and outputs one or
 * more DEX files.
 */
public class DexArchiveMergerTest {

    @ClassRule public static TemporaryFolder allTestsTemporaryFolder = new TemporaryFolder();

    private static final String BIG_CLASS = "BigClass";
    private static Path bigDexArchive;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUp() throws Exception {
        Path inputRoot = allTestsTemporaryFolder.getRoot().toPath().resolve("big_class");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, BIG_CLASS, 65524);

        bigDexArchive = allTestsTemporaryFolder.getRoot().toPath().resolve("big_dex_archive");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, bigDexArchive);
    }

    @Test
    public void test_monoDex_twoDexMerging() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(fstArchive, sndArchive), output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));

        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));
    }

    @Test
    public void test_monoDex_manyDexMerged() throws Exception {
        List<Path> archives = Lists.newArrayList();
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < 100; i++) {
            archives.add(
                    DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                            temporaryFolder.getRoot().toPath().resolve("A" + i), "A" + i));
            expectedClasses.add("A" + i);
        }

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(archives, output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(expectedClasses));
    }

    @Test
    public void test_monoDex_exactLimit() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 9);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive, bigDexArchive), outputDex);

        Dex finalDex = new Dex(outputDex.resolve("classes.dex"));
        assertThat(finalDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(BIG_CLASS, "A"));
    }

    @Test
    public void test_monoDex_aboveLimit() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "B", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        try {
            DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive, bigDexArchive), outputDex);
            fail("Too many methods for mono-dex. Merging should fail.");
        } catch (Exception e) {
            Truth.assertThat(Throwables.getStackTraceAsString(e)).contains("method ID not in");
        }
    }

    @Test
    public void test_legacyMultiDex_mergeTwo() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(fstArchive, sndArchive),
                output,
                ImmutableSet.of(PACKAGE + "/A.class"));

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A"));

        Dex secondaryDex = new Dex(output.resolve("classes2.dex"));
        assertThat(secondaryDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("B"));
    }

    @Test
    public void test_legacyMultiDex_allInMainDex() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(fstArchive, sndArchive),
                output,
                ImmutableSet.of(PACKAGE + "/A.class", PACKAGE + "/B.class"));

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));

        assertThat(output.resolve("classes2.dex")).doesNotExist();
    }

    @Test
    public void test_legacyMultiDex_multipleSecondary() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 1);
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "B", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(dexArchive, bigDexArchive),
                outputDex,
                ImmutableSet.of(PACKAGE + "/A.class"));

        Dex primaryDex = new Dex(outputDex.resolve("classes.dex"));
        assertThat(primaryDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A"));

        assertThat(outputDex.resolve("classes2.dex")).exists();
        assertThat(outputDex.resolve("classes3.dex")).exists();
        assertThat(outputDex.resolve("classes4.dex")).doesNotExist();
    }

    @Test
    public void test_nativeMultiDex_mergeTwo() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(fstArchive, sndArchive), output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));
    }

    @Test
    public void test_nativeMultiDex_multipleDexes() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(dexArchive, bigDexArchive), outputDex);

        assertThat(outputDex.resolve("classes.dex")).exists();
        assertThat(outputDex.resolve("classes2.dex")).exists();
        assertThat(outputDex.resolve("classes3.dex")).doesNotExist();
    }

    @Test
    public void testWindowsSmokeTest() throws Exception {
        FileSystem fs = Jimfs.newFileSystem(Configuration.windows());

        Set<String> classNames = ImmutableSet.of("A", "B", "C");
        Path classesInput = fs.getPath("tmp\\input_classes");
        DexArchiveTestUtil.createClasses(classesInput, classNames);
        Path dexArchive = fs.getPath("tmp\\dex_archive");
        DexArchiveTestUtil.convertClassesToDexArchive(classesInput, dexArchive);

        Path output = fs.getPath("tmp\\out");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive), output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));

        assertThat(outputDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B", "C"));
    }
}
