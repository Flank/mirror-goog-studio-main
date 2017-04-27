/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.tools.lint.checks.LocaleFolderDetector.suggestBcp47Correction;

import com.android.tools.lint.detector.api.Detector;
import org.intellij.lang.annotations.Language;

@SuppressWarnings("javadoc")
public class LocaleFolderDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new LocaleFolderDetector();
    }

    public void testDeprecated() throws Exception {
        String expected = ""
                + "res/values-he: Warning: The locale folder \"he\" should be called \"iw\" instead; see the java.util.Locale documentation [LocaleFolder]\n"
                + "res/values-id: Warning: The locale folder \"id\" should be called \"in\" instead; see the java.util.Locale documentation [LocaleFolder]\n"
                + "res/values-yi: Warning: The locale folder \"yi\" should be called \"ji\" instead; see the java.util.Locale documentation [LocaleFolder]\n"
                + "0 errors, 3 warnings\n";
        lint().files(
                xml("res/values/strings.xml", stringsXml),
                xml("res/values-no/strings.xml", stringsXml),
                xml("res/values-he/strings.xml", stringsXml),
                xml("res/values-id/strings.xml", stringsXml),
                xml("res/values-yi/strings.xml", stringsXml))
                .run()
                .expect(expected);
    }

    public void testSuspiciousRegion() throws Exception {
        String expected = ""
                + "res/values-ff-rNO: Warning: Suspicious language and region combination ff (Fulah) with NO (Norway): language ff is usually paired with: SN (Senegal), CM (Cameroon), GN (Guinea), MR (Mauritania) [WrongRegion]\n"
                + "res/values-nb-rSE: Warning: Suspicious language and region combination nb (Norwegian Bokmål) with SE (Sweden): language nb is usually paired with: NO (Norway), SJ (Svalbard & Jan Mayen) [WrongRegion]\n"
                + "res/values-sv-rSV: Warning: Suspicious language and region combination sv (Swedish) with SV (El Salvador): language sv is usually paired with: SE (Sweden), AX (Åland Islands), FI (Finland) [WrongRegion]\n"
                + "0 errors, 3 warnings\n";
        lint().files(
                xml("res/values/strings.xml", stringsXml),
                xml("res/values-no/strings.xml", stringsXml),
                xml("res/values-nb-rNO/strings.xml", stringsXml),
                xml("res/values-nb-rSJ/strings.xml", stringsXml),
                xml("res/values-nb-rSE/strings.xml", stringsXml),
                xml("res/values-sv-rSV/strings.xml", stringsXml),
                xml("res/values-en-rXA/strings.xml", stringsXml),
                xml("res/values-ff-rNO/strings.xml", stringsXml))
                .run()
                .expect(expected);
    }

    public void testAlpha3() throws Exception {
        String expected = ""
                + "res/values-b+nor+NOR: Warning: For compatibility, should use 2-letter language codes when available; use no instead of nor [UseAlpha2]\n"
                + "res/values-b+nor+NOR: Warning: For compatibility, should use 2-letter region codes when available; use NO instead of nor [UseAlpha2]\n"
                + "0 errors, 2 warnings\n";
        lint().files(
                xml("res/values/strings.xml", stringsXml),
                xml("res/values-no/strings.xml", stringsXml),
                xml("res/values-b+kok+IN//strings.xml", stringsXml), // OK
                xml("res/values-b+nor+NOR/strings.xml", stringsXml)) // Not OK
                .run()
                .expect(expected);

    }

    public void testInvalidFolder() throws Exception {
        String expected = ""
                + "res/values-ldtrl-mnc123: Error: Invalid resource folder: make sure qualifiers appear in the correct order, are spelled correctly, etc. [InvalidResourceFolder]\n"
                + "res/values-no-rNOR: Error: Invalid resource folder; did you mean b+no+NO ? [InvalidResourceFolder]\n"
                + "2 errors, 0 warnings\n";
        lint().files(
                xml("res/values/strings.xml", stringsXml),
                xml("res/values-ldtrl-mnc123/strings.xml", stringsXml),
                xml("res/values-kok-rIN//strings.xml", stringsXml),
                xml("res/values-no-rNOR/strings.xml", stringsXml))
                .run()
                .expect(expected);
    }

    public void testConflictingScripts() throws Exception {
        String expected = ""
                + "res/values-b+en+Scr1: Error: Multiple locale folders for language en map to a single folder in versions < API 21: values-b+en+Scr2, values-b+en+Scr1 [InvalidResourceFolder]\n"
                + "    res/values-b+en+Scr2: <No location-specific message\n"
                + "1 errors, 0 warnings\n";
        lint().files(
                xml("res/values/strings.xml", stringsXml),
                xml("res/values-b+en+Scr1/strings.xml", stringsXml),
                xml("res/values-b+en+Scr2/strings.xml", stringsXml),
                xml("res/values-b+en+Scr3-v21/strings.xml", stringsXml),
                xml("res/values-b+fr+Scr1-v21/strings.xml", stringsXml),
                xml("res/values-b+fr+Scr2-v21/strings.xml", stringsXml),
                xml("res/values-b+no+Scr1/strings.xml", stringsXml),
                xml("res/values-b+no+Scr2-v21/strings.xml", stringsXml),
                xml("res/values-b+se+Scr1/strings.xml", stringsXml),
                xml("res/values-b+de+Scr1+DE/strings.xml", stringsXml),
                xml("res/values-b+de+Scr2+AT/strings.xml", stringsXml))
                .run()
                .expect(expected);
    }

    public void testUsing3LetterCodesWithoutGetLocales() throws Exception {
        // b/34520084

        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(18),
                xml("res/values-no/strings.xml", stringsXml),
                xml("res/values-fil/strings.xml", stringsXml),
                xml("res/values-b+kok+IN//strings.xml", stringsXml))
                .run()

                // No warnings when there's no call to getLocales anywhere
                .expectClean();
    }

    public void testCrashApi19FromSource() throws Exception {
        // b/34520084

        String expected = ""
                + "src/test/pkg/myapplication/MyLibrary.java:9: Error: The app will crash on platforms older than v21 (minSdkVersion is 18) because AssetManager#getLocales is called and it contains one or more v21-style (3-letter or BCP47 locale) folders: values-b+kok+IN, values-fil [GetLocales]\n"
                + "        String[] locales = assets.getLocales();\n"
                + "                           ~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(18),
                // Explicit call to getLocales
                java(""
                        + "package test.pkg.myapplication;\n"
                        + "\n"
                        + "import android.content.res.AssetManager;\n"
                        + "import android.content.res.Resources;\n"
                        + "\n"
                        + "public class MyLibrary {\n"
                        + "    public static void doSomething(Resources resources) {\n"
                        + "        AssetManager assets = resources.getAssets();\n"
                        + "        String[] locales = assets.getLocales();\n"
                        + "    }\n"
                        + "}\n"),
                xml("res/values-no/strings.xml", stringsXml),
                xml("res/values-fil/strings.xml", stringsXml),
                xml("res/values-b+kok+IN//strings.xml", stringsXml))
                .run()
                .expect(expected);
    }

    public void testCrashApi19FromLibrary() throws Exception {
        // b/34520084
        String expected = ""
                + "res/values-b+kok+IN: Error: The app will crash on platforms older than v21 (minSdkVersion is 18) because AssetManager#getLocales() is called (from the library jar file libs/build-compat.jar) and this folder resource name only works on v21 or later with that call present in the app [GetLocales]\n"
                + "res/values-fil: Error: The app will crash on platforms older than v21 (minSdkVersion is 18) because AssetManager#getLocales() is called (from the library jar file libs/build-compat.jar) and this folder resource name only works on v21 or later with that call present in the app [GetLocales]\n"
                + "2 errors, 0 warnings\n";

        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(18),
                jar("libs/build-compat.jar",
                        base64gzip("android/support/v4/os/BuildCompat.class", ""
                                + "H4sIAAAAAAAAAIVSy07CQBQ9g0ihouID3woaF+DCWbjUmBATV1UTMW5cDWVS"
                                + "R8uUTAcTPsuNJi78AD/KeKegbox0cZ/n3HvupB+fb+8AjrDnYxpVHytY9bGG"
                                + "dQ8bHjYZCidKK3vKMNVo3jLkz5KuZJgPlJaXg15HmhvRiamyGCShiG+FUS4f"
                                + "F/P2XqUMe4GVqeX9x4j3hqLfj1UorEo0vxgGqmOEGR4zzHSTdtKTxNARURqB"
                                + "0F2TqC4PE22lttzIlF/LNBmYUKbHTkzJfKcMtQl4Bi92Ch126S54EE+Cx0JH"
                                + "vG0NbaS+386g58opn/uRduigZRTgedgqYxs7Hmpl1LHLUJ90F0Pld9NV50GG"
                                + "lmH7X6V0ViRtK02lpXi/0fzzsKx/IbSIpCHt9UkYOo+mBt8vUG00/3gD7CJP"
                                + "f4H7cmDuZLJFyjh5Rn764BXsOWuXyBZGRfhky+N4hiJQdxZzY3KLfM7VDl6Q"
                                + "e8FU8DvAzxoewYrZkJUREPOokC8ScQGL2e6ljLP8BSDzZ1KvAgAA")),
                xml("res/values-no/strings.xml", stringsXml),
                xml("res/values-fil/strings.xml", stringsXml),
                xml("res/values-b+kok+IN//strings.xml", stringsXml))
                .run()
                .expect(expected);
    }

    // TODO: Test v21? Test suppress?

    public void testBcpReplacement() {
        assertEquals("b+no+NO", suggestBcp47Correction("values-nor-rNO"));
        assertEquals("b+no+NO", suggestBcp47Correction("values-nor-rNOR"));
        assertEquals("b+es+419", suggestBcp47Correction("values-es-419"));
        assertNull(suggestBcp47Correction("values-car"));
        assertNull(suggestBcp47Correction("values-b+foo+bar"));
    }

    @SuppressWarnings("all") // Sample code
    @Language("XML")
    private static final String stringsXml = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<!-- Copyright (C) 2007 The Android Open Source Project\n"
            + "\n"
            + "     Licensed under the Apache License, Version 2.0 (the \"License\");\n"
            + "     you may not use this file except in compliance with the License.\n"
            + "     You may obtain a copy of the License at\n"
            + "\n"
            + "          http://www.apache.org/licenses/LICENSE-2.0\n"
            + "\n"
            + "     Unless required by applicable law or agreed to in writing, software\n"
            + "     distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + "     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + "     See the License for the specific language governing permissions and\n"
            + "     limitations under the License.\n"
            + "-->\n"
            + "\n"
            + "<resources>\n"
            + "    <!-- Home -->\n"
            + "    <string name=\"home_title\">Home Sample</string>\n"
            + "    <string name=\"show_all_apps\">All</string>\n"
            + "\n"
            + "    <!-- Home Menus -->\n"
            + "    <string name=\"menu_wallpaper\">Wallpaper</string>\n"
            + "    <string name=\"menu_search\">Search</string>\n"
            + "    <string name=\"menu_settings\">Settings</string>\n"
            + "    <string name=\"dummy\" translatable=\"false\">Ignore Me</string>\n"
            + "\n"
            + "    <!-- Wallpaper -->\n"
            + "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n"
            + "</resources>\n"
            + "\n";
}