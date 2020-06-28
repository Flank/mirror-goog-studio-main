/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class CommentDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CommentDetector();
    }

    public void testJava() {
        String expected =
                ""
                        + "src/test/pkg/Hidden.java:11: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                        + "    // STOPSHIP\n"
                        + "       ~~~~~~~~\n"
                        + "src/test/pkg/Hidden.java:12: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                        + "    /* We must STOPSHIP! */\n"
                        + "               ~~~~~~~~\n"
                        + "src/test/pkg/Hidden.java:5: Warning: Code might be hidden here; found unicode escape sequence which is interpreted as comment end, compiled code follows [EasterEgg]\n"
                        + "    /* \\u002a\\u002f static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */\n"
                        + "       ~~~~~~~~~~~~\n"
                        + "src/test/pkg/Hidden.java:6: Warning: Code might be hidden here; found unicode escape sequence which is interpreted as comment end, compiled code follows [EasterEgg]\n"
                        + "    /* \\u002A\\U002F static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */\n"
                        + "       ~~~~~~~~~~~~\n"
                        + "2 errors, 2 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class Hidden {\n"
                                        + "    // Innocent comment...?\n"
                                        + "    /* \\u002a\\u002f static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */\n"
                                        + "    /* \\u002A\\U002F static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */\n"
                                        + "    /* Normal \\\\u002A\\U002F */ // OK\n"
                                        + "    static {\n"
                                        + "        String s = \"\\u002a\\u002f\"; // OK\n"
                                        + "    }\n"
                                        + "    // STOPSHIP\n"
                                        + "    /* We must STOPSHIP! */\n"
                                        + "    String x = \"STOPSHIP\"; // OK\n"
                                        + "}\n"))
                .run()
                .expect(expected)
                .expectFixDiffs(
                        ""
                                + "Fix for src/test/pkg/Hidden.java line 10: Remove STOPSHIP:\n"
                                + "@@ -11 +11\n"
                                + "-     // STOPSHIP\n"
                                + "+     // \n"
                                + "Fix for src/test/pkg/Hidden.java line 11: Remove STOPSHIP:\n"
                                + "@@ -12 +12\n"
                                + "-     /* We must STOPSHIP! */\n"
                                + "+     /* We must ! */\n");
    }

    public void testKotlin() {
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "class Hidden {\n"
                                        + "    // Innocent comment...?\n"
                                        + "    /* \\u002a\\u002f static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */ \n"
                                        + "    /* \\u002A\\U002F static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */ \n"
                                        + "    /* Normal \\\\u002A\\U002F */ // OK\n"
                                        + "\n"
                                        + "    // STOPSHIP\n"
                                        + "    /* We must STOPSHIP! */\n"
                                        + "    var x = \"STOPSHIP\" // OK\n"
                                        + "\n"
                                        + "    fun test() {\n"
                                        + "        TODO()\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun test2() {\n"
                                        + "        TODO(\"This is not yet implemented\")\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/Hidden.kt:9: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "    // STOPSHIP\n"
                                + "       ~~~~~~~~\n"
                                + "src/test/pkg/Hidden.kt:10: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "    /* We must STOPSHIP! */\n"
                                + "               ~~~~~~~~\n"
                                + "src/test/pkg/Hidden.kt:14: Error: TODO call found; points to code which must be fixed prior to release [StopShip]\n"
                                + "        TODO()\n"
                                + "        ~~~~~~\n"
                                + "src/test/pkg/Hidden.kt:18: Error: TODO call found; points to code which must be fixed prior to release [StopShip]\n"
                                + "        TODO(\"This is not yet implemented\")\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/Hidden.kt:5: Warning: Code might be hidden here; found unicode escape sequence which is interpreted as comment end, compiled code follows [EasterEgg]\n"
                                + "    /* \\u002a\\u002f static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */ \n"
                                + "       ~~~~~~~~~~~~\n"
                                + "src/test/pkg/Hidden.kt:6: Warning: Code might be hidden here; found unicode escape sequence which is interpreted as comment end, compiled code follows [EasterEgg]\n"
                                + "    /* \\u002A\\U002F static { System.out.println(\"I'm executed on class load\"); } \\u002f\\u002a */ \n"
                                + "       ~~~~~~~~~~~~\n"
                                + "4 errors, 2 warnings")
                .expectFixDiffs(
                        ""
                                + "Fix for src/test/pkg/Hidden.kt line 9: Remove STOPSHIP:\n"
                                + "@@ -9 +9\n"
                                + "-     // STOPSHIP\n"
                                + "+     // \n"
                                + "Fix for src/test/pkg/Hidden.kt line 10: Remove STOPSHIP:\n"
                                + "@@ -10 +10\n"
                                + "-     /* We must STOPSHIP! */\n"
                                + "+     /* We must ! */\n"
                                + "Fix for src/test/pkg/Hidden.kt line 14: Remove TODO:\n"
                                + "@@ -14 +14\n"
                                + "-         TODO()\n"
                                + "Fix for src/test/pkg/Hidden.kt line 18: Remove TODO:\n"
                                + "@@ -18 +18\n"
                                + "-         TODO(\"This is not yet implemented\")");
    }

    public void test2() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.io.File;\n"
                                        + "\n"
                                        + "import android.content.Intent;\n"
                                        + "import android.net.Uri;\n"
                                        + "\n"
                                        + "/**\n"
                                        + " * Ignore comments - create(\"/sdcard/foo\")\n"
                                        + " */\n"
                                        + "public class SdCardTest {\n"
                                        + "\tprivate static final boolean PROFILE_STARTUP = true;\n"
                                        + "\tprivate static final String SDCARD_TEST_HTML = \"/sdcard/test.html\";\n"
                                        + "\tpublic static final String SDCARD_ROOT = \"/sdcard\";\n"
                                        + "\tpublic static final String PACKAGES_PATH = \"/sdcard/o/packages/\";\n"
                                        + "\tFile deviceDir = new File(\"/sdcard/vr\");\n"
                                        + "\n"
                                        + "\tpublic SdCardTest() {\n"
                                        + "\t\tif (PROFILE_STARTUP) {\n"
                                        + "\t\t\tandroid.os.Debug.startMethodTracing(\"/sdcard/launcher\");\n"
                                        + "\t\t}\n"
                                        + "\t\tif (new File(\"/sdcard\").exists()) {\n"
                                        + "\t\t}\n"
                                        + "\t\tString FilePath = \"/sdcard/\" + new File(\"test\");\n"
                                        + "\t\tSystem.setProperty(\"foo.bar\", \"file://sdcard\");\n"
                                        + "\n"
                                        + "\n"
                                        + "\t\tIntent intent = new Intent(Intent.ACTION_PICK);\n"
                                        + "\t\tintent.setDataAndType(Uri.parse(\"file://sdcard/foo.json\"), \"application/bar-json\");\n"
                                        + "\t\tintent.putExtra(\"path-filter\", \"/sdcard(/.+)*\");\n"
                                        + "\t\tintent.putExtra(\"start-dir\", \"/sdcard\");\n"
                                        + "\t\tString mypath = \"/data/data/foo\";\n"
                                        + "\t\tString base = \"/data/data/foo.bar/test-profiling\";\n"
                                        + "\t\tString s = \"file://sdcard/foo\";\n"
                                        + "\t}\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testXml() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=207168
        // StopShip doesn't work in XML
        lint().files(
                        manifest(
                                ""
                                        + "<manifest>\n"
                                        + "    <!-- STOPSHIP fail in manifest -->\n"
                                        + "</manifest>"),
                        xml(
                                "res/layout/foo.xml",
                                ""
                                        + "<!-- STOPSHIP implement this first -->\n"
                                        + "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"0dip\"\n"
                                        + "    android:layout_height=\"0dip\"\n"
                                        + "    android:visibility=\"gone\" />"))
                .run()
                .expect(
                        ""
                                + "AndroidManifest.xml:2: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "    <!-- STOPSHIP fail in manifest -->\n"
                                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "res/layout/foo.xml:1: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "<!-- STOPSHIP implement this first -->\n"
                                + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings")
                .expectFixDiffs(
                        ""
                                + "Fix for AndroidManifest.xml line 2: Remove STOPSHIP:\n"
                                + "@@ -2 +2\n"
                                + "-     <!-- STOPSHIP fail in manifest -->\n"
                                + "+     <!-- fail in manifest -->\n"
                                + "Fix for res/layout/foo.xml line 1: Remove STOPSHIP:\n"
                                + "@@ -1 +1\n"
                                + "- <!-- STOPSHIP implement this first -->\n"
                                + "+ <!-- implement this first -->");
    }

    public void testNoStopShipInDebugVariant() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class Hidden {\n"
                                        + "    /* We must STOPSHIP! */\n"
                                        + "    String x;\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    buildTypes {\n"
                                        + "        debug {\n"
                                        + "        }\n"
                                        + "        release {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .variant("debug")
                .run()
                .expectClean();
    }

    public void testDoWarnAboutStopShipInDebugVariantWhileEditing() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class Hidden {\n"
                                        + "    /* We must STOPSHIP! */\n"
                                        + "    String x;\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    buildTypes {\n"
                                        + "        debug {\n"
                                        + "        }\n"
                                        + "        release {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .variant("debug")
                .incremental("Hidden.java")
                .run()
                .expect(
                        ""
                                + "src/main/java/test/pkg/Hidden.java:4: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "    /* We must STOPSHIP! */\n"
                                + "               ~~~~~~~~\n"
                                + "1 errors, 0 warnings\n");
    }

    public void testStopShipInReleaseVariant() {
        String expected =
                ""
                        + "src/main/java/test/pkg/Hidden.java:4: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                        + "    /* We must STOPSHIP! */\n"
                        + "               ~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class Hidden {\n"
                                        + "    /* We must STOPSHIP! */\n"
                                        + "    String x;\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    buildTypes {\n"
                                        + "        debug {\n"
                                        + "        }\n"
                                        + "        release {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .variant("release")
                .run()
                .expect(expected);
    }

    public void testGradle() {
        // Test for
        // 117485020: StopShip doesn't work in Gradle and AndroidManifest.xml
        lint().files(
                        gradle(
                                        ""
                                                + "dependencies {\n"
                                                + "    // STOPSHIP replace this with stable version\n"
                                                + "    implementation \"foo:bar:2.0-rc3\"\n"
                                                + "}")
                                .indented(),
                        kts(""
                                        + "plugins {\n"
                                        + "    // STOPSHIP do something here\n"
                                        + "  id(\"com.android.application\")\n"
                                        + "  id(\"kotlin-android\")\n"
                                        + "} // STOPSHIP")
                                .indented())
                .variant("release")
                .run()
                .expect(
                        ""
                                + "build.gradle:2: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "    // STOPSHIP replace this with stable version\n"
                                + "       ~~~~~~~~\n"
                                + "build.gradle.kts:2: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "    // STOPSHIP do something here\n"
                                + "       ~~~~~~~~\n"
                                + "build.gradle.kts:5: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                                + "} // STOPSHIP\n"
                                + "     ~~~~~~~~\n"
                                + "3 errors, 0 warnings")
                .expectFixDiffs(
                        ""
                                + "Fix for build.gradle line 2: Remove STOPSHIP:\n"
                                + "@@ -2 +2\n"
                                + "-     // STOPSHIP replace this with stable version\n"
                                + "+     //  replace this with stable version\n"
                                + "Fix for build.gradle.kts line 2: Remove STOPSHIP:\n"
                                + "@@ -2 +2\n"
                                + "-     // STOPSHIP do something here\n"
                                + "+     //  do something here\n"
                                + "Fix for build.gradle.kts line 5: Remove STOPSHIP:\n"
                                + "@@ -5 +5\n"
                                + "- } // STOPSHIP\n"
                                + "@@ -6 +5\n"
                                + "+ } // ");
    }
}
