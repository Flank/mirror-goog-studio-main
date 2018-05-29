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

package com.android.ide.common.symbols;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.testutils.TestResources;
import com.android.testutils.truth.FileSubject;
import com.android.testutils.truth.PathSubject;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SymbolIoTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testSingleInt() throws Exception {
        String r = "int xml authenticator 0x7f040000\n";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(r);

        SymbolTable table = SymbolIo.readFromAapt(file, null);

        SymbolTable expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "xml", "authenticator", "int", "0x7f040000"))
                        .build();
        assertEquals(expected, table);
    }

    @Test
    public void testStyleables() throws Exception {
        String r =
                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n"
                        + "int styleable LimitedSizeLinearLayout_max_height 1\n"
                        + "int styleable LimitedSizeLinearLayout_max_width 0\n"
                        + "int xml authenticator 0x7f040000\n";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(r);

        SymbolTable table = SymbolIo.readFromAapt(file, null);

        SymbolTable expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001 }",
                                        ImmutableList.of("max_width", "max_height")))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "xml", "authenticator", "int", "0x7f040000"))
                        .build();
        assertEquals(expected, table);
    }

    @Test
    public void testStyleableWithAndroidAttr() throws Exception {
        String r =
                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x80010013 }\n"
                        + "int styleable LimitedSizeLinearLayout_max_height 0\n"
                        + "int styleable LimitedSizeLinearLayout_android_foo 1\n"
                        + "int xml authenticator 0x7f040000\n";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(r);

        SymbolTable table = SymbolIo.readFromAapt(file, null);

        SymbolTable expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x80010013 }",
                                        ImmutableList.of("max_height", "android:foo")))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "xml", "authenticator", "int", "0x7f040000"))
                        .build();
        assertEquals(expected, table);
    }

    @Test
    public void testStyleablesFromAapt() throws Exception {
        String r =
                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n"
                        + "int styleable LimitedSizeLinearLayout_a 1\n"
                        + "int styleable LimitedSizeLinearLayout_b 0\n";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(r);

        SymbolTable table = SymbolIo.readFromAapt(file, null);

        SymbolTable expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001 }",
                                        ImmutableList.of("b", "a")))
                        .build();
        assertEquals(expected, table);
    }

    @Test
    public void testDoesNotThrowNumberFormatExeption() throws Exception {
        String r =
                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n"
                        + "int styleable LimitedSizeLinearLayout_a NOT_AN_INT!\n"
                        + "int styleable LimitedSizeLinearLayout_b 0\n";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(r);
        try {
            SymbolIo.readFromAapt(file, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e.getMessage()).contains("LimitedSizeLinearLayout");
        }
    }

    @Test
    public void testInvalidType() throws Exception {
        String r = "INVALID_TYPE xml LimitedSizeLinearLayout 0x7f010000\n";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(r);
        try {
            SymbolIo.readFromAapt(file, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e.getMessage()).contains("LimitedSizeLinearLayout");
        }
    }

    @Test
    public void writeReadSymbolFile() throws Exception {
        SymbolTable original =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "b", "int", "d"))
                        .add(SymbolTestUtils.createSymbol("string", "f", "int", "h"))
                        .build();

        File f = mTemporaryFolder.newFile();

        SymbolIo.writeForAar(original, f);
        SymbolTable copy = SymbolIo.readFromAapt(f, null);

        assertEquals(original, copy);
    }

    @Test
    public void readStyleablesWithClashingPrefixes() throws Exception {
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, StandardCharsets.UTF_8)
                .write(
                        "int[] styleable a { 0x1 }\n"
                                + "int styleable a_b 0\n"
                                + "int[] styleable a_c { 0x2 }\n"
                                + "int styleable a_c_d 0");
        SymbolTable table = SymbolIo.readFromAapt(file, null);
        assertThat(table)
                .isEqualTo(
                        SymbolTable.builder()
                                .add(
                                        new Symbol.StyleableSymbol(
                                                "a", ImmutableList.of(0x1), ImmutableList.of("b")))
                                .add(
                                        new Symbol.StyleableSymbol(
                                                "a_c",
                                                ImmutableList.of(0x2),
                                                ImmutableList.of("d")))
                                .build());
    }

    private static void checkFileGeneration(
            @NonNull String expected, @NonNull FileSupplier generator) throws Exception {
        File result = generator.get();
        assertTrue(result.isFile());
        String contents = Joiner.on("\n").join(Files.readLines(result, Charsets.UTF_8));
        assertEquals(expected, contents);
    }

    private interface FileSupplier {
        File get() throws IOException;
    }

    private void checkRGeneration(
            @NonNull String expected,
            @NonNull Path path,
            @NonNull SymbolTable table,
            boolean finalIds)
            throws Exception {
        checkFileGeneration(
                expected,
                () -> {
                    File directory;
                    try {
                        directory = mTemporaryFolder.newFolder();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }

                    SymbolIo.exportToJava(table, directory, finalIds);
                    return directory.toPath().resolve(path).toFile();
                });
    }

    @Test
    public void rGenerationTest1() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .tablePackage("test.pkg")
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "xml", "authenticator", "int", "0x7f040000"))
                        .build();

        checkRGeneration(
                "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                        + " *\n"
                        + " * This class was automatically generated by the\n"
                        + " * gradle plugin from the resource data it found. It\n"
                        + " * should not be modified by hand.\n"
                        + " */\n"
                        + "package test.pkg;\n"
                        + "\n"
                        + "public final class R {\n"
                        + "    private R() {}\n"
                        + "\n"
                        + "    public static final class xml {\n"
                        + "        private xml() {}\n"
                        + "\n"
                        + "        public static final int authenticator = 0x7f040000;\n"
                        + "    }\n"
                        + "}",
                Paths.get("test", "pkg", "R.java"),
                table,
                true);
    }

    @Test
    public void rGenerationTest2() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .tablePackage("test.pkg")
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "string", "app_name", "int", "0x7f030000"))
                        .add(SymbolTestUtils.createSymbol("string", "lib1", "int", "0x7f030001"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "style", "AppBaseTheme", "int", "0x7f040000"))
                        .add(SymbolTestUtils.createSymbol("style", "AppTheme", "int", "0x7f040001"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "foobar", "int", "0x7f020000"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "ic_launcher", "int", "0x7f020001"))
                        .build();

        checkRGeneration(
                "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                        + " *\n"
                        + " * This class was automatically generated by the\n"
                        + " * gradle plugin from the resource data it found. It\n"
                        + " * should not be modified by hand.\n"
                        + " */\n"
                        + "package test.pkg;\n"
                        + "\n"
                        + "public final class R {\n"
                        + "    private R() {}\n"
                        + "\n"
                        + "    public static final class drawable {\n"
                        + "        private drawable() {}\n"
                        + "\n"
                        + "        public static final int foobar = 0x7f020000;\n"
                        + "        public static final int ic_launcher = 0x7f020001;\n"
                        + "    }\n"
                        + "    public static final class string {\n"
                        + "        private string() {}\n"
                        + "\n"
                        + "        public static final int app_name = 0x7f030000;\n"
                        + "        public static final int lib1 = 0x7f030001;\n"
                        + "    }\n"
                        + "    public static final class style {\n"
                        + "        private style() {}\n"
                        + "\n"
                        + "        public static final int AppBaseTheme = 0x7f040000;\n"
                        + "        public static final int AppTheme = 0x7f040001;\n"
                        + "    }\n"
                        + "}",
                Paths.get("test", "pkg", "R.java"),
                table,
                true);
    }

    @Test
    public void rGenerationTestNonFinalIds() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .tablePackage("test.pkg")
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "string", "app_name", "int", "0x7f030000"))
                        .add(SymbolTestUtils.createSymbol("string", "lib1", "int", "0x7f030001"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "style", "AppBaseTheme", "int", "0x7f040000"))
                        .add(SymbolTestUtils.createSymbol("style", "AppTheme", "int", "0x7f040001"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "foobar", "int", "0x7f020000"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "ic_launcher", "int", "0x7f020001"))
                        .build();

        checkRGeneration(
                "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                        + " *\n"
                        + " * This class was automatically generated by the\n"
                        + " * gradle plugin from the resource data it found. It\n"
                        + " * should not be modified by hand.\n"
                        + " */\n"
                        + "package test.pkg;\n"
                        + "\n"
                        + "public final class R {\n"
                        + "    private R() {}\n"
                        + "\n"
                        + "    public static final class drawable {\n"
                        + "        private drawable() {}\n"
                        + "\n"
                        + "        public static int foobar = 0x7f020000;\n"
                        + "        public static int ic_launcher = 0x7f020001;\n"
                        + "    }\n"
                        + "    public static final class string {\n"
                        + "        private string() {}\n"
                        + "\n"
                        + "        public static int app_name = 0x7f030000;\n"
                        + "        public static int lib1 = 0x7f030001;\n"
                        + "    }\n"
                        + "    public static final class style {\n"
                        + "        private style() {}\n"
                        + "\n"
                        + "        public static int AppBaseTheme = 0x7f040000;\n"
                        + "        public static int AppTheme = 0x7f040001;\n"
                        + "    }\n"
                        + "}",
                Paths.get("test", "pkg", "R.java"),
                table,
                false);
    }

    @Test
    public void rGenerationTestStyleablesInDefaultPackage() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "TiledView",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001, 0x7f010002, "
                                                + "0x7f010003, 0x7f010004 }",
                                        ImmutableList.of(
                                                "tilingProperty",
                                                "tilingResource",
                                                "tileName",
                                                "tilingMode",
                                                "tilingEnum")))
                        .build();

        checkRGeneration(
                "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                        + " *\n"
                        + " * This class was automatically generated by the\n"
                        + " * gradle plugin from the resource data it found. It\n"
                        + " * should not be modified by hand.\n"
                        + " */\n"
                        + "\n"
                        + "public final class R {\n"
                        + "    private R() {}\n"
                        + "\n"
                        + "    public static final class styleable {\n"
                        + "        private styleable() {}\n"
                        + "\n"
                        + "        public static final int[] TiledView = "
                        + "{ 0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003, 0x7f010004 };\n"
                        + "        public static final int TiledView_tilingProperty = 0;\n"
                        + "        public static final int TiledView_tilingResource = 1;\n"
                        + "        public static final int TiledView_tileName = 2;\n"
                        + "        public static final int TiledView_tilingMode = 3;\n"
                        + "        public static final int TiledView_tilingEnum = 4;\n"
                        + "    }\n"
                        + "}",
                Paths.get("R.java"),
                table,
                true);
    }

    @Test
    public void testStyleables2() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .tablePackage("test.pkg")
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001 }",
                                        ImmutableList.of("max_width", "max_height")))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "xml", "authenticator", "int", "0x7f040000"))
                        .build();

        checkRGeneration(
                "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                        + " *\n"
                        + " * This class was automatically generated by the\n"
                        + " * gradle plugin from the resource data it found. It\n"
                        + " * should not be modified by hand.\n"
                        + " */\n"
                        + "package test.pkg;\n"
                        + "\n"
                        + "public final class R {\n"
                        + "    private R() {}\n"
                        + "\n"
                        + "    public static final class styleable {\n"
                        + "        private styleable() {}\n"
                        + "\n"
                        + "        public static final int[] LimitedSizeLinearLayout = "
                        + "{ 0x7f010000, 0x7f010001 };\n"
                        + "        public static final int LimitedSizeLinearLayout_max_width = 0;\n"
                        + "        public static final int LimitedSizeLinearLayout_max_height = 1;\n"
                        + "    }\n"
                        + "    public static final class xml {\n"
                        + "        private xml() {}\n"
                        + "\n"
                        + "        public static final int authenticator = 0x7f040000;\n"
                        + "    }\n"
                        + "}",
                Paths.get("test", "pkg", "R.java"),
                table,
                true);
    }

    @Test
    public void writeRTxtGeneration() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001 }",
                                        ImmutableList.of("max_width", "max_height")))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "string", "app_name", "int", "0x7f030000"))
                        .add(SymbolTestUtils.createSymbol("string", "lib1", "int", "0x7f030001"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "style", "AppBaseTheme", "int", "0x7f040000"))
                        .add(SymbolTestUtils.createSymbol("style", "AppTheme", "int", "0x7f040001"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "foobar", "int", "0x7f020000"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "TiledView",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001, 0x7f010002, "
                                                + "0x7f010003, 0x7f010004 }",
                                        ImmutableList.of(
                                                "tilingProperty",
                                                "tilingResource",
                                                "tileName",
                                                "tilingMode",
                                                "tilingEnum")))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "ic_launcher", "int", "0x7f020001"))
                        .build();

        String original =
                ""
                        + "int drawable foobar 0x7f020000\n"
                        + "int drawable ic_launcher 0x7f020001\n"
                        + "int string app_name 0x7f030000\n"
                        + "int string lib1 0x7f030001\n"
                        + "int style AppBaseTheme 0x7f040000\n"
                        + "int style AppTheme 0x7f040001\n"
                        + "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n"
                        + "int styleable LimitedSizeLinearLayout_max_width 0\n"
                        + "int styleable LimitedSizeLinearLayout_max_height 1\n"
                        + "int[] styleable TiledView { 0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003, 0x7f010004 }\n"
                        + "int styleable TiledView_tilingProperty 0\n"
                        + "int styleable TiledView_tilingResource 1\n"
                        + "int styleable TiledView_tileName 2\n"
                        + "int styleable TiledView_tilingMode 3\n"
                        + "int styleable TiledView_tilingEnum 4";
        checkFileGeneration(
                original,
                () -> {
                    File f;
                    try {
                        f = mTemporaryFolder.newFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                    SymbolIo.writeForAar(table, f);
                    return f;
                });
    }

    @Test
    public void checkReadWithCrLf() throws IOException {
        File txt = mTemporaryFolder.newFile();
        String content =
                "int drawable foobar 0x7f02000 \r\n"
                        + "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 } \r\n"
                        + "int styleable LimitedSizeLinearLayout_max_width 0 \r\n"
                        + "int styleable LimitedSizeLinearLayout_max_height 1 \r\n";
        Files.asCharSink(txt, Charsets.UTF_8).write(content);
        SymbolTable table = SymbolIo.readFromAapt(txt, "com.example.app");
        assertThat(table.getSymbols().values())
                .containsExactly(
                        new Symbol.NormalSymbol(ResourceType.DRAWABLE, "foobar", 0x7f02000),
                        new Symbol.StyleableSymbol(
                                "LimitedSizeLinearLayout",
                                ImmutableList.of(0x7f010000, 0x7f010001),
                                ImmutableList.of("max_width", "max_height")));
    }

    @Test
    public void checkWriteWithAndroidNamespace() throws Exception {
        SymbolTable table =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001 }",
                                        ImmutableList.of(
                                                "android:max_width", "android:max_height")))
                        .build();

        String original =
                ""
                        + "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n"
                        + "int styleable LimitedSizeLinearLayout_android_max_width 0\n"
                        + "int styleable LimitedSizeLinearLayout_android_max_height 1";
        checkFileGeneration(
                original,
                () -> {
                    File f;
                    try {
                        f = mTemporaryFolder.newFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                    SymbolIo.writeForAar(table, f);
                    return f;
                });
    }

    @Test
    public void checkReadWithAndroidNamespace() throws Exception {
        File txt = mTemporaryFolder.newFile();
        String content =
                ""
                        + "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 } \r\n"
                        + "int styleable LimitedSizeLinearLayout_android_max_width 0 \r\n"
                        + "int styleable LimitedSizeLinearLayout_android_max_height 1 \r\n";
        Files.asCharSink(txt, Charsets.UTF_8).write(content);
        SymbolTable table = SymbolIo.readFromAapt(txt, "com.example.app");
        assertThat(table.getSymbols().values())
                .containsExactly(
                        new Symbol.StyleableSymbol(
                                "LimitedSizeLinearLayout",
                                ImmutableList.of(0x7f010000, 0x7f010001),
                                ImmutableList.of("android:max_width", "android:max_height")));
    }

    @Test
    public void testWriteSymbolListWithPackageName() throws Exception {
        assertThat(
                        writeSymbolTableToPackage(
                                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 } ",
                                "int styleable LimitedSizeLinearLayout_android_max_width 0 ",
                                "int styleable LimitedSizeLinearLayout_android_max_height 1 ",
                                "int string app_name 0x7f030000"))
                .containsExactly(
                        "somePackage",
                        "styleable LimitedSizeLinearLayout android_max_width android_max_height",
                        "string app_name")
                .inOrder();
    }

    @Test
    public void testWriteSymbolListWithPackageNameMisordered() throws Exception {
        assertThat(
                        writeSymbolTableToPackage(
                                // Ignored out-of-order styleable child
                                "int styleable LimitedSizeLinearLayout_bad 2 ",
                                // Ignores incomplete lines
                                "ignored",
                                "int ignored",
                                "int string ignored",
                                // Valid stylable
                                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 } ",
                                // Valid child
                                "int styleable LimitedSizeLinearLayout_android_max_width 0 ",
                                // Child ignored as no value
                                "int styleable LimitedSizeLinearLayout_ignored_as_no_value",
                                // Child ignored as not for the same stylable
                                "int styleable OtherBroken_child 2 ",
                                // Valid child
                                "int styleable LimitedSizeLinearLayout_android_max_height 1 ",
                                // Valid string
                                "int string other_string 0x7f030001",
                                // Ignored out-of-order styleable child
                                "int styleable LimitedSizeLinearLayout_android_will_be_ignored 3 ",
                                // valid string
                                "int string app_name 0x7f030000"))
                .containsExactly(
                        "somePackage",
                        "styleable LimitedSizeLinearLayout android_max_width android_max_height",
                        "string other_string",
                        "string app_name")
                .inOrder();
    }

    private static List<String> writeSymbolTableToPackage(String... lines) throws IOException {
        Path tmp = Jimfs.newFileSystem(Configuration.unix()).getPath("/tmp");
        java.nio.file.Files.createDirectories(tmp);
        Path aarRTxt = tmp.resolve("R.txt");
        java.nio.file.Files.write(aarRTxt, Arrays.asList(lines), StandardCharsets.UTF_8);
        Path out = tmp.resolve("with_package.txt");
        SymbolIo.writeSymbolListWithPackageName(aarRTxt, "somePackage", out);
        return java.nio.file.Files.readAllLines(out, StandardCharsets.UTF_8);
    }

    @Test
    public void writeSymbolTableToPackageNoTable() throws IOException {
        Path tmp = Jimfs.newFileSystem(Configuration.unix()).getPath("/tmp");
        java.nio.file.Files.createDirectories(tmp);
        Path aarRTxt = tmp.resolve("R.txt");
        Path out = tmp.resolve("with_package.txt");
        SymbolIo.writeSymbolListWithPackageName(aarRTxt, "somePackage", out);
        PathSubject.assertThat(out).hasContents("somePackage");
    }

    @Test
    public void testPackageNameRead() throws Exception {
        String content =
                "com.example.lib\n"
                        + "drawable foobar\n"
                        + "styleable LimitedSizeLinearLayout child_1 child_2\n"
                        + "styleable S2";
        File file = mTemporaryFolder.newFile();
        Files.asCharSink(file, Charsets.UTF_8).write(content);

        SymbolTable table = SymbolIo.readSymbolListWithPackageName(file.toPath());

        assertThat(table.getTablePackage()).isEqualTo("com.example.lib");
        assertThat(table.getSymbols().values())
                .containsExactly(
                        new Symbol.NormalSymbol(ResourceType.DRAWABLE, "foobar", 0),
                        new Symbol.StyleableSymbol(
                                "LimitedSizeLinearLayout",
                                ImmutableList.of(),
                                ImmutableList.of("child_1", "child_2")),
                        new Symbol.StyleableSymbol("S2", ImmutableList.of(), ImmutableList.of()));
    }

    @Test
    public void testPackageNameWriteAndRead() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();

        SymbolTable table =
                SymbolTable.builder()
                        .tablePackage("com.example.lib")
                        .add(new Symbol.NormalSymbol(ResourceType.DRAWABLE, "foobar", 0x7f02000))
                        .add(
                                new Symbol.StyleableSymbol(
                                        "LimitedSizeLinearLayout",
                                        ImmutableList.of(0x7f010000, 0x7f010001),
                                        ImmutableList.of("max_width", "max_height")))
                        .build();
        Path rTxt = fs.getPath("r.txt");
        SymbolIo.writeForAar(table, rTxt);

        Path manifest = fs.getPath("AndroidManifest.xml");
        java.nio.file.Files.write(
                manifest,
                ImmutableList.of("<manifest package=\"com.example.lib\"></manifest>"),
                StandardCharsets.UTF_8);

        Path output = fs.getPath("package-aware-r.txt");
        SymbolIo.writeSymbolListWithPackageName(rTxt, manifest, output);

        List<String> outputLines = java.nio.file.Files.readAllLines(output, StandardCharsets.UTF_8);
        assertThat(outputLines)
                .containsExactly(
                        "com.example.lib",
                        "drawable foobar",
                        "styleable LimitedSizeLinearLayout max_width max_height")
                .inOrder();

        SymbolTable result = SymbolIo.readSymbolListWithPackageName(output);
        for (ResourceType type : ResourceType.values()) {
            assertThat(result.getSymbols().row(type).keySet())
                    .named(type.getName() + " resources in table " + result)
                    .containsExactlyElementsIn(table.getSymbols().row(type).keySet());
        }

        // Simulate what might happen with AAPT1 on windows.
        Path symbolTable = fs.getPath("preSymbolTable");
        SymbolIo.writeForAar(table, symbolTable);
        Path mixedLineEndings = fs.getPath("withAAPT1onWindows.txt");
        try (Stream<String> lines = java.nio.file.Files.lines(symbolTable);
                BufferedWriter w =
                        java.nio.file.Files.newBufferedWriter(
                                mixedLineEndings, StandardCharsets.UTF_8)) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                w.write(iterator.next());
                w.write("\r\n");
            }
        }
        Path outputWindows = fs.getPath("package-aware-r.txt");
        SymbolIo.writeSymbolListWithPackageName(mixedLineEndings, manifest, output);
        assertThat(java.nio.file.Files.readAllLines(outputWindows))
                .containsExactlyElementsIn(java.nio.file.Files.readAllLines(output))
                .inOrder();
    }

    @Test
    public void testPackageNameWithNoSymbolTableWrite() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();

        Path doesNotExist = fs.getPath("r.txt");

        Path manifest = fs.getPath("AndroidManifest.xml");
        java.nio.file.Files.write(
                manifest,
                ImmutableList.of("<manifest package=\"com.example.lib\"></manifest>"),
                StandardCharsets.UTF_8);

        Path output = fs.getPath("package-aware-r.txt");
        SymbolIo.writeSymbolListWithPackageName(doesNotExist, manifest, output);

        List<String> outputLines = java.nio.file.Files.readAllLines(output, StandardCharsets.UTF_8);
        assertThat(outputLines).containsExactly("com.example.lib");
    }

    @Test
    public void testEmptyPackageAwareSymbolTableRead() throws Exception {
        File file = mTemporaryFolder.newFile();
        FileSubject.assertThat(file).exists();

        try {
            SymbolIo.readSymbolListWithPackageName(file.toPath());
            fail();
        } catch (IOException e) {
            assertThat(e.getMessage())
                    .contains(
                            "Internal error: Symbol file with package cannot be empty. "
                                    + "File located at: "
                                    + file.toPath().toString());
        }
    }


    @Test
    public void testMisorderedAarWithPackage() throws Exception {
        Path misordered =
                TestResources.getFile(SymbolIoTest.class, "/testData/symbolIo/misordered_R.txt")
                        .toPath();
        FileSystem fs = Jimfs.newFileSystem();
        Path manifest = fs.getPath("AndroidManifest.xml");
        java.nio.file.Files.write(
                manifest,
                ImmutableList.of("<manifest package=\"com.example.lib\"></manifest>"),
                StandardCharsets.UTF_8);

        Path output = fs.getPath("package-aware-r.txt");
        SymbolIo.writeSymbolListWithPackageName(misordered, manifest, output);

        SymbolTable symbolTable = SymbolIo.readSymbolListWithPackageName(output);
        Symbol symbol = symbolTable.getSymbols().get(ResourceType.STYLEABLE, "AlertDialog");

        assertThat(symbol)
                .isEqualTo(
                        new Symbol.StyleableSymbol(
                                "AlertDialog",
                                ImmutableList.of(),
                                ImmutableList.of(),
                                ResourceVisibility.UNDEFINED));
    }

    @Test
    public void testRealMisorderedAar() throws Exception {
        File misordered =
                TestResources.getFile(SymbolIoTest.class, "/testData/symbolIo/misordered_R.txt");
        try {
            SymbolIo.readFromAapt(misordered, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e).hasMessageThat().contains("File format error");
            assertThat(e).hasCauseThat().hasMessageThat().contains("Unexpected styleable child");
        }
    }

    @Test
    public void testMisorderedAarNoChildren() throws Exception {
        File misordered = new File(mTemporaryFolder.newFolder(), "other R.txt");
        Files.asCharSink(misordered, Charsets.UTF_8).write("int[] styleable myStyleable {732,733}");
        try {
            SymbolIo.readFromAapt(misordered, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e).hasMessageThat().contains("File format error");
            assertThat(e).hasCauseThat().hasMessageThat().contains("should have 0 item(s).");
        }
    }

    @Test
    public void testMisorderedAarMissingChildren() throws Exception {
        File misordered = new File(mTemporaryFolder.newFolder(), "other R.txt");
        Files.asCharSink(misordered, Charsets.UTF_8)
                .write(
                        "int[] styleable myStyleable {732,733}\n"
                                + "int styleable myStylable_one 1\n");
        try {
            SymbolIo.readFromAapt(misordered, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e).hasMessageThat().contains("File format error");
            assertThat(e).hasCauseThat().hasMessageThat().contains("should have 1 item(s).");
        }
    }

    @Test
    public void testMisorderedAarExtraChildren() throws Exception {
        File misordered = new File(mTemporaryFolder.newFolder(), "other R.txt");
        Files.asCharSink(misordered, Charsets.UTF_8)
                .write(
                        "int[] styleable myStyleable {732, 733}\n"
                                + "int styleable myStylable_one 1\n"
                                + "int styleable myStylable_two 2\n"
                                + "int styleable myStylable_three 3\n");
        try {
            SymbolIo.readFromAapt(misordered, null);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e).hasMessageThat().contains("File format error");
            assertThat(e).hasCauseThat().hasMessageThat().contains("should have 3 item(s).");
        }
    }

    @Test
    public void testPartialRFileRead() throws Exception {
        File partialR = mTemporaryFolder.newFile();
        java.nio.file.Files.write(
                partialR.toPath(),
                ImmutableList.of(
                        "public int drawable img",
                        "default int id bar",
                        "private int string beep",
                        "default int string foo",
                        "default int[] styleable s1",
                        "default int styleable s1_a1",
                        "default int styleable s1_a2",
                        "public int transition t"),
                StandardCharsets.UTF_8);

        SymbolTable table = SymbolIo.readFromPartialRFile(partialR, "foo.bar.com");

        assertThat(table.getTablePackage()).isEqualTo("foo.bar.com");
        assertThat(table.getSymbols().values())
                .containsExactly(
                        new Symbol.NormalSymbol(
                                ResourceType.DRAWABLE, "img", 0, ResourceVisibility.PUBLIC),
                        new Symbol.NormalSymbol(
                                ResourceType.ID, "bar", 0, ResourceVisibility.PRIVATE_XML_ONLY),
                        new Symbol.NormalSymbol(
                                ResourceType.STRING, "beep", 0, ResourceVisibility.PRIVATE),
                        new Symbol.NormalSymbol(
                                ResourceType.STRING, "foo", 0, ResourceVisibility.PRIVATE_XML_ONLY),
                        new Symbol.StyleableSymbol(
                                "s1",
                                ImmutableList.of(),
                                ImmutableList.of("a1", "a2"),
                                ResourceVisibility.PRIVATE_XML_ONLY),
                        new Symbol.NormalSymbol(
                                ResourceType.TRANSITION, "t", 0, ResourceVisibility.PUBLIC));
    }

    @Test
    public void testPublicRFileRead() throws Exception {
        File publicTxt = mTemporaryFolder.newFile();
        java.nio.file.Files.write(
                publicTxt.toPath(),
                ImmutableList.of(
                        "attr color",
                        "attr size",
                        "string publicString",
                        "integer value",
                        "styleable myStyleable"),
                StandardCharsets.UTF_8);

        SymbolTable table = SymbolIo.readFromPublicTxtFile(publicTxt, "foo.bar.com");

        // Sanity check for the package.
        assertThat(table.getTablePackage()).isEqualTo("foo.bar.com");
        //Check the size.
        assertThat(table.getSymbols().values().size()).isEqualTo(5);
        // Make sure all are public.
        assertThat(table.getSymbols().values().size())
                .isEqualTo(table.getSymbolByVisibility(ResourceVisibility.PUBLIC).size());

        assertThat(table.getSymbols().values())
                .containsExactly(
                        new Symbol.NormalSymbol(
                                ResourceType.ATTR, "color", 0, ResourceVisibility.PUBLIC),
                        new Symbol.NormalSymbol(
                                ResourceType.ATTR, "size", 0, ResourceVisibility.PUBLIC),
                        new Symbol.NormalSymbol(
                                ResourceType.STRING, "publicString", 0, ResourceVisibility.PUBLIC),
                        new Symbol.NormalSymbol(
                                ResourceType.INTEGER, "value", 0, ResourceVisibility.PUBLIC),
                        new Symbol.StyleableSymbol(
                                "myStyleable",
                                ImmutableList.of(),
                                ImmutableList.of(),
                                ResourceVisibility.PUBLIC));
    }
}
