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

package com.android.builder.symbols;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.testutils.TestResources;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.jimfs.Jimfs;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;
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
        Files.write(r, file, Charsets.UTF_8);

        SymbolTable table = SymbolIo.read(file, null);

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
        Files.write(r, file, Charsets.UTF_8);

        SymbolTable table = SymbolIo.read(file, null);

        SymbolTable expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout",
                                        "int[]",
                                        "{ 0x7f010000, 0x7f010001 }",
                                        ImmutableList.of("max_height", "max_width")))
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
        Files.write(r, file, Charsets.UTF_8);

        SymbolTable table = SymbolIo.read(file, null);

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
        Files.write(r, file, Charsets.UTF_8);

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
    public void writeReadSymbolFile() throws Exception {
        SymbolTable original =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "b", "int", "d"))
                        .add(SymbolTestUtils.createSymbol("string", "f", "int", "h"))
                        .build();

        File f = mTemporaryFolder.newFile();

        SymbolIo.write(original, f);
        SymbolTable copy = SymbolIo.read(f, null);

        assertEquals(original, copy);
    }

    @Test
    public void writeReadBrokenSymbolFile() throws Exception {
        File R = TestResources.getFile(SymbolIoTest.class, "/testData/symbolIo/misordered_R.txt");
        SymbolIo.read(R, null);
    }

    private static void checkFileGeneration(
            @NonNull String expected, @NonNull Supplier<File> generator) throws Exception {
        File result = generator.get();
        assertTrue(result.isFile());
        String contents = Joiner.on("\n").join(Files.readLines(result, Charsets.UTF_8));
        assertEquals(expected, contents);
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
                        + "    public static final class xml {\n"
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
                        + "    public static final class drawable {\n"
                        + "        public static final int foobar = 0x7f020000;\n"
                        + "        public static final int ic_launcher = 0x7f020001;\n"
                        + "    }\n"
                        + "    public static final class string {\n"
                        + "        public static final int app_name = 0x7f030000;\n"
                        + "        public static final int lib1 = 0x7f030001;\n"
                        + "    }\n"
                        + "    public static final class style {\n"
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
                        + "    public static final class drawable {\n"
                        + "        public static int foobar = 0x7f020000;\n"
                        + "        public static int ic_launcher = 0x7f020001;\n"
                        + "    }\n"
                        + "    public static final class string {\n"
                        + "        public static int app_name = 0x7f030000;\n"
                        + "        public static int lib1 = 0x7f030001;\n"
                        + "    }\n"
                        + "    public static final class style {\n"
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
                                                + "0x7f010003, 0x7f010004 }"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "TiledView_tileName", "int", "2"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "TiledView_tilingEnum", "int", "4"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "TiledView_tilingMode", "int", "3"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "TiledView_tilingProperty", "int", "0"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "TiledView_tilingResource", "int", "1"))
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
                        + "    public static final class styleable {\n"
                        + "        public static final int[] TiledView = "
                        + "{ 0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003, 0x7f010004 };\n"
                        + "        public static final int TiledView_tileName = 2;\n"
                        + "        public static final int TiledView_tilingEnum = 4;\n"
                        + "        public static final int TiledView_tilingMode = 3;\n"
                        + "        public static final int TiledView_tilingProperty = 0;\n"
                        + "        public static final int TiledView_tilingResource = 1;\n"
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
                                        "{ 0x7f010000, 0x7f010001 }"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout_max_height",
                                        "int",
                                        "1"))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "LimitedSizeLinearLayout_max_width",
                                        "int",
                                        "0"))
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
                        + "    public static final class styleable {\n"
                        + "        public static final int[] LimitedSizeLinearLayout = "
                        + "{ 0x7f010000, 0x7f010001 };\n"
                        + "        public static final int LimitedSizeLinearLayout_max_height = 1;\n"
                        + "        public static final int LimitedSizeLinearLayout_max_width = 0;\n"
                        + "    }\n"
                        + "    public static final class xml {\n"
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
                        + "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n"
                        + "int styleable LimitedSizeLinearLayout_max_width 0\n"
                        + "int styleable LimitedSizeLinearLayout_max_height 1\n"
                        + "int[] styleable TiledView { 0x7f010000, 0x7f010001, 0x7f010002, 0x7f010003, 0x7f010004 }\n"
                        + "int styleable TiledView_tilingProperty 0\n"
                        + "int styleable TiledView_tilingResource 1\n"
                        + "int styleable TiledView_tileName 2\n"
                        + "int styleable TiledView_tilingMode 3\n"
                        + "int styleable TiledView_tilingEnum 4\n"
                        + "int style AppBaseTheme 0x7f040000\n"
                        + "int string app_name 0x7f030000\n"
                        + "int drawable ic_launcher 0x7f020001\n"
                        + "int style AppTheme 0x7f040001\n"
                        + "int string lib1 0x7f030001";
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
                    SymbolIo.write(table, f);
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
        java.nio.file.Files.write(txt.toPath(), content.getBytes(StandardCharsets.UTF_8));
        SymbolTable table = SymbolIo.read(txt, "com.example.app");
        assertThat(table.allSymbols())
                .containsExactly(
                        Symbol.createSymbol(
                                ResourceType.DRAWABLE,
                                "foobar",
                                SymbolJavaType.INT,
                                "0x7f02000 ",
                                Symbol.NO_CHILDREN),
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "LimitedSizeLinearLayout",
                                SymbolJavaType.INT_LIST,
                                "{ 0x7f010000, 0x7f010001 } ",
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
                    SymbolIo.write(table, f);
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
        java.nio.file.Files.write(txt.toPath(), content.getBytes(StandardCharsets.UTF_8));
        SymbolTable table = SymbolIo.read(txt, "com.example.app");
        assertThat(table.allSymbols())
                .containsExactly(
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "LimitedSizeLinearLayout",
                                SymbolJavaType.INT_LIST,
                                "{ 0x7f010000, 0x7f010001 } ",
                                ImmutableList.of("android:max_width", "android:max_height")));
    }

    @Test
    public void testPackageNameRead() throws Exception {
        String content =
                "com.example.lib\n"
                        + "int drawable foobar 0x7f02000 \r\n"
                        + "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 } \r\n"
                        + "int styleable LimitedSizeLinearLayout_max_width 0 \r\n"
                        + "int styleable LimitedSizeLinearLayout_max_height 1 \r\n";
        File file = mTemporaryFolder.newFile();
        Files.write(content, file, Charsets.UTF_8);

        SymbolTable table = SymbolIo.readTableWithPackage(file);

        assertThat(table.getTablePackage()).isEqualTo("com.example.lib");
        assertThat(table.allSymbols())
                .containsExactly(
                        Symbol.createSymbol(
                                ResourceType.DRAWABLE,
                                "foobar",
                                SymbolJavaType.INT,
                                "0x7f02000 ",
                                Symbol.NO_CHILDREN),
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "LimitedSizeLinearLayout",
                                SymbolJavaType.INT_LIST,
                                "{ 0x7f010000, 0x7f010001 } ",
                                ImmutableList.of("max_width", "max_height")));
    }

    @Test
    public void testPackageNameWriteAndRead() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();

        SymbolTable table =
                SymbolTable.builder()
                        .tablePackage("com.example.lib")
                        .add(
                                Symbol.createSymbol(
                                        ResourceType.DRAWABLE,
                                        "foobar",
                                        SymbolJavaType.INT,
                                        "0x7f02000 ",
                                        Symbol.NO_CHILDREN))
                        .add(
                                Symbol.createSymbol(
                                        ResourceType.STYLEABLE,
                                        "LimitedSizeLinearLayout",
                                        SymbolJavaType.INT_LIST,
                                        "{ 0x7f010000, 0x7f010001 } ",
                                        ImmutableList.of("max_width", "max_height")))
                        .build();
        Path rTxt = fs.getPath("r.txt");
        SymbolIo.write(table, rTxt);

        Path manifest = fs.getPath("AndroidManifest.xml");
        java.nio.file.Files.write(
                manifest,
                ImmutableList.of("<manifest package=\"com.example.lib\"></manifest>"),
                StandardCharsets.UTF_8);

        Path output = fs.getPath("package-aware-r.txt");
        SymbolIo.writeSymbolTableWithPackage(rTxt, manifest, output);

        List<String> outputLines = java.nio.file.Files.readAllLines(output, StandardCharsets.UTF_8);
        assertThat(outputLines)
                .containsExactly(
                        "com.example.lib",
                        "int drawable foobar 0x7f02000 ",
                        "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 } ",
                        "int styleable LimitedSizeLinearLayout_max_width 0",
                        "int styleable LimitedSizeLinearLayout_max_height 1")
                .inOrder();

        assertThat(SymbolIo.readTableWithPackage(output)).isEqualTo(table);

        // Simulate what might happen with AAPT1 on windows, that the first line ending will be \n
        // but the rest will be \r\n
        Path mixedLineEndings = fs.getPath("withAAPT1onWindows.txt");
        try (BufferedWriter w =
                java.nio.file.Files.newBufferedWriter(mixedLineEndings, StandardCharsets.UTF_8)) {
            w.write(outputLines.get(0));
            w.write('\n');
            for (int i = 1; i < outputLines.size(); i++) {
                w.write(outputLines.get(i));
                w.write("\r\n");
            }
        }
        assertThat(SymbolIo.readTableWithPackage(mixedLineEndings)).isEqualTo(table);
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
        SymbolIo.writeSymbolTableWithPackage(doesNotExist, manifest, output);

        List<String> outputLines = java.nio.file.Files.readAllLines(output, StandardCharsets.UTF_8);
        assertThat(outputLines).containsExactly("com.example.lib");
    }
}
