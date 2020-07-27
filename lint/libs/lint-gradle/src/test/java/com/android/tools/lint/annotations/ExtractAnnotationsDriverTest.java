/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.lint.annotations;

import static com.android.testutils.AssumeUtil.assumeNotWindows;
import static com.android.testutils.TestUtils.deleteFile;
import static com.android.tools.lint.checks.infrastructure.LintDetectorTest.base64gzip;
import static com.android.utils.SdkUtils.fileToUrlString;
import static java.io.File.pathSeparator;
import static java.io.File.pathSeparatorChar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.infrastructure.KotlinClasspathKt;
import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.checks.infrastructure.TestFiles;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

// TODO: Test functions not in classes
// TODO: Test file-level annotations
// TODO: Test package-statements -- where are they in UAST?

@SuppressWarnings("ClassNameDiffersFromFileName")
public class ExtractAnnotationsDriverTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testProGuard() throws Exception {
        File androidJar = TestUtils.getPlatformFile("android.jar");

        File project = createProject(keepTest, SUPPORT_ANNOTATIONS_JAR);
        File supportLib = new File(project, SUPPORT_JAR_PATH);

        File output = temporaryFolder.newFile("proguard.cfg");

        List<String> list =
                java.util.Arrays.asList(
                        "--sources",
                        new File(project, "src").getPath(),
                        "--classpath",
                        androidJar.getPath() + pathSeparator + supportLib,
                        "--quiet",
                        "--proguard",
                        output.getPath());
        String[] args = list.toArray(new String[0]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);
        assertEquals(
                ""
                        + "-keep class test.pkg.KeepTest {\n"
                        + "    java.lang.Object myField\n"
                        + "}\n"
                        + "\n"
                        + "-keep class test.pkg.KeepTest {\n"
                        + "    void foo()\n"
                        + "}\n"
                        + "\n"
                        + "-keep class test.pkg.KeepTest.MyAnnotation\n"
                        + "\n"
                        + "-keep class test.pkg.KeepTest.MyClass\n"
                        + "\n"
                        + "-keep enum test.pkg.KeepTest.MyEnum\n"
                        + "\n"
                        + "-keep interface test.pkg.KeepTest.MyInterface\n"
                        + "\n"
                        + "-keep interface test.pkg.KeepTest.MyInterface2 {\n"
                        + "    void paint2()\n"
                        + "}\n"
                        + "\n",
                Files.toString(output, Charsets.UTF_8));
        deleteFile(project);
    }

    @Test
    public void testIncludeClassRetention() throws Exception {
        File androidJar = TestUtils.getPlatformFile("android.jar");

        File project =
                createProject(
                        packageTest,
                        genericTest,
                        intDefTest,
                        permissionsTest,
                        manifest,
                        SUPPORT_ANNOTATIONS_JAR);
        File supportLib = new File(project, SUPPORT_JAR_PATH);

        File output = temporaryFolder.newFile("annotations.zip");
        File proguard = temporaryFolder.newFile("proguard.cfg");

        List<String> list =
                java.util.Arrays.asList(
                        "--sources",
                        new File(project, "src").getPath(),
                        "--classpath",
                        androidJar.getPath() + pathSeparator + supportLib,
                        "--quiet",
                        "--output",
                        output.getPath(),
                        "--proguard",
                        proguard.getPath());
        String[] args = list.toArray(new String[0]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);

        // Check extracted annotations
        checkPackageXml(
                "test.pkg",
                output,
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<root>\n"
                        + "  <item name=\"test.pkg\">\n"
                        + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                        + "      <val name=\"from\" val=\"20\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.IntDefTest void setFlags(java.lang.Object, int) 1\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT, 3, 4}\" />\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.IntDefTest void setStyle(int, int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT}\" />\n"
                        + "    </annotation>\n"
                        + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                        + "      <val name=\"from\" val=\"20\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.IntDefTest.Inner void setInner(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT, 3, 4}\" />\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.MyEnhancedList\">\n"
                        + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                        + "      <val name=\"from\" val=\"0\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.MyEnhancedList E getReversed(java.util.List&lt;java.lang.String&gt;, java.util.Comparator&lt;? super E&gt;)\">\n"
                        + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                        + "      <val name=\"from\" val=\"10\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.MyEnhancedList java.lang.String getPrefix()\">\n"
                        + "    <annotation name=\"android.support.annotation.Nullable\" />\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.PermissionsTest CONTENT_URI\">\n"
                        + "    <annotation name=\"android.support.annotation.RequiresPermission.Read\">\n"
                        + "      <val name=\"value\" val=\"&quot;android.permission.MY_READ_PERMISSION_STRING&quot;\" />\n"
                        + "    </annotation>\n"
                        + "    <annotation name=\"android.support.annotation.RequiresPermission.Write\">\n"
                        + "      <val name=\"value\" val=\"&quot;android.permission.MY_WRITE_PERMISSION_STRING&quot;\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.PermissionsTest void myMethod()\">\n"
                        + "    <annotation name=\"android.support.annotation.RequiresPermission\">\n"
                        + "      <val name=\"value\" val=\"&quot;android.permission.MY_PERMISSION_STRING&quot;\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.PermissionsTest void myMethod2()\">\n"
                        + "    <annotation name=\"android.support.annotation.RequiresPermission\">\n"
                        + "      <val name=\"anyOf\" val=\"{&quot;android.permission.MY_PERMISSION_STRING&quot;, &quot;android.permission.MY_PERMISSION_STRING2&quot;}\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "</root>\n"
                        + "\n");

        // Check proguard rules
        assertEquals(
                ""
                        + "-keep class test.pkg.IntDefTest {\n"
                        + "    void testIntDef(int)\n"
                        + "}\n"
                        + "\n",
                Files.toString(proguard, Charsets.UTF_8));

        deleteFile(project);
    }

    @Test
    public void testSkipClassRetention() throws Exception {
        File androidJar = TestUtils.getPlatformFile("android.jar");

        File project =
                createProject(intDefTest, permissionsTest, manifest, SUPPORT_ANNOTATIONS_JAR);
        File supportLib = new File(project, SUPPORT_JAR_PATH);

        File output = temporaryFolder.newFile("annotations.zip");
        File proguard = temporaryFolder.newFile("proguard.cfg");

        List<String> list =
                java.util.Arrays.asList(
                        "--sources",
                        new File(project, "src").getPath(),
                        "--classpath",
                        androidJar.getPath() + pathSeparator + supportLib,
                        "--quiet",
                        "--skip-class-retention",
                        "--output",
                        output.getPath(),
                        "--proguard",
                        proguard.getPath());
        String[] args = list.toArray(new String[0]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);

        // Check external annotations
        checkPackageXml(
                "test.pkg",
                output,
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<root>\n"
                        + "  <item name=\"test.pkg.IntDefTest void setFlags(java.lang.Object, int) 1\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT, 3, 4}\" />\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.IntDefTest void setStyle(int, int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT}\" />\n"
                        + "    </annotation>\n"
                        + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                        + "      <val name=\"from\" val=\"20\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.IntDefTest.Inner void setInner(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT, 3, 4}\" />\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "</root>\n\n");

        deleteFile(project);
    }

    @Test
    public void testKotlin() throws Exception {
        assumeNotWindows();
        File androidJar = TestUtils.getPlatformFile("android.jar");

        File project =
                createProject(
                        longDefTest,
                        SUPPORT_ANNOTATIONS_JAR,
                        longDefAnnotation); // LongDef not yet in support lib binary
        File supportLib = new File(project, SUPPORT_JAR_PATH);

        File output = temporaryFolder.newFile("annotations.zip");
        File proguard = temporaryFolder.newFile("proguard.cfg");
        Joiner pathJoiner = Joiner.on(pathSeparatorChar);
        String kotlinLibraries = pathJoiner.join(KotlinClasspathKt.findKotlinStdlibPath());

        List<String> list =
                java.util.Arrays.asList(
                        "--sources",
                        new File(project, "src").getPath(),
                        "--classpath",
                        androidJar.getPath()
                                + pathSeparator
                                + supportLib
                                + pathSeparator
                                + kotlinLibraries,
                        "--quiet",
                        // "--skip-class-retention",
                        "--output",
                        output.getPath(),
                        "--proguard",
                        proguard.getPath());
        String[] args = list.toArray(new String[0]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);

        // Check external annotations
        checkPackageXml(
                "test.pkg",
                output,
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<root>\n"
                        + "  <item name=\"test.pkg.LongDefTest void setFlags(java.lang.Object, int) 1\">\n"
                        + "    <annotation name=\"android.support.annotation.LongDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.LongDefTestKt.STYLE_NORMAL, test.pkg.LongDefTestKt.STYLE_NO_TITLE, test.pkg.LongDefTestKt.STYLE_NO_FRAME, test.pkg.LongDefTestKt.STYLE_NO_INPUT, 3, 4}\" />\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.LongDefTest void setStyle(int, int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.LongDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.LongDefTestKt.STYLE_NORMAL, test.pkg.LongDefTestKt.STYLE_NO_TITLE, test.pkg.LongDefTestKt.STYLE_NO_FRAME, test.pkg.LongDefTestKt.STYLE_NO_INPUT}\" />\n"
                        + "    </annotation>\n"
                        + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                        + "      <val name=\"from\" val=\"20\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "  <item name=\"test.pkg.LongDefTest.Inner void setInner(int) 0\">\n"
                        + "    <annotation name=\"android.support.annotation.LongDef\">\n"
                        + "      <val name=\"value\" val=\"{test.pkg.LongDefTestKt.STYLE_NORMAL, test.pkg.LongDefTestKt.STYLE_NO_TITLE, test.pkg.LongDefTestKt.STYLE_NO_FRAME, test.pkg.LongDefTestKt.STYLE_NO_INPUT, 3, 4}\" />\n"
                        + "      <val name=\"flag\" val=\"true\" />\n"
                        + "    </annotation>\n"
                        + "  </item>\n"
                        + "</root>\n\n");

        deleteFile(project);
    }

    @Test
    public void testWriteJarRecipeFile() throws Exception {
        File androidJar = TestUtils.getPlatformFile("android.jar");

        File project =
                createProject(intDefTest, permissionsTest, manifest, SUPPORT_ANNOTATIONS_JAR);
        File supportLib = new File(project, SUPPORT_JAR_PATH);

        File output = temporaryFolder.newFile("annotations.zip");
        File proguard = temporaryFolder.newFile("proguard.cfg");
        File typedefFile = temporaryFolder.newFile("typedefs.txt");

        List<String> list =
                java.util.Arrays.asList(
                        "--sources",
                        new File(project, "src").getPath(),
                        "--classpath",
                        androidJar.getPath() + pathSeparator + supportLib,
                        "--quiet",
                        "--output",
                        output.getPath(),
                        "--proguard",
                        proguard.getPath(),
                        "--typedef-file",
                        typedefFile.getPath());
        String[] args = list.toArray(new String[0]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);

        // Check external annotations
        assertEquals(
                "D test/pkg/IntDefTest$DialogFlags\nD test/pkg/IntDefTest$DialogStyle\n",
                Files.toString(typedefFile, Charsets.UTF_8));

        deleteFile(project);
    }

    private File createProject(@NonNull TestFile... files) throws IOException {
        File dir = temporaryFolder.newFolder();

        for (TestFile fp : files) {
            File file = fp.createFile(dir);
            assertNotNull(file);
        }

        return dir;
    }

    private final TestFile longDefAnnotation =
            TestFiles.java(
                    ""
                            + "package android.support.annotation;\n"
                            + "import java.lang.annotation.Retention;\n"
                            + "import java.lang.annotation.Target;\n"
                            + "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n"
                            + "import static java.lang.annotation.RetentionPolicy.SOURCE;\n"
                            + "/** @noinspection ClassNameDiffersFromFileName*/ @Retention(SOURCE)\n"
                            + "@Target({ANNOTATION_TYPE})\n"
                            + "public @interface LongDef {\n"
                            + "    long[] value() default {};\n"
                            + "    boolean flag() default false;\n"
                            + "}");

    private final TestFile packageTest =
            TestFiles.java(
                    ""
                            + "@IntRange(from = 20)\n"
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.support.annotation.IntRange;");

    private final TestFile genericTest =
            TestFiles.java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.support.annotation.IntRange;\n"
                            + "import android.support.annotation.Nullable;\n"
                            + "\n"
                            + "import java.util.Comparator;\n"
                            + "import java.util.List;\n"
                            + "\n"
                            + "@IntRange(from = 0)\n"
                            + "public interface MyEnhancedList<E> extends List<E> {\n"
                            + "    @IntRange(from = 10)\n"
                            + "    E getReversed(List<String> filter, Comparator<? super E> comparator);\n"
                            + "    @Nullable String getPrefix();\n"
                            + "}\n");

    private final TestFile intDefTest =
            TestFiles.java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.content.Context;\n"
                            + "import android.support.annotation.IntDef;\n"
                            + "import android.support.annotation.IntRange;\n"
                            + "import android.support.annotation.Keep;\n"
                            + "import android.view.View;\n"
                            + "\n"
                            + "import java.lang.annotation.Retention;\n"
                            + "import java.lang.annotation.RetentionPolicy;\n"
                            + "\n"
                            + "/** @noinspection ClassNameDiffersFromFileName*/ @SuppressWarnings(\"UnusedDeclaration\")\n"
                            + "public class IntDefTest {\n"
                            + "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n"
                            + "    @IntRange(from = 20)\n"
                            + "    @Retention(RetentionPolicy.SOURCE)\n"
                            + "    private @interface DialogStyle {}\n"
                            + "\n"
                            + "    public static final int STYLE_NORMAL = 0;\n"
                            + "    public static final int STYLE_NO_TITLE = 1;\n"
                            + "    public static final int STYLE_NO_FRAME = 2;\n"
                            + "    public static final int STYLE_NO_INPUT = 3;\n"
                            + "    public static final int UNRELATED = 3;\n"
                            + "\n"
                            + "    public void setStyle(@DialogStyle int style, int theme) {\n"
                            + "    }\n"
                            + "\n"
                            + "    @Keep"
                            + "    public void testIntDef(int arg) {\n"
                            + "    }\n"
                            + "    @IntDef(value = {(int)STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, (int)STYLE_NO_INPUT, 3, 3 + 1}, flag=true)\n"
                            + "    @Retention(RetentionPolicy.SOURCE)\n"
                            + "    private @interface DialogFlags {}\n"
                            + "\n"
                            + "    public void setFlags(Object first, @DialogFlags int flags) {\n"
                            + "    }\n"
                            + "\n"
                            + "    public static final String TYPE_1 = \"type1\";\n"
                            + "    public static final String TYPE_2 = \"type2\";\n"
                            + "    public static final String UNRELATED_TYPE = \"other\";\n"
                            + "\n"
                            + "    public static class Inner {\n"
                            + "        public void setInner(@DialogFlags int flags) {\n"
                            + "        }\n"
                            + "    }\n"
                            + "}");

    private final TestFile longDefTest =
            TestFiles.kotlin(
                    ""
                            + "@file:Suppress(\"unused\", \"UseExpressionBody\")\n"
                            +
                            // WARNING: This makes resolve fail!
                            // "@file:JvmName(\"LongDefTest\")" +
                            "\n"
                            + "package test.pkg\n"
                            + "\n"
                            + "import android.support.annotation.IntRange\n"
                            + "import android.support.annotation.Keep\n"
                            + "import android.support.annotation.LongDef\n"
                            + "import android.support.annotation.Nullable\n"
                            + "\n"
                            + "const val STYLE_NORMAL = 0L\n"
                            + "const val STYLE_NO_TITLE = 1L\n"
                            + "const val STYLE_NO_FRAME = 2L\n"
                            + "const val STYLE_NO_INPUT = 3L\n"
                            + "const val UNRELATED = 3L\n"
                            + "\n"
                            + "const val TYPE_1 = \"type1\"\n"
                            + "const val TYPE_2 = \"type2\"\n"
                            + "const val UNRELATED_TYPE = \"other\"\n"
                            + "\n"
                            + "class LongDefTest {\n"
                            + "\n"
                            + "    /** @hide */\n"
                            + "    @LongDef(STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT)\n"
                            +
                            // Why oh why does this get matched as kotlin.IntRange?
                            "    @IntRange(from = 20)\n"
                            +
                            // "    @android.support.annotation.IntRange(from = 20)\n" +
                            "    @Retention(AnnotationRetention.SOURCE)\n"
                            + "    private annotation class DialogStyle\n"
                            + "\n"
                            + "    fun setStyle(@DialogStyle style: Int, theme: Int) {}\n"
                            + "\n"
                            + "    @Keep\n"
                            + "    fun testLongDef(arg: Int) {\n"
                            + "    }\n"
                            + "\n"
                            + "    @LongDef(STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT, 3L, 3L + 1L, flag = true)\n"
                            + "    @Retention(AnnotationRetention.SOURCE)\n"
                            + "    private annotation class DialogFlags\n"
                            + "\n"
                            + "    fun setFlags(first: Any, @DialogFlags flags: Int) {}\n"
                            + "\n"
                            + "    class Inner {\n"
                            + "        fun setInner(@DialogFlags flags: Int) {}\n"
                            + "        fun isNull(value: String?): Boolean\n"
                            + "    }\n"
                            + "}");

    private final TestFile permissionsTest =
            TestFiles.java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.support.annotation.RequiresPermission;\n"
                            + "\n"
                            + "public class PermissionsTest {\n"
                            + "    @RequiresPermission(Manifest.permission.MY_PERMISSION)\n"
                            + "    public void myMethod() {\n"
                            + "    }\n"
                            + "    @RequiresPermission(anyOf={Manifest.permission.MY_PERMISSION,Manifest.permission.MY_PERMISSION2})\n"
                            + "    public void myMethod2() {\n"
                            + "    }\n"
                            + "\n"
                            + "\n"
                            + "    @RequiresPermission.Read(@RequiresPermission(Manifest.permission.MY_READ_PERMISSION))\n"
                            + "    @RequiresPermission.Write(@RequiresPermission(Manifest.permission.MY_WRITE_PERMISSION))\n"
                            + "    public static final String CONTENT_URI = \"\";\n"
                            + "}\n");

    private final TestFile manifest =
            TestFiles.java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "public class Manifest {\n"
                            + "    public static final class permission {\n"
                            + "        public static final String MY_PERMISSION = \"android.permission.MY_PERMISSION_STRING\";\n"
                            + "        public static final String MY_PERMISSION2 = \"android.permission.MY_PERMISSION_STRING2\";\n"
                            + "        public static final String MY_READ_PERMISSION = \"android.permission.MY_READ_PERMISSION_STRING\";\n"
                            + "        public static final String MY_WRITE_PERMISSION = \"android.permission.MY_WRITE_PERMISSION_STRING\";\n"
                            + "    }\n"
                            + "}\n");

    private final TestFile keepTest =
            TestFiles.java(
                    ""
                            + "package test.pkg;\n"
                            + "import android.support.annotation.Keep;\n"
                            + "/** @noinspection UnnecessaryInterfaceModifier,UnnecessaryEnumModifier*/\n"
                            + "public class KeepTest {\n"
                            + "    @Keep\n"
                            + "    public void foo() {\n"
                            + "    }\n"
                            + "\n"
                            + "    @Keep\n"
                            + "    public static class MyClass {\n"
                            + "        public void paint() {\n"
                            + "        }\n"
                            + "    }\n"
                            + "\n"
                            + "    @Keep\n"
                            + "    public static interface MyInterface {\n"
                            + "        public void paint();\n"
                            + "    }\n"
                            + "\n"
                            + "    public static interface MyInterface2 {\n"
                            + "        @Keep\n"
                            + "        public void paint2();\n"
                            + "    }\n"
                            + "\n"
                            + "    @Keep\n"
                            + "    public static enum MyEnum {\n"
                            + "        TYPE1, TYPE2\n"
                            + "    }\n"
                            + "\n"
                            + "    @Keep\n"
                            + "    public static @interface MyAnnotation {\n"
                            + "    }\n"
                            + "\n"
                            + "    @Keep\n"
                            + "    public Object myField = null;"
                            + "}\n");

    private static void checkPackageXml(
            @SuppressWarnings("SameParameterValue") String pkg, File output, String expected)
            throws IOException {
        assertNotNull(output);
        assertTrue(output.exists());
        URL url =
                new URL(
                        "jar:"
                                + fileToUrlString(output)
                                + "!/"
                                + pkg.replace('.', '/')
                                + "/annotations.xml");
        InputStream stream = url.openStream();
        try {
            byte[] bytes = ByteStreams.toByteArray(stream);
            assertNotNull(bytes);
            String xml = new String(bytes, Charsets.UTF_8).replace("\r\n", "\n");
            assertEquals(expected, xml);
        } finally {
            Closeables.closeQuietly(stream);
        }
    }

    @Test
    public void testGetRaw() {
        assertEquals("", ApiDatabase.getRawClass(""));
        assertEquals("Foo", ApiDatabase.getRawClass("Foo"));
        assertEquals("Foo", ApiDatabase.getRawClass("Foo<T>"));
        assertEquals("Foo", ApiDatabase.getRawMethod("Foo<T>"));
        assertEquals("Foo", ApiDatabase.getRawClass("Foo<A,B>"));
        assertEquals("Foo", ApiDatabase.getRawParameterList("Foo<? extends java.util.List>"));
        assertEquals(
                "Object,java.util.List,List,int[],Object[]",
                ApiDatabase.getRawParameterList(
                        "Object<? extends java.util.List>,java.util.List<String>,"
                                + "List<? super Number>,int[],Object..."));
    }

    // Unfortunate copy from AnnotationDetectorTest; it seems bazel doesn't want to let
    // me have one test macro depend on another.
    // Snapshot of support library: support-annotations-26.0.0-SNAPSHOT.jar
    public static final String SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP =
            ""
                    + "H4sIAAAAAAAAAN18dVTVW9c10iVIh3R3d3d3iyDdBzh0SEi30tKd0p3SDSIgIt0pCIKEiOB34AZ6nvtc"
                    + "9f3G+8f34fiN4XWcM3/jzr1Ze+255loq8lDQiBAQ8PAQGtEJchCgH0iIP34QQI+ipIYoo6ySFDMUhMoP"
                    + "H6z2hdy/D/oAHujB/P6DiqJKslKS6hpMilKfFEeGFeQZmcbvyjPSjY68rlFjmWRf3ljdgAQD+/6t8KDH"
                    + "yM7U0d7K9D9e+v3n7n33OScXINDe0flfP3//Hz5vZGdn72zkbGVv9x9fLVxbRyK7AwGxDnpo/v2rYvb2"
                    + "ADUzJyYTgJGTU1C0vDIk673+QvhSErI2FKLIZ1DSp8vj6n6UsaV+e3aN7ixmydGxE7ELTAgGrwKIO1DN"
                    + "0vDJibiqZL+xp5+/Ir/87PUY7glc4/HySIATKhMuh4m6Ke3TPDHYsjZWuq7WBvSHnct0dLUJNmmDeqNY"
                    + "jmVRWFr46eyNSIRr9NnP2z4rfhaPw7aZxMZ5hmw3nAzPiLaOpjaHqDm2vi+8PUmTvK/XSbLuMLLc3EqC"
                    + "NoPO6HC2cS6S/ZB5xjY664tTtIBiMDK+OpGDJzSaIm5UQLTxByDb8spupLTJIiqUNe0xGbvjylavuQnb"
                    + "RcPB637W7b1+pU16BcaA1AnF0/YZs44ssTOI+WKRk4c2KtRzuUXSp9FHdwOpCESHm5hVJHmC6+MwqAmp"
                    + "Cw1+5PmVG3MgJYjjPdDD8O88SzgauRkZA8xuuVZRvOY6nazSP/yzy0fDrzxAU1QiQU0dMtm7pqR6AknV"
                    + "/KSU8i0zH4w/2xB9vXNFszYZcD9OETDB/cbDnq3i0/nJ1R2IM+BKZzGcBVEau+VrhRhJHjUEtm0U9nOq"
                    + "5wjBSWfvHOnx9IKD6q0DkBLoyRqn+uRKIkfx5hELDzGKtdu+Un7NDgu/ax1xCBWQaq0YmogZH5NXlSAr"
                    + "+ubxN5lCKtXxUU5k6MxXuHqa3CNm9WLyzS3WQjJjM6kHahSqn3iThEF866oTOXpCk4L4hhKte5qE3Oiu"
                    + "/xE4QiwpQpT/lD3XWJh5YtL09HX78MY43fB43AY++fBy84Ri0zgRFCxn8+NlpbvvhaefUPUKaQqGX/Q0"
                    + "3JsFQoQwefvxdaE5ExDwb/PxnMP+SDd1fQm7LohqZtCvBe2/0y1r56xmZGdh9ifXqvLKXSL3MmDkoGKe"
                    + "22vkjAdu9xIJeOpm98TKyVrJRMJVd4jJyT2PZ0SgvtS/IduFS2JFZJWCn//TK+5nI14+L30hOgg1nHnG"
                    + "VzRC2CGLVSkKoGPfcStCUuIHi7HtRFSOSjOg5J3C0pNGR4s9vnyE4AeQ7hl/wv31CcbAg2znBzybDRj1"
                    + "htBDdgRyvkMtV/MHcF76LkrLXVoE5CLNnVeQwcOUqAkuyiIPK0n6qt/6FtmifQ7IlyuaqX1DqjKTsVxl"
                    + "OUJ5xNTQ/+qJclVOQ5oDRX6mwsF4McZAbbH8OKv+3lilwv2q1g+TRQu7au9yUOtls0QeMvbnZmzUei46"
                    + "3Y+hx0YhX0GN8hBIDZOxc/2ygOG36oleKLwCJbvstQvc3dAMa4Zso1gkj+ma8wt+hP/SFMMZmi1VBLuJ"
                    + "PxyBd2DNPkuzAxgjjuBqpJzMnOUwHQKVj5LAp/Zep/gI9ccFKmmp/SQOWiAh0AKR//sCqbj/uTQxw0pd"
                    + "IpjB31SzIMPdn+OumXXGMuk1v1AxjsNPEZtuZBFPjiY1kxuK7yhp+njHi2Yt2ZKCKoppM9Np04t6i+2A"
                    + "uMIeYn+sEmi9MtLFK56faWpF0cyGjaxVaa14HsvO0aVgxYsh3hSdq8MjR5lik1wE0x1lj8u/aW38KfPJ"
                    + "gmwOgCoee+1x8K4XFWYKfDU8LZNHmur4prBldA+bbmpg/cHxBfLzcwSoDKVAqn0u4f2+rlpundcwovnU"
                    + "bh+nd1nioaidBxzEX4yd6GUuWvtUfPjMLE59nAa3ZNb4+VFIYSIvktUXvag3Ek8/SHJlUhdf1WHLqUdU"
                    + "xCAlx3qmXj0Zx/Rdh6YP/mIeumhJR/YKmMD/riCBCqobm1AdMg9mghFTNadFsk3yDKV3GBJLo8pmxAIT"
                    + "+lSFvdo+z24dIqRHC4Enx2lvZR3jxzWgTMcr+MXYL2pnZftd7B9WgmQFLQQZi3+o+8NGlkfYDMkw8tpY"
                    + "wTJssGz3uJgU8PDV2GpNC8S7/lyJYtZVY/TUY+HCLa+tWB+vtpdQ26sT3ckhTqg4yCdhg290UDTI8PD1"
                    + "SlwoakblADDdmvE2JVOOBWycyhTvZnWMEsbxuvW3cYXRPHcilgKXuB3aEmeHeqG6bVkxZOWhPQI16skG"
                    + "rRwviJ0B6FgHeqLM63EI3S4OnRp58lZhLcyjcGHOu4pu92VVTniThKK+INexPuMR9A+lQg4Wlaky53l/"
                    + "+NGlL5fUF/km+HNar1icm5uo/RH8a09NpD8lRDJ2K65QXSwB2Fnit4k60tGHlnRFEwAhOUQz4kPvKN7z"
                    + "iHU3MdPIE+TurPfIDg/u4PzIc/G93d5f5dnZ2fF/nef5WY7YBxaU9f4rdEN1mja86skp1LE2tqWVQ9uR"
                    + "K0zOkZnwL4977bsOUnkM5G03Vv1W6pPC8ij9BcToHsasczRdovJxhYW7lWfhHA0GrjQ3GtJN5Nf2LOBs"
                    + "BUg0nxS0RFEUfSFQyJTyDp5Nkk4jEpXAgO0mydNhJzhx/8y3Nh4tHHynDtMzBr/uY41XDWvx5dzZ1hqu"
                    + "7kVV7rlcX+JKATDu0r4eD1fOGXVZCWHDvixLrl5c9dke6YYNugtB9so8Tjzl03Fnx9W8jfVjpB95DrLL"
                    + "COYEcSwHiim8/86zmpmDi5WjmZOKmaOtlZMT6J8otB2tnP88BBJU9eUhRTEFc8tTzfO9ZUWh0dRQ6wKf"
                    + "vaRoijWkSZ0UV85dNDVNr0vWz26fEhfOIsZaCBogMfBq3BpNf9x4lpb8uuIMFcJFQ19Db695GgO4yaYg"
                    + "s/x+r5X8ZEDCen3GlCtCYU+2LFenj27ikUhFGJvpY9OCId22ZuwiDh8ERc8YWVwkd3plWiGJMDw1+SCG"
                    + "3C+ZjrwxTrTAexR8WuqeGlp3xXRL6yGl0+PXgtSefSvflWcvSNJOYzi4IFWU7b9swGMdtKq7VFIn8T04"
                    + "O2j2ojnGFw1AEgZWMasYDE7vxKmy3nf0cSwu40yeU9GVL21svTdJqGA5wOzWqrHWVdjRK4doDQmJ95TB"
                    + "t3kAYX0ZmLA11OQ3TBvB2YeMSOMotL7JJ72P90AM4T4toKAyiHAfJwaFwaRF6zPYAWyQ5AC4Aq1BLWg9"
                    + "6P59LcSNAAB1F6CZ41+7vhO06+8FfxM1wsIaT4ULwO8eF3Q/loBYuxNgxP1IIGEBgZb2Q8Fc7EdrIncI"
                    + "4qHpE4jrvJI/7ry9nT254uA14xNpPSmeZ2NRQFFL6r65yMx8YzELmng5r85TI9opfyDnp9lBrPZhtVEs"
                    + "1FouJR4rZ9nGdA2F0sBHPcbUPeRx51g2/ecYKwUZyVVIEgOQE3pIG+IHV8LbqapscxrBK6bRFIG7nuJ0"
                    + "e/76y/vvT6pWrVud1YFPdCvWjs34yOaR0h+Sf2o8ljQqvbCZsQ0aW/jGm87M6xT4yDb18sUXaDHyeSiN"
                    + "Qr/upYYwrdCXKlFiAohfWHOjMNK0duBCHYJHwU7JgKXDcHYQgzKgHc3z2ztazczI9L9taGPUOo2/NvTx"
                    + "xJsibrwHFrYWWtsVJ2Oo29KSbghsAzjE+01OW0T7vG6bn3YvoTo4XzDRMbnq7yHwOOFRUQAfCO/KCIaT"
                    + "cx4psKVKVp3kTcUkaEnOIm1zqrImE/WqmyEbLMBr4LcHUr0coKiFXBxSD3+mio5anC9Wr3qp3JQ00ITJ"
                    + "A49u+zY+I141IBp7Qmq5KM6xVeX9oUFTW2S/6YNVU48lVCu52dMl8x6Kwren/Ip+XwOW2g+OssdsoO8y"
                    + "fe3obet4KxeRDkshDdmUqWFto3ioTxNFevrw0fQOChXHuhv/o/fAqvyOc7kvnHf8kKUT2/XWpxEIyo/0"
                    + "eBuAvGZVfUmidyRHQ+b1tgYEyeVpjMiDXmHWV3Zfkk34mQHeIS79uBAdexe5+KCFePPz7azu7GhlZyFh"
                    + "Zv5XQjksByV6r3TUhtOaKBwOTVXC/9F8gLSyKDl87N2WEhLeAQqr7sBBwPQI7BEoe4cQltujoeCFFlBQ"
                    + "5tR823vs5e3rBdnY6ReSEtIc4i3mUKDQF9g9XLL/RuZVvH084ZcY+TrCRfT3ijYYhWlKe4LtJSM8Yrxy"
                    + "1bWvUWdDq46osY48jagJgpJdPEeYPV88Dta+ol0BMIoxIgib9rv6UVQePsuLw13IjKjEhHm9oh8BrJ5B"
                    + "K3EeUArcSj1PXKmKjNzCwFatm7GDc2D3QZ61P7SR7ntEwy+DIfixcMY0Ns9uocWLPbsen+hYav75ZZrd"
                    + "yEPGHFGS9fX1Df4iEZosGn3ZZi9tOZRF+4Wu+vl+/yExHFz+6d5FSFfRSMQXwj8yPLiDTCMEYhcR8qeH"
                    + "pJK9nZILAPBXuHgFygrv9VuoFpFA+vQHqPICa+GYqTK0aORKbdXx0df8GMzE5RIXCgCy7pyoQk9AF1FO"
                    + "KQEOh7jz13sj9mxx5zc30fs8dcsD/iZISvCTwXSvjReVoalVzuKPHUnLtwC98GlBTZOD/s9izdjQ0uSK"
                    + "Od6ExcpDapcbATrXR8zbYVoKV7068zff8EM+71UY4ETHJVV7xBqztj4qbJklgV6hKP4uv5vU79NqcKgZ"
                    + "e3mMDc47ws5XnuN8G/CaGenb4wdoRbAceZ8dxNLGiHmYNvHLl/L33SW677x0I4RUfO/hI5HrdxCV+xyC"
                    + "XUukRfn4Ti5X3BsFSvXHyYXFQ8dLycLSlGOzpevKmwJT0EyIDfN6zeJPRBBskUboO08bErP9r7BrsL8Q"
                    + "lmrzThrivKfyCkIMEbTEtcK3wpvhdjRc/wh2gA4AxMxwQGswfuentyZNKw1Lx7/jS1C0ojwUKGYvHdvU"
                    + "1ekKMxH1MNO4O4DuqAn1q9nNZtxYPerN/JPxttWAmqArTtQrCN/4JHhdVCTYo9FoD/NFFuJM+wMIO+C6"
                    + "0VK3X4h4sHlehEABfzK6+GOycxslsqaMiARDHhQks3X/jHU1JfX3tPlRLGollN3nA6w+dJMjOO0wvllr"
                    + "xqJDKo7DQK3RQFMGI/aISW0yxlKXM2LnUnW8Cn1x38kIsuC4I1imIzy8Yf34dyG0TQeHLlLY1gLI594s"
                    + "hpYnAf2hIXFCqGLB+L5EqHhMQXKOjBi+D0yTR18t8QqnGm8DvPx30oy7gbrWLx8YPPfLNrbH2tdQuZji"
                    + "w+d6NKP9uo/x4RIVx3jXU3kDFcraql3T17JbYOwmyJYIUYCYXQU91P/Oro7t90rLK6U/lZYnZD6EArjC"
                    + "KAQzoACClShTDVvdQkpFpPCAKi+v1rRBvqvE4yYN1MA4rAxT/JK5M2LP9+YmDewgTHsMXL0WAJBTqmir"
                    + "MUPUSAOmPslJJp0lGpPj6kkE1TsGqScPJ4uV11V1yeU6wXJnm3hRHnfhtCP4Gk4YB82ufs3odOHFyL5O"
                    + "A63vZyNaz1/CpeNSvimj3w4ReIqnl4hDQfYwi3K/XoCYxnmNco4TXcMXMbb/8ImoSRe5UghJDkq36j21"
                    + "UsKnFwJe6UeWPcRdTzIYjgXYHfe3ej1t2S7OFjN9TcZmXAhfJybpwmrnB+wJ1Qw/EQ06JWrU9HvfMJWl"
                    + "VC8NSgOFNu7DK1p2+jkZZIUD4W2ppZX4lJ62g2la9Rtfl1BAJDf+PFhLuxg5mpqZinn8xbWwMqTovd6l"
                    + "NLjColBffCRy1mDLNaxEraIsKlxkt6oq3UaEB9V7MSbPAivMV+A+iuNtMTxZRY9I9545f8k9c351/hUS"
                    + "GLEGEQURcucpfEIQGy+GUnn5Y4w6lWxVVwIppjeuXtJFklYZUY0lUSwnhXpmCNRnw6mQlGkojrvH8Usn"
                    + "eqh3G8oU0x356py6ZqTSdDm7dMxMXGO9VBcPcDOqXQ4d+yA1m5nTF8mzv8yGZV6QpWyU3+FnDUNX3iVe"
                    + "Z+T0rGXoIvb8QEOmR5dqWqzU5SGUmp8851zmo2BZanLRjfCMYKXVecLzLiNkZ1wN5WPqzOVcsiHxkwrY"
                    + "6cQWWmtp3G9QRLnBCzl7Ru3uxpMO4MGBofrZ4HVwmAM9TP/OqoajkZ2T1fVfv9vFite6SvASTLa/Qeij"
                    + "610swXy6PI4Rg67IQpnveVhbi1GdtxfrKt9l8GkZ9aM0noVgKJXivKmFuUXGjBvbHxHCYW9lTPxdd4zI"
                    + "Y+MhgezsIZms+1tqOEN0ueR0dpuOlDBKOWoP67JwNIuspLzLohJQaKGZpJ4eshdbtH2l/QwVF480kTjB"
                    + "7/fJmopsGFo6kkOLjf5B+xnoOpMsNzU/lhb4etybBKi7gDbsJZXYOfzeMzomlfkR8+DlKWYP6opIFue+"
                    + "tLcPNNprDi9kqbSWWL5XUXKXOXpMinbfBr4+ei5ikYultBWv41cjd/4K5TEkJGebS6N691SDbdbgVUoN"
                    + "0YVteQO87Z7hu6CKIozTyoMRP/Z05h8p/hge++A6QrwHPfQ/Va3MLMwc/5NfySx/xBuRUGZdBpVAUJMm"
                    + "Jg9WDR4H1ZwWxK+zbAvlSoXHR7jPuZE1RD3iGNwzbiB+zwd8rmUr0F2xMznACQ4H8iRs9E0oLB2V3yYL"
                    + "a20eR15pTexDWiDWZpiqTUqxwiuTo1iJ+1bxSmx+LR1sdgxx+fS+GHYG0ucpjeOrqsv1SQiDLJ1E5All"
                    + "kU5Ei1dwhLs6kvwPx5BaByWWr2+LbyjxNvcjPvkouL7csRNH1jT4uvOpiFnl/uPsflThdYSAbGJgKVqk"
                    + "56OPXJ+6ss8wP0Oisj3I02WcZ1gCOFtfnS58SxexEfbgMFA1hbZDfD7ja/lQJKvnDOI0XuVqHPEFgJ/A"
                    + "ZZrIsU2qaA2+f8pbMhnoH5xQkJCY8Mb+R7KZ2aVm6EBEH4Melp+T7Qi0Bxg52zv+U1zOIqMjuonLApVw"
                    + "zzLoGGJMX8UGAQ8Nh9hIKfMX9lz/zjw875n8JYFLfzk8P0WBOAPu38iyzrgc1AUD6ARyQch85Las8lHo"
                    + "U6asmy8TpuFhcRyyy/TWFDYdy6OvL+kT22mditu4BgigS7pyF3FSng65y0MBlkYmC+NaiWhpGVtyDdH7"
                    + "zt9E+6UGTXcRv5BmKuCCorOCZL2E/Lyth5DK9lSqnTpF0RXojp5zJ6AvqR90R1fACM4F3dHpI7+AovOh"
                    + "ew9x95PO9LGIkYGgTBwra7aLloNMVNOxxahnm/QKDAElE1Gu7bPXQrgb6vdCuGvmjRDekX0TnaEZqfuV"
                    + "ZpWU2sCic2D687ckIM6XQQ/VT3g3/U4D51fuZrkn9Lw+G0bO4y5yaP9YFR5uskJquJxocWwREpRjnPoG"
                    + "XV1uT/Fke18H6rI06SghjTgFw3lL/1n6gZfBEnEmxEd4bZQ0fuBKB2WAXV4JWWfYSLS/Fb4xr15YlCGQ"
                    + "UGtogHPUjBYHzzG/Fk3KWpOt8ays71WgbvPqc2A/X/FUEXKBMvL+e05SfLVo6dgpKVOsE8Kv5Ok7ClQP"
                    + "H7IceWCyNhYzh0pWSYg3h3guDo89xbF3olC9eBth4M8aMBoZkgbJnIcSV7ylXr4FR77f/tqDZ+N1m/QS"
                    + "Gh1jrcJC9ZCTflL7wf5UX1a7PT6DVhIubEOd054br2FHlugZxJ6ViFMIo2UcodoU6WMZL7hsJGkgYIHb"
                    + "kixN5HVXcVBbUA3xjxxzWq11XSfTyD+Xv68z6etqw20i1w0KIxYltoA6Yp810a/+RK86I5atbNUZ+yjy"
                    + "skmIhrj0hxI/FFCkin1LgfN9IiyXhlVLBMm0x19zINCNfHGzqbmBgE4/mBBsArSiu7ECUR7KMkpZ+xtq"
                    + "1VXr79jrTaGVuvSsWLfEYxCci9XCzHUp6MiBK/h9iQqKtqcPK1tk0143TXg5Si+3JiE0SUGySwzJUzkR"
                    + "LFzB3UWnXU2w2m50ZgEF6fl7Vc5DnI7Pe12kHWse1eoCA+leKx1vuyFoQOGrefGQKm2fEOK8DN51K+LH"
                    + "n6hUPtAPEYnzzCAeLzp4nLtNKdKPaLkVJmTo2jtJodjTM3rUSyZ7Asqm2+Wy6hYHiV+7lyKkYZU3Xb3n"
                    + "ugNJ2XrfUAmBq70OGSuyYVsXt6Hy0YZKgO006w69Bs9ktlGeVZ6Z6buBjJhLsGj+PETc8BdlPyl7O+f/"
                    + "ddkPKtf2/0t59SHtUSU5iOPNn99aJKxszX7ISWyulaYlHZeSsikfQk+aS8hn+50mysXlGtM5pav5Iejv"
                    + "SGJsyVoedLZPifuK+Grs5MXLGWX7tbIne1CxD/ZvzfE/YRoVTHPnOQH2qWQmCURtiFDGZo9rV6hJs+2W"
                    + "9kix6Ek01WNeH5kYg3VcWjpmzn1ADL4+5cBHX1a5V84U094QajHt45VHJMvokg2Q51WjySqVzX3LMokr"
                    + "ct9F9LyO3UwRSAVk+nKg2O0gJJN3375ZX97/hXLnscOkRVGUR8/OPYlJIxZksXDObtVnIczsRHfPxXFZ"
                    + "5hFeykTpfn7Q9ih18srj7PVr1lpX7z+Y1lyhaTm4ZtryoLPsO6a9b5gGxe5M1UTLrJ31ARDTh2BMU5GM"
                    + "XFH8GtOijo5GHj9kJzdMk1X6+99kJ3I81X8elX+WMBVkBm+qxb6vAoQ7iM0AipioqdN5BwoWGTsKm2uf"
                    + "PhB2CP7NNPGuQFQxM4ax5UcOrLnQ6lE1AHS3ZDw9uTZHQnIaZU/ChBRuseafTOt+vmbaOa1CBi9FmEUP"
                    + "hx0NT17nNVlClIzjXPMlKt9EWGRDedb9o8OklepGQ7IJGLzhBeytgMB597gPKQwa3ihUyt1E4rY95HbE"
                    + "oqb8x3CiliavgNMbK7vx0lBXuLDIbmKv1Bm3fBIr3vFx+n67nCEQt3nnxvNQDYBbjig9U1bzqkOl2g11"
                    + "H5ScTCG+mHIFTNa4Lm3aBPKhALfnceIR7nKkE/Ba8fJeQf5IdTAHJjPlr6WCKgAXRyOA0z+S/VcqOEDE"
                    + "4/miUiKWM/omL5mIpsynbNFHv7oJ4UM8JyTXEupS8qjAm+SRrXcXcE+YmATTPHjOgJ1/bGsS1dhsXi0P"
                    + "Og3swdIa9IedQKyNWSobXvlq0/KbEIKRyx7cMsbahv5DCCH/LoSIbUy2XlcO6ML+oXIARbng/mZBCl3D"
                    + "G6Hgda6QuO0IudIz0J0xwAh0Z0R76inglXpoOZDZ9WRFdFvSOjYocyM1SvbDku9Zi4nlB35QVoL3MDi1"
                    + "y+gUyfbvECL8YwjpqnIyUE3kEe2WfXMdQrh/ZJsMUmkbF8R0589vjDJGAHMpgL2R8w+1eW9JQxisw7XV"
                    + "TsvIleVOQeBx7aodyYMa0wdN7iwwdLS0jA+aKb7+IXw4vL8jED1tuqjwZbHVamv3Yg1D5P4WkZJAmieP"
                    + "SRGqQswzSYoEQ0vnJs9n8SunPn3aaStkjLUJTqzx8rxqZGOzOnb54/LBu9NWXr0nk1v3u5SDsGY1UZET"
                    + "sLRhzuuvYk6jYtc4OTXgBuVnEfCY3NxMBTIR5btSF7yKetIYak7EooumUCeWeUe64tNo3eKov5ikOft/"
                    + "tsXNe7nMWqCaUITTJzHvKDgYlfZ52LiKpelyt2UGSdyJnwfvVU9Seblrm1lLKTaUeJjxzYbuRXwRf1B0"
                    + "FDqHU5T9FEq7LYcBGEoOdxeuHyUFTNyz1qenxwNx+/bnu1nRyMruR2lJ8DrxCzY4ox+DZCRGgloVz0FH"
                    + "wuNj07RFE5d/0UNCSsXNi6VdTJivlH/FCesDIYxoxK4SLKNz9Jh6yelM9+za/ZAVgoEaQvRUgICdg1x/"
                    + "nXVhOB53FoMUy3dYlqIBqQobudAoO1JvokZv8IGc9032V2fcSMDfpxyUcrHasIzqoscgiye3j8s0lBwd"
                    + "SQkwj/mimxfEeXAVoORCtxZlgxt5odoD1FrskBSiNmkqOfpCQ3km2DY4IBY1GCoMLR48ReIe6f6ZY1l0"
                    + "QGWN+Q1ad52KbLnZakU4w5umc36LFvrg0SnaJEYaLuSt/M05GFc8XLpTrRUlmDrislyjtSjvE8B81Ym+"
                    + "GAbjXtMxlmaXEEpabbpz250fGQY8sF/2AbHrA/lThm927w+Whzk5UM6X/oGyU373Uq1uUWQc59NyrrJN"
                    + "qjEJF+VdmN2AUoeHZHMAWW6YzK/MxF8h2vuYVSyQ6rEQmUzZtkaSZ2aSn1U0uEE0BpJjnCrysKxEi5TH"
                    + "DRD57c14JHQgRpkUJ4Wb6m0QJiKaefl2bd8boLFqfaoqusXrQDMhyrGeC/mh9Ok9HRVHOpaleCv0I12K"
                    + "ewxvA02HLByeGwjUkJKrHL3RkRRcDIcdz12Og4WFRtEvq4IeYuRC0RHSFphaxPL7Gh2GiZR5RW4rgxbE"
                    + "zJe9OmEeJ+LhKtYRy6RdxJ9B2ABzYNS7nP9a1XR+tjMqJCDVRiWTrNK7NXtzmslR8l2Jylm2pHfIoxYu"
                    + "oYyp3UciUP0lwtBud02VdL7GD2wP4uddtilMHivGyaLpZByvCgp9UkHjSZpZNPUqlKhAFn4Zr4eCc9dD"
                    + "AjYHgKVAsVkQ5kGBnf1RvjY7tGeCGLKEMTfVD0untz1oET/4HS+ggwpgdUZ0RFCfnffqKVfL7J6V+hXY"
                    + "MvZ5Zp8OgZYwFAYCgvVnBR8nZ0crE2cNewp1E3vgX6VLdSNlfNF73tRwbsuk4di9xraM9VmatW+nEDXv"
                    + "WYmL2XSTIhwG8K7Ive4g5EX5JvNNpjePy3b2c5Pwi509OUz1N/HkyTP9I63e7C0Xu2cNmRAZpLtBoYVC"
                    + "umWySQkf50NIHrHUkJrMS3feTTxU66P4lMpWkKQ8FMqSGcva/ZUe4SFknphPYRKtFnTj2xdvJ2BoPN5M"
                    + "fgCI2zSc1D/J9zefAgwXUc6maw1XpweapyTm8gabUYV7zlVFvVCvzmnlo+1YIhfwUp+f82UW2bxiaafv"
                    + "aChpALx8weupKNI4zUqYW/w8qt5hcoxdO6/WoghYmm4ZFmw+hfGOU2KfaWMyVk7h/ZVSfbTqW4I7tLqj"
                    + "mKcz1ueGIlUZGGXjRvrlPasBU+EPSqTWEDGR1X3fWRHtuMrmSsmgUi9ckJBg7NpIUELWZESiNipWGmrW"
                    + "ST2oudu3OCfGUVcwauw7rq5M281BwDc7xtiytWb2wlUIf7JGm/N9waYzwNixV0dESfP+y9UPbbNpM1YF"
                    + "sLEvE0wtTF32XlzNGvpDvaMdjtTpFHR1scACkDOSkhs6QPjAqO7RhJqIdvKIIVIlJxtTdY0gi0FK2XGJ"
                    + "PC+crVSYYEOLOONscHMu1Sac4P9mAAldevYsQThu3bbQy+nM1DMhQy96sYKa0DzlzlGcoPIQayDLNPcr"
                    + "R5JlpmcSnnjsI8+UaopV9QJxzg6fVG/Cv4qxyKxAWkEKZlr+oiDT0U2EUSl7rjpydvTOkvm0gpmGySvo"
                    + "ApJsmLg55ltoqWNyUH/oIU9UKoOJTlCEz5PaSlzekK1j4g107GRngRVe+egqhVO1AWrrQO+Tg3v0ooMu"
                    + "KOfIj1C8yOC6CWjsUL4SRLrzwANZ6BxYqL6RrL+fSCgP1UcuwXLD8alb3SHL0spj6ptkhy/UVk+mys5K"
                    + "3ZJ+OmVfLJ5Q4PgEWdbtJI+qsuuSbv8j7VhNJx/VxArYCVITFHXnF09ocXuAvaOCvZ3F9yc0ZtqNe87h"
                    + "0JCjD/hxGZQO6b6jjC19UF0KSj4h74JO6JkPsR/fCXyG+qrap4267NdDlc49OJL6rq3i4JkyhH7aY55D"
                    + "4PuVyGxeDjMhNMFiaHF6jqH1qvGaC14Dh+CI2kktph4ZbMdSiay6qnm5XCTQCe0BOqFHtu53Xp/QVXDB"
                    + "s+ElsY7833TgpAYHU/Dfd69z3iUPHl4gxHtt0RV7aN5ARFuVNQB4iWlqjezP4R5WTZNv+uL1IMYlq1IX"
                    + "iQ8xjJWwe6SVRtm7Z8bodk0Zm1JKf0qPm3anXWOLKEp9TqxVyNr8S9X6WiF+2WKglFND5bIX/73GQahH"
                    + "3gckjrEev1z7IkSC1QHR4c6qgFOwSjN8Xy072a8VJ0Tt/jGxh7z1JhJcexMH8QfErm2gVj8m9mnWZLe5"
                    + "5ul1Yi+C85xoVCDtcaNhkbJCRp9jFvrg2LInzm2i+aIu7YcbFM73N6iK/HrHww7gwxGJCXRRItKEMhlH"
                    + "6/krOMJUiV5+7TGkc9MuoL4eC9aOmu7qfsQnUbK/Ek2UAuU/E82QvxJNvOtEMx2UaBJ3Xxcnbu+qN8UJ"
                    + "yw8tT/9MNEVPM2fMDWnGhZ48pIxvr/Onm0GRFKwOia+QnRzviHEyUEnkkXozGBaXGPcGTBN4xBbgS/Fr"
                    + "NlApRyMTMAn9pRIoE+p/nW9r6afWBgvb8981MG2T5TvuND3Jfq6hgZTfDrzO+Jd22tqEhe4so5UT8Lgt"
                    + "d/nJwy7IaEv6YySTBlzl3QuL0Q3SQdG+rgWFJLA5Rjk9GJIM1qO1DZSUgz1rM5tXKMqnv0Lf16F+i5/Q"
                    + "eRzWqS+IkUPqLyCmez9Gc9L7Iyrfm2SF+nrD9U80xkB1g3sRxmHBjSFfWp9nEzIvbsqofCeDET2SIaRS"
                    + "f12o8xpVat438xNwKNP7mTnmrQx26XuWKF0yX4eCTa+AHDA3a+V6n5DFcCzrMcQpR9Z5yY0MNkn6WOYc"
                    + "RQzn6fJ0s/2YBI9fRoI6vTe9BVjQmOY336cFMb30800ta+d8W7KPfiUPKYrbl3loI09fxxwaKoSSg3M2"
                    + "VhUX74dWvo2dF4J74oKHZ2GWjvX1Hd8x3LG05AG6GVx2N8tB/4FHsIeQ0NLiHb216U6/AHE4aciZ/ElW"
                    + "BNMXdZ0BIk1GCpb5mtlNuWGRdRtvJYZFSPTLTt6Uhoi04fSNh+4nmJtlm4GS9GI9Ob84O0BpyniNblF2"
                    + "Qd2C2PIqq5Eeo+Ly0FLQHKU8VbEdNN03knSVkvHwTr3Xj98vcsGMctF74cGY4rXOttpbp1Tlt6dgiPdK"
                    + "tXw4v9u+UWdw+Rxl7pEc+1MxTlecQy8iqdOK04tuyPy6Gu60idAavqXP03xB4cuf1bxiKdTxUHAe7DvO"
                    + "58NBoBIALpG/TJtzZUbTGIYcqJe7eqST7mWHqqmhkMCb7fmC5S6tq18UjUBMP4b86d7+y6wiCrT6Kwe9"
                    + "KV+k50/SScC0o4X1SeQQ7XXmUndJlxYn1r7tcdc1JJUfqq9/+PeVtUSKh6NxcGR07zG/vetNHIEozzbv"
                    + "yL6jCfUUflsdPy/yiauPERoNdyA8ZYIAJiNXnhUJZ6sovMaChoyQUWLSUeAZJBc1FfQogA7WYQDOPFZK"
                    + "FVc+7qqzgDJPvjd04d4A6howp/t11wv8KB4/f5wCh0wX9yfe8XqnKUdJlFGhBlE78ehFX62FzWgyp8NN"
                    + "YGkqi3bYBgPDVWqvUPPvw7YKi+cs1t7PkM5K9O4sPAyVXU83X/vwRLEyRziiLfO9sNsctNb+3FXZGzIV"
                    + "AB9qxoEQl3e/ZPHTt/WHtW2mdkgBHVUjlOmLSSeUfmsjTJtvijFIosXVDZKGTeSzTNr8k8397pTgYqI9"
                    + "EqefF0XD2x3GC7Ut0q1EoJxnZqUjj7eohJmrzO3CSfjQleF4MURIo30Opi54HVhhdYOWSgQKAoL9d31F"
                    + "fyaaaqrKGKL3rhLr02A0RO6pokkAZuFeShB5N8eTcxW/3NyflCEemUB5Ffvh0cuX5DsGnzqJl5+xjVIZ"
                    + "J3CwCszv7Y2cvfLi5hf28blAWcZlFiRwd99fGetG8pst1MOiqJxiDT6qT5yKPQFSqETJPsUFUroMPikz"
                    + "L3lMW5FXkY93IRtDz+5DX4mGG4dVrZLMm4304L0YxEisrjQO5NyrKUO0MqpXIxvaIcl4tP2JulkTgHRN"
                    + "HUocHdlqNuKJ4on5x9QFFflDg6e0mTQ2eFezWFjTUfLqRQjeGlrpNikRVZBwvpyJvug+CjB4W1WRfb4k"
                    + "lwolKyM7tm4BFGjTDxVkzIQK/B1CIj5lFyBoK1ioG49dZr2LmCD5IPGSeWfLh6byq5DCkiS1wDPXSfmJ"
                    + "z+L8Ucb7nrMILQRlZwE8hjIDVdoTkUcKUV4PlQofPPpacrGZBD215Z/zSR7/6GKPeZInM73jsCyF4HIx"
                    + "m7vBpOnSGt5nl/Jqvka75GuJzsoc3RIdtg1BY8Tjd353xN09uaF2pxtR0ozi6AUN3RAeTm++sTggLdwN"
                    + "JBToKneCK0IjWgtjV06fcdpMZr/QMZ+ginthF3Hfxd1r0WoS/Nc3dt5MRgK0J1BAv75s/74ntKycrIwB"
                    + "ZlL2jhqgS4jV35mW6h9ayIY4tGQHKqbgdpil8fry+BvzO+IUlQ+49BT9wilt5F1bcL/9+TtMKSHDcchi"
                    + "tdTKXZH+h2mygWd4hSJEF6UQmff1AXJ6Vm8HH7Kkdsk00CxYQEEiZZI2OHarEXXUe9eBsm6HQ3zx04Xo"
                    + "8F1dxdQBFlPPy8XYEfTiVzWhj8wYcCeea5vqO8RPBN8v1lQXOKTgQA7yU8qpshyONn2bHblBg2lLCEPG"
                    + "paZob+7ViWftkxPphUbv8wGDXc+5rADNkdP2I3HOChJ5/MsFVlOcepzgL8JqyiGOX82R5svZcVn0nDjD"
                    + "atM4Vk2F3sXMlH450Q4+mm0TTgljPXG72orYhX3P4ZJHMzGQJRWTf5GYzmcpPL3Y4dEVdLdV3N0NCqfT"
                    + "EuYxjWoBAGeSNAKTTt6RGM45FjoEUxpSk8AX6sc1uZTiF/1FwVXd2eP7lpHvipMk16aRdvSnoHhaiChP"
                    + "8QBqhvIPwVXWirtFH/3RqwDUZXG8QqrwEEXAxDcFC583ySPX0jbEfHcTHDVRmiewLwsvlUweSIPFZtnr"
                    + "WsJy3Z9zv3OZ7D/7cxD+7s+BisO87s9BRS7/uz8HXlNld194u4cmGaAvKnzdn1PdakhXTam7ajf8yU+M"
                    + "e9E5lUz0ZMvwCwcRi6WCe88OmsSkEQ+iWHhdt0NmSAU7UdRnE1yWRgzPGLymj5/IFBXNv11d1PDCpusv"
                    + "HXoFhZkBNfmG7NL1YZHpnM860hE12utgXaoFwz1Nz2iXRCy77hDwncG8gGe05Od1nHBw/ArzI9Uo2Rbr"
                    + "1w6/GdDD+JNGKCs7UzPH/yZSHY1DIkGtmtAYk0KRq1tF4CIWowcEJs2va+jlu8bzJXx5h/v5zleawFci"
                    + "/BJdsUv85xkt75pv7hjcPEdjh8DllehsvB2yZzuPCnrGmuLostPatyhid5N1EIPXKlWkRnckmdYp8jPU"
                    + "ZuHJ63RZ0p4RrBWKKXofan+E42PazEOmbEC22FCQ6UPnqpHxpp3omnX7JtrPl3A4xHks/ZLGmIfWfpks"
                    + "M7ZqXtLDOyn2gsjAzDS7by1aGBoqWEvYPdz98/zWtUpVsUbanX+jUjFfq1QzaTVNjMGns/RJIzRcyIX5"
                    + "p7tYtyqVwbVKlXCtUlWf2F6rVI3HdzW7mE7T9vitvoHVfgdbTAuxQQS3/Xw/39ziQFnZ35c4eSjQJW6y"
                    + "3qak7GRtVRQziEigMYRIsLe5NyiBsUWemYhiITg2lsoslfH4D4NDj8SYpw5LtIe4+WLrx5ucDBm4uTLd"
                    + "HRai7u/6Yphsi5yUV9SiKJ9JzGwfG9ANPxqnhle1jlGFrT5YrDbLQl6ri6EnObomX7ApdIH1NfB+OUEL"
                    + "tSUgicEW8I14V0c+xl6/myXHSg6ouhBdZTwadaKvveu3Lp+aZKs9n6JrkjK2RYKE7l9QpnxILsodHsLh"
                    + "mT5GkYeqaij9tIukuuuueo1YRHI7kza9flJ7k/BD0u0Pm31vYF5/eGO2oe1a8h7j3uTeQgA/52R7lV9B"
                    + "WUOih9bclmV2FJS0gcoWj0S/6LvOmIBFsOrMoT+rMe6veVRBV7j/tn3FaG801qK/NVZSTePoe3GpaeEl"
                    + "k0/VCtW+4UMJPyEOqsTT/U5jPbk8RPgnjdW7bvOAN2zdlUnLf4WDIT4GyTNGnlexuCKeq0umLBH5uUn/"
                    + "52IVdgMA4pVKf69/fPb605WcNIRBnS5z7OIKCV2dA1C8kChke9QS3NlILAna2Xf11tnZElvoKp6pOS6t"
                    + "7UYyTLoTHIYYDozhQyujoWbCCcDjWX91YTNuxKYbrFP4ahZjxsftuwXQ6h2bwYd5w9sbvNI6+bilnQlK"
                    + "vHeRbr4467TEhQPQWlWREcVf3yZvmRMk+LwtW25aem7QLw7cjK2WxDdJBeJ2/+cR4iYY/9cevuX+zoDD"
                    + "tSfc1/2SQTf9kuhMf5r4Ur+7KHveQ0mdzrMv+LR4rLh5XSeAIFqZCGBHHRVsNByLVIyh5IlHxHM2btGk"
                    + "2UQkH3lMX4WGXJ4j9rCpL3mEEk3PxiQoQWoLeV+s8DBhTN7gK9pVB0Uvfl1vL0bAnKNi4E0PXz3aC5KL"
                    + "feGJMFW2meZgn4G1pE4Xj24NU/navBbmLaggu9YdLkn0twYBO8PuHVmsK2iF0vdiuoGYgRo2QWtCqEsW"
                    + "AviHWyyGH0n+6JmUTEkZ2f/W0i4I+yH9zL2tq4etkZbQomGCCQoWqxluWQnmRbtOAF8vkaZgeEgP970U"
                    + "IHQ/k3cAXRdCLUEanyX/9lfwaKEVgX9dA9v+VdP1P5bA/pAlQlcGiASuS2BxnNH6Xb0BtMOB8Qzfaz5T"
                    + "UO7TPVTeFnyfFCzYbuwiRMCVlcnrKnqweWkEQ7afQlg2VqrCCw8JDzfSKf8VNrr4GPmUAT0l9VU5K/LI"
                    + "STqLxruEoGD8yrtK8bN3Oqa8WrD5M2TuDQWJPvSpGhlbxomu0/2vAU+d6eKi6LeDBJ7ilQ8h9ZGlxlXN"
                    + "63sIyQxGcS+WDoidvDP8KrDHziElsHqKJlHfvUwJb1uKhfJYwCvz43aQcNCd7pcqNQrDyZk4JQmjI0u+"
                    + "DSUmlgf4qIVIcsjdLjsMLd6uuKDAvN+ZjviiXVd8YbY8ynNqf84rYKznDkr6RTDdCvyfthwfMLKpPwXs"
                    + "/WKqcROaf6+2Kzvze7VdLFaOZt0djpzSGsz7nUC0jbAY+pQhhVHMQ4pxKdziuMgVKufIConqj9dtSjs8"
                    + "BqrGllcdQP0RCVMGI8EIDS6xjXqXC2LnvIR/qu1CSdq1DroqYr8QgosrNHqWzbWK1vCMpJrwSwBJbZU5"
                    + "z97RR5eh3Du+yFDB/DEj/xu1XSjmnh6KX9vXCkYe9i7/bA0Jv7GGGOuwISWW0yHHmCKbkgD0ktjSomOz"
                    + "KdkJQWld0HVa9xBn+Q9riOBS0/pNM8H28lB3UwgS6nPkk6rRN0lf2EiDpWjq+bjRzD7kT8MH79JoS5Vb"
                    + "0rHzyofS39isI1eErlvC6j72Mi3/VUdnncf6vo7ecvJHHZ0p+4brAh6jG64FMftv6+h3qZh/rKNPO+3i"
                    + "unp8FtoYfyIccqc7V+WTxnBT5sZundKrpeaWt6wcZ1EhNZSgiF0PTLwUeoiMTPn4piXMoCzL2nbuynOK"
                    + "aNY9SqUf7SloW7OtQN+lLlRKURoF39Yy7SgBv2jCUTSzc/lHP2Xon8Jm3vWujpdRg6QNjICriUd4oFaf"
                    + "t/B3AHlg8r2f8tqE496RH8AOt4XCBP0+jkKZ/H1JEjS++ISHzAMLyga1ZbqRur/clLVKkRx048kd+oZ4"
                    + "3ImeO0NLgXPyOV6Ks2NgbsqMa2GTuirctT7rvntcWMe1CadAnm1kAWfrMmnP881CMO4LlMudT0XUf7sp"
                    + "L/2IgaV4kc2PPvJ96vI7w/w8r/ZeMqUoKHUjVTE2pd3bV0gpq46YP6WChg2SOyh1z7eGkURl9THEYzka"
                    + "XxsYzXrCp3wAVF4D8kmHwGAXAxUqglyrjR654SFwE07Oc5Jn127KOz8Xf8QtzUxsQES7AP5K7mIEr13X"
                    + "wUsJz9S/QkpiIAXo3rNt7ZoIh+iDDFgJjXrrGLm1WiVv5yb72Qb7poRejhYuz8DFXvNppDXVrd1XqB1i"
                    + "foWjw+GJ+RMMv44vFKS70CnvuAOS9YZYEvgQyJ/Tps8jcfDp3qdOZ2uWtp8ojBTLwsbL7D3OUkMvclui"
                    + "vcKN2di3bRNfBqzXqTt5qmopqukyV0E+v893VIF2qrC3lUeo+OA0JwkvxsSfXYpOPtlTy/5bgFCqRm+J"
                    + "Vxb/tkRMh8dhh445Au9mefjFZfzRVpQ2knHRl0liYZTHDMwnHOzW+LrtOkkjtk8Bn7uYyhpQqNnDz19o"
                    + "0tRylTBOv2cUorziUcWmye3STCiJlewos1VqqAzA78c2aSE4w52hJli+dyLgHUTCAQTvdwz3KuH/xZkC"
                    + "1/27YO7V/yUxWUjbkTGSSYwh5CYf6ZbDa+prYtggEy+/yfn+X1aTG9JdZA1BVDv9vMR+W5v9Syz7s8TO"
                    + "qFtS5lYSSqaL2Qn/ZTOCtEA3UtZKS/QLyZwzLe2OnSxX0FUKqs8TYkolOp1A/y8sFig1Fkz8LTe968A7"
                    + "/PgA68p8Uc0AW0iP18/FlbIm7/lLmw+sJvXjElWxdH48P5SdM8+5P5hV0j8IpCHPZyfNM6aLFrM/1JH0"
                    + "qygSSMNsJlM5NkU/byUKTDLIQBz9AuiB13gYptL5RBjGYQyh6VAfqWV/rlgKNXn7EpDTKIOnVXFJZjXE"
                    + "8rGfZiS1fsUD2tWCWgX7pFFsoXPPiX4dqlSmP44/4W6/Q7Ho/fJPYpmp2H7zz+kFdI5HX5JWGjY/lX74"
                    + "jMrl4XTrYiUg0lqxu5XbTtX9uJASvUih0IHfe58e8jE7XBrT044d4UdFUk9EYBkjlvzWgVJJ4olBHj3I"
                    + "mEZ3Limr0a05UCIqoTfi67M7siGHaUvzKlO6dBF3aQEid9gjaF5lb1SUZP64UDNNjxNCQQsVB/nT8/XG"
                    + "EHgragap6cuviWAKNliVEdtcTn348pKh+sKY2OvYCkB2P1RRRlzVK7oqPUHGVS0lbPkPSRP9AF43yD+w"
                    + "ifcszYmd221z53wFrQPtdHe62KThqaBYbxT8OsbmXBNT5GQBdY0aLwC6ZLxJsqfo/gMNUg35dHz/Txeo"
                    + "2owIZU7kiik22pdxZn1DAG5maW+XFPPwMeCbb3I4pcSLQ/yyln35XCMnE2QvRycb20j24KbeDOW1WQ26"
                    + "ZW9sXsYKG08uJ1Vyz2DQd6NIAYoXZgxICLyMoo4YPizN+buSNOt3na77Szy1izdaej3Y/Jx27pGjsAYg"
                    + "he9TbHu2fFKYBAPPofHiyuQZ5Ca+6DR2p6ZyTxsPgkCX/7ZzlwSFz3STHBOZuY+sIO7GTpZmfLeREFsm"
                    + "1SGXQtdqY8LHL1onHh9bMFruK39+shdz5PjZKiccf4328YsiJ6FL0swVr1NxIbgX6G1GGcmw7E9fJyJi"
                    + "P+qVZHi5SA83/PWR0fCLLvypKqMVSmvEpfHalZC7qVyAmcpLsHzJM6dz8jrOTYIeyn9fT3kzM+DthatL"
                    + "BDNNMguS7nOyOws+KsGHO/YZKbrxTxittDHWOhk+iiXG19P+XVFoLAlzJ3GIS0/eSz1zPboRDJyBxitj"
                    + "XTFBJsGe8djnSc1sVMG6mTQtKWH5hjx3IyQGeQUHgwh61ylkXwTGWlUbNuLiPiOMMLWOWPInjM0BrXHw"
                    + "kFpPENHMVT+fggnvo2RYt2UJtO6ZTVimI1raDy3zemfGKbthXgkxXA/E+z/wuxGJJwujcnISLqE2dj/2"
                    + "+/bl0wQQSnh1JXc4IDr7y/LhxbJW//Zpq9+mU4zCkYbSnmSMmVX51ZVZfc+Y7ha8FDs+/UIt66yniy1s"
                    + "cGzbQ5bCwCNuLZW3XmUJ+TZzgfjvLXHP/emM4FpgT5E57tYi793n+QzGcyG9tbgdiGNzyJ/yrG7l+Zd7"
                    + "KOb1df9ln4WfGindZwMNaN57L6cDpJXp4+GL4+ODe9xJkEpNZa3uW4a5j28dLUN9VnkP0Nj26xEcGfVa"
                    + "snfjqrnp7AHCUGIh0mFq8pBnjSRhOEIefoqA9WN83/yoA3KlSfNqQIxapc0Hh8xkha7iY43pYEb886AW"
                    + "LindzyjaV/CQO/U4Sl9aqQj2oq0jLHx4jdmlSCyC7Ej6WskLcrY+mr2j9cshxXtltK9igiRe496hn6yP"
                    + "QfMtoFZA+ngLng4roH4O+qgEdRrAtn7HMf9t2/5H1kXL195PVV5IMnoWzjVLGr5hoZ/cdi3SLOQOViIJ"
                    + "6yt70JtY12y3nJH9qsBKvcWOq+XUGykG0gkOQxm51mr7YpEi1DW2hjRav61rL8pduEOcnrieQCd5Wbtn"
                    + "3W15AAMBEkGlUs4Yy/5JuBgdXZ6qzjPE+zSmOWLY9nN9HLD8lREtwTNEswgoF0CIEDZviCBxlKemPIkr"
                    + "EsGtyIVgXcm65nn+v1i0VzNy+7WOwomFPxtX5K+V+r/EYQIKKtCN7Vvy6Fcq9vQbtdL9iTzqqADP2bK/"
                    + "KDubZOKySv5AFuV6S0drQ/z9wWU5zNkXWEqa5ea0jmVRCVp4f8xuKlRj9jpP/QhHRatVxDhKjDefyA75"
                    + "hzY8UJvVt+A7FiarWe0CcxktjRawe+SvY45U27PwfCsAc8H90DUK+q2B/2cPAcOxJGDnDhr8exYCGNLe"
                    + "2ZXG190NtDjkV5iwx2bFW8mqTsLMUzZxM8IB+6d9WTUHfBmMrFXIdnJwM8w3DYX7dxrV/d4r/TC7qXZ5"
                    + "oKniu9lNYAe/B2+D4i9Kw9r2jja/Ig2LSxe8YDSmyZNDhmdtGixuzHPY+DdpmJDAw9KD5yNwQOWZRrlJ"
                    + "cwIta21Ggg/1qyWOsHVPukT/FZ4RdnKLYwocxzQ17uF4NKkXgE7g9bUtHLAa0e6/pO0glDui4tgN1Br5"
                    + "oztW4znZqzLv6yuyGl6Fnrj9dBRZcNwqLNNxYOQGU/huQPz82WdrJewXC6if9ccsLI/9Q0JDngkSBSJj"
                    + "XbHhs+iFM4zLUV2RZb5O4Ep14mroHdN7DLNGC7qr1Y75vOzighIfXGSbp80+KtGcBLRWV2REPaxvUyzK"
                    + "9SN83pajMN2bEedN/YpYRf4OJCbUf5/J9tf8sw+iEN8PXgP/GviEtr9+8vxo/8u8tn97MfwPL3b9brX/"
                    + "7Vv3fvhWxj/skX/79v0fvj36E7c3GBD4ALfb/32EO78+zg0cFXxc2S2qA+TvDS8DRwafzHWLvAz1G3O6"
                    + "wGHB50ndwvrB/NJ0KXBA8OFIt4AwcL8+KgkcFXwU0C2qLfyvDwYCRwUffHOL+hbh/2IMDvhrwGe63L7G"
                    + "COl3JryA44JPObnFLUT+n888AX8L+AiP27ewoPzOQA9wXPDBFbe4sai/PsYCHBV8FMMtKjbabwxmAIcF"
                    + "n0FwC+uM/ssTCcBBwfvtb0EXMH6n+x4cF7zj/BaXHet3+8/BscFbrW+xE7F/q/EaHBi8rfgWGAr395uM"
                    + "wdHBm2dv0R/j/WorLTgmeLPoLeYS/m+0joLDgrc/3sKaEvx6MyQ4Kniz3y3qCOFvtP6Bw4J3tt3CchH/"
                    + "Rp8bOCx4F9ctbDbJb/V0gQODNyzdAqOS/U77EjgueKPOLa49+W+17YADg/en3AKPU/xWtwo4MHjHxC1w"
                    + "EtX/oH8CHB7cd34LX0X7Oy50cFxwv/UtLhr9L7uvwUHB3cW3oO4Mv+c1BkcGd9LeIm8y/rKvFhwU3DB6"
                    + "C6rB/Hv2UXBkcH/jLfI3lv+R2xH8BeBmudsXYLD/T6xz4Pjgxq9b/EKO37CBgcOCm5xuYdG5ftPy9B9p"
                    + "EJi15xbajfs3jD7gsOCmllvYSZ7fsbiA44KbOW5xOfh+09rxH0yA2RZuofP5f8fE8B83J7AK/XeLJ/gb"
                    + "9XpwWPBq9C2st9Dv1KbBccErr7e4O8K/XocFRwWvM96iqor+XtURHBm8mnaL3Cv2e7U1cGTwwtF3e03i"
                    + "t8pI4MDghY5b4AXJ3yl7gOOCC+63uGXSvyi//4dMAaYt30Jiyf6i0gwOCa5+3kL2yf2yFgoOCq713YIy"
                    + "Kvym8qciDwN7/VUe0J9l0O0qVvH6v/4PSKCfCIhgAAA=";

    private static final String SUPPORT_JAR_PATH = "libs/support-annotations.jar";
    private static final LintDetectorTest.TestFile SUPPORT_ANNOTATIONS_JAR =
            base64gzip(SUPPORT_JAR_PATH, SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP);
}
