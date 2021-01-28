package com.android.tools.lint.checks;

import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;

@SuppressWarnings("javadoc")
public class PrivateResourceDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new PrivateResourceDetector();
    }

    @SuppressWarnings("ClassNameDiffersFromFileName") // Sample code
    private static final TestFile cls =
            java(
                    "src/main/java/test/pkg/Private.java",
                    ""
                            + "package test.pkg;\n"
                            + "public class Private {\n"
                            + "    void test() {\n"
                            + "        int x = R.string.my_private_string; // ERROR\n"
                            + "        int y = R.string.my_public_string; // OK\n"
                            + "    }\n"
                            + "}\n");

    private static final TestFile strings =
            xml(
                    "src/main/res/values/strings.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n"
                            + "\n"
                            + "    <string tools:override=\"true\" name=\"my_private_string\">String 1</string>\n"
                            + "    <string name=\"my_public_string\">String 2</string>\n"
                            + "\n"
                            + "</resources>\n");

    private final File rFile = createRFile();
    private final File publicTxtFile = createPublicResourcesFile();
    private final TestFile gradle =
            gradle(
                            ""
                                    + "apply plugin: 'com.android.application'\n"
                                    + "\n"
                                    + "dependencies {\n"
                                    + "    compile 'com.android.tools:test-library:1.0.0'\n"
                                    + "}\n")
                    .withMockerConfigurator(
                            mocker -> {
                                mocker.withLibraryPublicResourcesFile(
                                        "com.android.tools:test-library:1.0.0",
                                        publicTxtFile.getPath());
                                mocker.withLibrarySymbolFile(
                                        "com.android.tools:test-library:1.0.0", rFile.getPath());
                            });

    @NonNull
    private static File createPublicResourcesFile() {
        try {
            File tempDir = TestUtils.createTempDirDeletedOnExit().toFile();
            String publicResources =
                    "" + "" + "string my_public_string\n" + "style Theme.AppCompat.DayNight\n";

            File publicTxtFile = new File(tempDir, FN_PUBLIC_TXT);
            Files.asCharSink(publicTxtFile, Charsets.UTF_8).write(publicResources);
            return publicTxtFile;
        } catch (IOException ioe) {
            fail(ioe.getMessage());
            return new File("");
        }
    }

    @NonNull
    private static File createRFile() {
        try {
            File tempDir = TestUtils.createTempDirDeletedOnExit().toFile();

            String allResources =
                    ""
                            + "int string my_private_string 0x7f040000\n"
                            + "int string my_public_string 0x7f040001\n"
                            + "int layout my_private_layout 0x7f040002\n"
                            + "int id title 0x7f040003\n"
                            + "int style Theme_AppCompat_DayNight 0x7f070004";

            File rFile = new File(tempDir, FN_RESOURCE_TEXT);
            Files.asCharSink(rFile, Charsets.UTF_8).write(allResources);
            return rFile;
        } catch (IOException ioe) {
            fail(ioe.getMessage());
            return new File("");
        }
    }

    @SuppressWarnings("ClassNameDiffersFromFileName") // Sample code
    private static final TestFile rClass =
            java(
                    "src/main/java/test/pkg/R.java",
                    ""
                            + "package test.pkg;\n"
                            + "public final class R {\n"
                            + "    public static final class string {\n"
                            + "        public static final int my_private_string = 0x7f0a0000;\n"
                            + "        public static final int my_public_string = 0x7f0a0001;\n"
                            + "    }\n"
                            + "}\n");

    public void testPrivateInXml() {
        String expected =
                ""
                        + "src/main/res/layout/private.xml:11: Warning: The resource @string/my_private_string is marked as private in com.android.tools:test-library:1.0.0 [PrivateResource]\n"
                        + "            android:text=\"@string/my_private_string\" />\n"
                        + "                          ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        xml(
                                "src/main/res/layout/private.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:id=\"@+id/newlinear\"\n"
                                        + "              android:orientation=\"vertical\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\">\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:text=\"@string/my_private_string\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:text=\"@string/my_public_string\" />\n"
                                        + "</LinearLayout>\n"),
                        gradle)
                .run()
                .expect(expected);
    }

    public void testPrivateInJava() {
        String expected =
                ""
                        + "src/main/java/test/pkg/Private.java:4: Warning: The resource @string/my_private_string is marked as private in com.android.tools:test-library:1.0.0 [PrivateResource]\n"
                        + "        int x = R.string.my_private_string; // ERROR\n"
                        + "                         ~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/main/java/test/pkg/Private.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "public class Private {\n"
                                        + "    void test() {\n"
                                        + "        int x = R.string.my_private_string; // ERROR\n"
                                        + "        int y = R.string.my_public_string; // OK\n"
                                        + "        int z = android.R.string.my_private_string; // OK (not in project namespace)\n"
                                        + "    }\n"
                                        + "}\n"),
                        rClass,
                        gradle)
                .allowCompilationErrors()
                .run()
                .expect(expected);
    }

    public void testStyle() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=221560
        lint().files(
                        xml(
                                "src/main/res/layout/private2.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "\n"
                                        + "    <View\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\"\n"
                                        + "        android:theme=\"@style/Theme.AppCompat.DayNight\" />\n"
                                        + "\n"
                                        + "    <View\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\"\n"
                                        + "        android:theme=\"@style/Theme_AppCompat_DayNight\" />\n"
                                        + "\n"
                                        + "</merge>\n"),
                        gradle)
                .run()
                .expectClean();
    }

    public void testOverride() {
        String expected =
                ""
                        + "src/main/res/layout/my_private_layout.xml: Warning: Overriding @layout/my_private_layout which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                        + "src/main/res/values/strings.xml:5: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                        + "    <string name=\"my_private_string\">String 1</string>\n"
                        + "                  ~~~~~~~~~~~~~~~~~\n"
                        + "src/main/res/values/strings.xml:9: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                        + "    <item type=\"string\" name=\"my_private_string\">String 1</item>\n"
                        + "                              ~~~~~~~~~~~~~~~~~\n"
                        + "src/main/res/values/strings.xml:12: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override=\"true\", otherwise pick a different name. [PrivateResource]\n"
                        + "    <string tools:override=\"false\" name=\"my_private_string\">String 2</string>\n"
                        + "                                         ~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings";

        lint().files(
                        xml(
                                "src/main/res/values/strings.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "\n"
                                        + "    <string name=\"app_name\">LibraryProject</string>\n"
                                        + "    <string name=\"my_private_string\">String 1</string>\n"
                                        + "    <string name=\"my_public_string\">String 2</string>\n"
                                        + "    <string name=\"string3\"> @my_private_string </string>\n"
                                        + "    <string name=\"string4\"> @my_public_string </string>\n"
                                        + "    <item type=\"string\" name=\"my_private_string\">String 1</item>\n"
                                        + "    <dimen name=\"my_private_string\">String 1</dimen>\n" // unrelated
                                        + "    <string tools:ignore=\"PrivateResource\" name=\"my_private_string\">String 2</string>\n"
                                        + "    <string tools:override=\"false\" name=\"my_private_string\">String 2</string>\n"
                                        + "    <string tools:override=\"true\" name=\"my_private_string\">String 2</string>\n"
                                        + "\n"
                                        + "</resources>\n"),
                        xml("src/main/res/layout/my_private_layout.xml", "<LinearLayout/>"),
                        xml("src/main/res/layout/my_public_layout.xml", "<LinearLayout/>"),
                        gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.tools:test-library:1.0.0'\n"
                                                + "}\n")
                                .withMockerConfigurator(
                                        mocker -> {
                                            mocker.withLibraryPublicResourcesFile(
                                                    "com.android.tools:test-library:1.0.0",
                                                    publicTxtFile.getPath());
                                            mocker.withLibrarySymbolFile(
                                                    "com.android.tools:test-library:1.0.0",
                                                    rFile.getPath());
                                        }))
                .run()
                .expect(expected);
    }

    @SuppressWarnings("ClassNameDiffersFromFileName")
    public void testIds() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=183851
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "src/main/res/layout/private.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:id=\"@+id/title\"\n"
                                        + "              android:orientation=\"vertical\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"/>\n"),
                        java(
                                "src/main/java/test/pkg/Private.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "public class Private {\n"
                                        + "    void test() {\n"
                                        + "        int x = R.id.title; // ERROR\n"
                                        + "    }\n"
                                        + "    public static final class R {\n"
                                        + "        public static final class id {\n"
                                        + "            public static final int title = 0x7f0a0000;\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"),
                        gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.tools:test-library:1.0.0'\n"
                                                + "}\n")
                                .withMockerConfigurator(
                                        mocker -> {
                                            mocker.withLibraryPublicResourcesFile(
                                                    "com.android.tools:test-library:1.0.0",
                                                    publicTxtFile.getPath());
                                            mocker.withLibrarySymbolFile(
                                                    "com.android.tools:test-library:1.0.0",
                                                    rFile.getPath());
                                        }))
                .run()
                .expectClean();
    }

    public void testAllowLocalOverrides() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=207152
        // Allow referencing private resources from Java, if
        //   (1) you are not directly referencing the foreign R class, and
        //   (2) you have a local definition of the same resource. (In that case
        //       you also need to mark that local resource as a deliberate override,
        //       but if not you'll get a warning in the XML file where the override is
        //       defined.)
        lint().files(manifest().pkg("test.pkg"), rClass, cls, strings, gradle).run().expectClean();
    }

    public void testAllowLocalOverridesWithResourceRepository() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=207152
        lint().files(manifest().pkg("test.pkg"), rClass, cls, strings, gradle)
                .supportResourceRepository(true)
                .run()
                .expectClean();
    }
}
