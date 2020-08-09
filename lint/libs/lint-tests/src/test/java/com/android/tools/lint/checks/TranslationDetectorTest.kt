/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.detector.api.Detector

class TranslationDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return TranslationDetector()
    }

    override fun includeParentPath(): Boolean {
        return true
    }

    fun testTranslation() {
        val expected =
            """
            res/values/strings.xml:20: Error: "show_all_apps" is not translated in "nl" (Dutch) [MissingTranslation]
                <string name="show_all_apps">All</string>
                        ~~~~~~~~~~~~~~~~~~~~
            res/values/strings.xml:23: Error: "menu_wallpaper" is not translated in "nl" (Dutch) [MissingTranslation]
                <string name="menu_wallpaper">Wallpaper</string>
                        ~~~~~~~~~~~~~~~~~~~~~
            res/values/strings.xml:25: Error: "menu_settings" is not translated in "cs" (Czech), "de" (German), "es" (Spanish), "nl" (Dutch) [MissingTranslation]
                <string name="menu_settings">Settings</string>
                        ~~~~~~~~~~~~~~~~~~~~
            res/values-cs/arrays.xml:3: Error: The array "security_questions" in values-cs has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [ExtraTranslation]
              <string-array name="security_questions">
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
            res/values-de-rDE/strings.xml:11: Error: "continue_skip_label" is translated here but not found in default locale [ExtraTranslation]
                <string name="continue_skip_label">"Weiter"</string>
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/values-es/strings.xml:12: Error: The array "security_questions" in values-es has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [ExtraTranslation]
              <string-array name="security_questions">
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
            6 errors, 0 warnings
            """
        lint().files(
            valuesStrings,
            valuesCsStrings,
            valuesDeDeStrings,
            valuesEsStrings,
            valuesEsUsStrings,
            valuesLandStrings,
            valuesCsArrays,
            doNotTranslateEsStrings,
            valuesNlNlStrings,
            xml(
                "res/values/public.xml",
                // Make sure we don't flag missing name here
                "<resources><public /></resources>"
            ),

            // Regression test for https://issuetracker.google.com/111819105
            xml("res/layout/foo.xml", "<LinearLayout/>"),
            xml("res/layout-ja/foo.xml", "<LinearLayout/>")
        ).run().expect(expected)
    }

    fun testExtraTranslationIncremental() {
        lint().files(
            valuesStrings,
            valuesCsStrings,
            valuesDeDeStrings,
            valuesEsStrings,
            valuesEsUsStrings,
            valuesLandStrings,
            valuesCsArrays,
            doNotTranslateEsStrings,
            valuesNlNlStrings
        )
            .incremental("res/values-cs/arrays.xml")
            .run()
            .expect(
                """
                res/values-cs/arrays.xml:3: Error: The array "security_questions" in values-cs has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [ExtraTranslation]
                  <string-array name="security_questions">
                                ~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testMissingTranslationIncremental() {
        lint().files(
            valuesStrings,
            valuesCsStrings,
            valuesDeDeStrings,
            valuesEsStrings,
            valuesEsUsStrings,
            valuesLandStrings,
            valuesCsArrays,
            doNotTranslateEsStrings,
            valuesNlNlStrings
        )
            .incremental("res/values/strings.xml")
            .run()
            .expect(
                """
                res/values/strings.xml:20: Error: "show_all_apps" is not translated in "nl" (Dutch) [MissingTranslation]
                    <string name="show_all_apps">All</string>
                            ~~~~~~~~~~~~~~~~~~~~
                res/values/strings.xml:23: Error: "menu_wallpaper" is not translated in "nl" (Dutch) [MissingTranslation]
                    <string name="menu_wallpaper">Wallpaper</string>
                            ~~~~~~~~~~~~~~~~~~~~~
                res/values/strings.xml:25: Error: "menu_settings" is not translated in "cs" (Czech), "de" (German), "es" (Spanish), "nl" (Dutch) [MissingTranslation]
                    <string name="menu_settings">Settings</string>
                            ~~~~~~~~~~~~~~~~~~~~
                3 errors, 0 warnings
                """
            )
    }

    fun testCaseHandlingInRepositories() {
        // Regression test for https://issuetracker.google.com/120747416
        lint().files(
            xml(
                "res/values/cases.xml",
                """
                <resources>
                    <string name="abc_abc.abc.abc_abc">ABC</string>
                </resources>
            """
            ).indented()
        )
            .incremental("res/values/cases.xml")
            .run()
            .expectClean()
    }

    private val valuesCsArrays = xml(
        "res/values-cs/arrays.xml",
        "" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "  <string-array name=\"security_questions\">\n" +
            "    <item>\"Oblíbené jídlo?\"</item>\n" +
            "    <item>\"M\u011bsto narození.\"</item>\n" +
            "    <item>\"Jméno nejlep\u0161ího kamaráda z d\u011btství?\"</item>\n" +
            "    <item>\"Název st\u0159ední \u0161koly\"</item>\n" +
            "  </string-array>\n" +
            "</resources>\n"
    )

    private val doNotTranslateEsStrings = xml(
        "res/values-es/donottranslate.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"full_wday_month_day_no_year\">EEEE, d MMMM</string>\n" +
            "</resources>\n"
    )

    private val valuesStrings = xml(
        "res/values/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!-- Copyright (C) 2007 The Android Open Source Project\n" +
            "\n" +
            "     Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
            "     you may not use this file except in compliance with the License.\n" +
            "     You may obtain a copy of the License at\n" +
            "\n" +
            "          http://www.apache.org/licenses/LICENSE-2.0\n" +
            "\n" +
            "     Unless required by applicable law or agreed to in writing, software\n" +
            "     distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            "     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            "     See the License for the specific language governing permissions and\n" +
            "     limitations under the License.\n" +
            "-->\n" +
            "\n" +
            "<resources>\n" +
            "    <!-- Home -->\n" +
            "    <string name=\"home_title\">Home Sample</string>\n" +
            "    <string name=\"show_all_apps\">All</string>\n" +
            "\n" +
            "    <!-- Home Menus -->\n" +
            "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
            "    <string name=\"menu_search\">Search</string>\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "    <string name=\"sample\" translatable=\"false\">Ignore Me</string>\n" +
            "\n" +
            "    <!-- Wallpaper -->\n" +
            "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n" +
            "</resources>\n" +
            "\n"
    )

    private val strings13 = xml(
        "res/values/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"home_title\">\"Casa\"</string>\n" +
            "    <string name=\"show_all_apps\">\"Todo\"</string>\n" +
            "    <string name=\"menu_wallpaper\">\"Papel tapiz\"</string>\n" +
            "    <string name=\"menu_search\">\"Búsqueda\"</string>\n" +
            "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
            "    <skip />\n" +
            "    <string name=\"wallpaper_instructions\">\"Puntee en la imagen para establecer papel tapiz vertical\"</string>\n" +
            "\n" +
            "  <string-array name=\"security_questions\">\n" +
            "    <item>\"Comida favorita\"</item>\n" +
            "    <item>\"Ciudad de nacimiento\"</item>\n" +
            "    <item>\"Nombre de tu mejor amigo/a de la infancia\"</item>\n" +
            "    <item>\"Nombre de tu colegio\"</item>\n" +
            "  </string-array>\n" +
            "</resources>\n"
    )

    private val strings14 = xml(
        "res/values/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"menu_search\">\"Búsqueda\"</string>\n" +
            "</resources>\n"
    )

    private val valuesCsStrings = xml(
        "res/values-cs/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"home_title\">\"Dom\u016f\"</string>\n" +
            "    <string name=\"show_all_apps\">\"V\u0161e\"</string>\n" +
            "    <string name=\"menu_wallpaper\">\"Tapeta\"</string>\n" +
            "    <string name=\"menu_search\">\"Hledat\"</string>\n" +
            "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
            "    <skip />\n" +
            "    <string name=\"wallpaper_instructions\">\"Klepnutím na obrázek nastavíte tapetu portrétu\"</string>\n" +
            "</resources>\n"
    )

    private val valuesStrings2 = xml(
        "res/values/strings2.xml",
        """

            <resources>
                <string name="hello">Hello</string>
            </resources>

            """
    ).indented()

    private val valuesNbStrings2 = xml(
        "res/values-nb/strings2.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources>\n" +
            "    <string name=\"hello\">Hello</string>\n" +
            "</resources>\n" +
            "\n"
    )

    private val valuesDeDeStrings = xml(
        "res/values-de-rDE/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"home_title\">\"Startseite\"</string>\n" +
            "    <string name=\"show_all_apps\">\"Alle\"</string>\n" +
            "    <string name=\"menu_wallpaper\">\"Bildschirmhintergrund\"</string>\n" +
            "    <string name=\"menu_search\">\"Suchen\"</string>\n" +
            "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
            "    <skip />\n" +
            "    <string name=\"wallpaper_instructions\">\"Tippen Sie auf Bild, um Porträt-Bildschirmhintergrund einzustellen\"</string>\n" +
            "    <string name=\"continue_skip_label\">\"Weiter\"</string>\n" +
            "</resources>\n"
    )

    private val valuesEnRgbStrings = xml(
        "res/values-en-rGB/strings.xml",
        "" +
            "<?xml version='1.0' encoding='UTF-8' standalone='no'?>\n" +
            "<resources>\n" +
            "\n" +
            "    <string name=\"dateFormat\">ukformat</string>\n" +
            "\n" +
            "</resources>\n"
    )

    private val valuesDeRdeStrings = xml(
        "res/values-de-rDE/strings.xml",
        "" +
            "<?xml version='1.0' encoding='UTF-8' standalone='no'?>\n" +
            "<resources>\n" +
            "\n" +
            "    <string name=\"dateFormat\">ukformat</string>\n" +
            "\n" +
            "</resources>\n"
    )

    private val valuesEsStrings = xml(
        "res/values-es/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"home_title\">\"Casa\"</string>\n" +
            "    <string name=\"show_all_apps\">\"Todo\"</string>\n" +
            "    <string name=\"menu_wallpaper\">\"Papel tapiz\"</string>\n" +
            "    <string name=\"menu_search\">\"Búsqueda\"</string>\n" +
            "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
            "    <skip />\n" +
            "    <string name=\"wallpaper_instructions\">\"Puntee en la imagen para establecer papel tapiz vertical\"</string>\n" +
            "\n" +
            "  <string-array name=\"security_questions\">\n" +
            "    <item>\"Comida favorita\"</item>\n" +
            "    <item>\"Ciudad de nacimiento\"</item>\n" +
            "    <item>\"Nombre de tu mejor amigo/a de la infancia\"</item>\n" +
            "    <item>\"Nombre de tu colegio\"</item>\n" +
            "  </string-array>\n" +
            "</resources>\n"
    )

    private val valuesEsUsStrings = xml(
        "res/values-es-rUS/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"menu_search\">\"Búsqueda\"</string>\n" +
            "</resources>\n"
    )

    private val valuesLandStrings = xml(
        "res/values-land/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!-- Copyright (C) 2007 The Android Open Source Project\n" +
            "\n" +
            "     Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
            "     you may not use this file except in compliance with the License.\n" +
            "     You may obtain a copy of the License at\n" +
            "\n" +
            "          http://www.apache.org/licenses/LICENSE-2.0\n" +
            "\n" +
            "     Unless required by applicable law or agreed to in writing, software\n" +
            "     distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            "     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            "     See the License for the specific language governing permissions and\n" +
            "     limitations under the License.\n" +
            "-->\n" +
            "\n" +
            "<resources>\n" +
            "    <!-- Wallpaper -->\n" +
            "    <string name=\"wallpaper_instructions\">Tap image to set landscape wallpaper</string>\n" +
            "</resources>\n" +
            "\n"
    )

    private val valuesNlNlStrings = xml(
        "res/values-nl-rNL/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"home_title\">\"Start\"</string>\n" +
            "    <!-- Commented out in the unit test to generate extra warnings:\n" +
            "    <string name=\"show_all_apps\">\"Alles\"</string>\n" +
            "    <string name=\"menu_wallpaper\">\"Achtergrond\"</string>\n" +
            "    -->\n" +
            "    <string name=\"menu_search\">\"Zoeken\"</string>\n" +
            "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
            "    <skip />\n" +
            "    <string name=\"wallpaper_instructions\">\"Tik op afbeelding om portretachtergrond in te stellen\"</string>\n" +
            "</resources>\n"
    )

    private val valuesTlhStrings = xml(
        "res/values-b+tlh/strings.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
            "    <string name=\"home_title\">\"Dom\u016f\"</string>\n" +
            "    <string name=\"show_all_apps\">\"V\u0161e\"</string>\n" +
            "    <string name=\"menu_wallpaper\">\"Tapeta\"</string>\n" +
            "    <string name=\"menu_search\">\"Hledat\"</string>\n" +
            "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
            "    <skip />\n" +
            "    <string name=\"wallpaper_instructions\">\"Klepnutím na obrázek nastavíte tapetu portrétu\"</string>\n" +
            "</resources>\n"
    )

    fun testBcp47() {
        val expected =
            """
            res/values/strings.xml:25: Error: "menu_settings" is not translated in "tlh" (Klingon; tlhIngan-Hol) [MissingTranslation]
                <string name="menu_settings">Settings</string>
                        ~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            valuesStrings,
            valuesTlhStrings
        ).run().expect(expected)
    }

    fun testHandleBom() {
        // This isn't really testing translation detection; it's just making sure that the
        // XML parser doesn't bomb on BOM bytes (byte order marker) at the beginning of
        // the XML document

        lint().files(
            xml(
                "res/values/strings.xml",
                (
                    "" +
                        "\ufeff<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
                        "    <string name=\"app_name\">Unit Test</string>\n" +
                        "</resources>\n"
                    )
            )
        ).run().expectClean()
    }

    fun testTranslatedArrays() {
        lint().files(
            xml(
                "res/values/translatedarrays.xml",
                (
                    "" +
                        "<resources>\n" +
                        "    <string name=\"item1\">Item1</string>\n" +
                        "    <string name=\"item2\">Item2</string>\n" +
                        "    <string-array name=\"myarray\">\n" +
                        "        <item>@string/item1</item>\n" +
                        "        <item>@string/item2</item>\n" +
                        "    </string-array>\n" +
                        "</resources>\n"
                    )
            ),
            xml(
                "res/values-cs/translatedarrays.xml",
                (
                    "" +
                        "<resources>\n" +
                        "    <string name=\"item1\">Item1-cs</string>\n" +
                        "    <string name=\"item2\">Item2-cs</string>\n" +
                        "</resources>\n"
                    )
            )
        ).run().expectClean()
    }

    fun testTranslationSuppresss() {
        lint().files(
            xml(
                "res/values/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                        "    <!-- Home -->\n" +
                        "    <string name=\"home_title\">Home Sample</string>\n" +
                        "    <string name=\"show_all_apps\" tools:ignore=\"MissingTranslation\">All</string>\n" +
                        "\n" +
                        "    <!-- Home Menus -->\n" +
                        "    <string name=\"menu_wallpaper\" tools:ignore=\"MissingTranslation\">Wallpaper</string>\n" +
                        "    <string name=\"menu_search\">Search</string>\n" +
                        "    <string name=\"menu_settings\" tools:ignore=\"all\">Settings</string>\n" +
                        "    <string name=\"sample\" translatable=\"false\">Ignore Me</string>\n" +
                        "\n" +
                        "    <!-- Wallpaper -->\n" +
                        "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n" +
                        "</resources>\n"
                    )
            ),
            xml(
                "res/values-es/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                        "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
                        "    <string name=\"home_title\">\"Casa\"</string>\n" +
                        "    <string name=\"show_all_apps\">\"Todo\"</string>\n" +
                        "    <string name=\"menu_wallpaper\">\"Papel tapiz\"</string>\n" +
                        "    <string name=\"menu_search\">\"Búsqueda\"</string>\n" +
                        "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
                        "    <skip />\n" +
                        "    <string name=\"wallpaper_instructions\">\"Puntee en la imagen para establecer papel tapiz vertical\"</string>\n" +
                        "    <string name=\"other\" tools:ignore=\"ExtraTranslation\">\"?\"</string>\n" +
                        "\n" +
                        "  <string-array name=\"security_questions\" tools:ignore=\"ExtraTranslation\">\n" +
                        "    <item>\"Comida favorita\"</item>\n" +
                        "    <item>\"Ciudad de nacimiento\"</item>\n" +
                        "    <item>\"Nombre de tu mejor amigo/a de la infancia\"</item>\n" +
                        "    <item>\"Nombre de tu colegio\"</item>\n" +
                        "  </string-array>\n" +
                        "</resources>\n"
                    )
            ),
            valuesNlNlStrings
        ).run().expectClean()
    }

    fun testMixedTranslationArrays() {
        // See issue http://code.google.com/p/android/issues/detail?id=29263

        lint().files(
            xml(
                "res/values/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<resources xmlns:tools=\"http://schemas.android.com/tools\" tools:ignore=\"MissingTranslation\">\n" +
                        "\n" +
                        "    <string name=\"test_string\">Test (English)</string>\n" +
                        "\n" +
                        "    <string-array name=\"test_string_array\">\n" +
                        "\t\t<item>@string/test_string</item>\n" +
                        "\t</string-array>\n" +
                        "\n" +
                        "</resources>\n"
                    )
            ),
            xml(
                "res/values-fr/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<resources>\n" +
                        "\n" +
                        "    <string-array name=\"test_string_array\">\n" +
                        "\t\t<item>Test (French)</item>\n" +
                        "\t</string-array>\n" +
                        "\n" +
                        "</resources>\n"
                    )
            )
        ).run().expectClean()
    }

    fun testLibraryProjects() {
        // If a library project provides additional locales, that should not force
        // the main project to include all those translations

        val library = project(
            // Library project
            xml(
                "res/values/strings.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<resources>\n" +
                    "    <!-- Home -->\n" +
                    "    <string name=\"home_title\">Home Sample</string>\n" +
                    "    <string name=\"show_all_apps\">All</string>\n" +
                    "\n" +
                    "    <!-- Home Menus -->\n" +
                    "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
                    "    <string name=\"menu_search\">Search</string>\n" +
                    "    <string name=\"menu_settings\">Settings</string>\n" +
                    "    <string name=\"sample\" translatable=\"false\">Ignore Me</string>\n" +
                    "\n" +
                    "    <!-- Wallpaper -->\n" +
                    "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n" +
                    "</resources>\n" +
                    "\n"
            ),
            xml(
                "res/values-cs/strings.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
                    "    <string name=\"home_title\">\"Dom\u016f\"</string>\n" +
                    "    <string name=\"show_all_apps\">\"V\u0161e\"</string>\n" +
                    "    <string name=\"menu_wallpaper\">\"Tapeta\"</string>\n" +
                    "    <string name=\"menu_search\">\"Hledat\"</string>\n" +
                    "    <!-- no translation found for menu_settings (1769059051084007158) -->\n" +
                    "    <skip />\n" +
                    "    <string name=\"wallpaper_instructions\">\"Klepnutím na obrázek nastavíte tapetu portrétu\"</string>\n" +
                    "</resources>\n"
            )

        ).type(ProjectDescription.Type.LIBRARY).name("LibraryProject").report(false)

        val main = project(
            xml(
                "res/values/strings2.xml",
                """
            <resources>
                <string name="hello">Hello</string>
            </resources>
            """
            ).indented()
        ).name("App").dependsOn(library)

        lint().projects(main, library).run().expectClean()
    }

    fun testMissingName() {
        val expected =
            """
            res/values/strings.xml:2: Error: Missing name attribute in <string> declaration [MissingTranslation]
                <string>Ignore Me</string>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        val fixes =
            """
            Fix for res/values/strings.xml line 1: Set name:
            @@ -4 +4
            -     <string>Ignore Me</string>
            +     <string name="[TODO]|">Ignore Me</string>
            """
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                    <resources>
                        <string>Ignore Me</string>
                    </resources>
                    """
            ).indented()
        ).run().expect(expected).expectFixDiffs(fixes)
    }

    fun testMissingNameIncremental() {
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <string name="" />
                </resources>
                """
            ).indented()
        ).incremental().run().expectClean()
    }

    fun testNonTranslatable1() {
        val expected =
            """
            res/values-nb/nontranslatable.xml:2: Warning: The resource string "sample" has been marked as translatable="false" elsewhere (usually in the values folder), but is translated to "nb" (Norwegian Bokmål) here [Untranslatable]
                <string name="sample">Ignore Me</string>
                        ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        val fixes =
            """
            Fix for res/values-nb/nontranslatable.xml line 1: Remove translation:
            @@ -2 +2
            -     <string name="sample">Ignore Me</string>
            """
        lint().files(
            xml(
                "res/values/nontranslatable.xml",
                """
                    <resources>
                        <string name="sample" translatable="false">Ignore Me</string>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-nb/nontranslatable.xml",
                """
                    <resources>
                        <string name="sample">Ignore Me</string>
                    </resources>

                    """
            ).indented()
        ).run().expect(expected).expectFixDiffs(fixes)
    }

    fun testNonTranslatable3() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=92861
        // Don't treat "google_maps_key" or "google_maps_key_instructions" as translatable
        lint().files(
            xml(
                "res/values/google_maps_api.xml",
                (
                    "" +
                        "<resources>\n" +
                        "    <string name=\"google_maps_key_instructions\"><!--\n" +
                        "    TODO: Before you run your application, you need a Google Maps API key.\n" +
                        "    Once you have your key, replace the \"google_maps_key\" string in this file.\n" +
                        "    --></string>\n" +
                        "\n" +
                        "    <string name=\"google_maps_key\">\n" +
                        "        YOUR_KEY_HERE\n" +
                        "    </string>\n" +
                        "</resources>\n"
                    )
            ),
            valuesStrings2,
            valuesNbStrings2
        ).run().expectClean()
    }

    fun testSpecifiedLanguageOk() {
        lint().files(
            strings13,
            valuesEsStrings,
            valuesEsUsStrings
        ).run().expectClean()
    }

    fun testSpecifiedLanguage() {
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                    <resources xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"
                        xmlns:tools="http://schemas.android.com/tools"
                        tools:locale="es">
                        <string name="home_title">"Casa"</string>
                        <string name="show_all_apps">"Todo"</string>
                        <string name="menu_wallpaper">"Papel tapiz"</string>
                        <string name="menu_search">"Búsqueda"</string>
                        <!-- no translation found for menu_settings (1769059051084007158) -->
                        <skip />
                        <string name="wallpaper_instructions">"Puntee en la imagen para establecer papel tapiz vertical"</string>

                        <string-array name="security_questions">
                            <item>"Comida favorita"</item>
                            <item>"Ciudad de nacimiento"</item>
                            <item>"Nombre de tu mejor amigo/a de la infancia"</item>
                            <item>"Nombre de tu colegio"</item>
                        </string-array>
                    </resources>
                    """
            ).indented(),
            valuesEsUsStrings
        ).run().expectClean()
    }

    fun testAnalytics() {
        // See http://code.google.com/p/android/issues/detail?id=43070

        lint().files(
            xml(
                "res/values/analytics.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<resources>\n" +
                        "  <!--Replace placeholder ID with your tracking ID-->\n" +
                        "  <string name=\"ga_trackingId\">UA-12345678-1</string>\n" +
                        "\n" +
                        "  <!--Enable Activity tracking-->\n" +
                        "  <bool name=\"ga_autoActivityTracking\">true</bool>\n" +
                        "\n" +
                        "  <!--Enable automatic exception tracking-->\n" +
                        "  <bool name=\"ga_reportUncaughtExceptions\">true</bool>\n" +
                        "\n" +
                        "  <!-- The screen names that will appear in your reporting -->\n" +
                        "  <string name=\"com.example.app.BaseActivity\">Home</string>\n" +
                        "  <string name=\"com.example.app.PrefsActivity\">Preferences</string>\n" +
                        "  <string name=\"test.pkg.OnClickActivity\">Clicks</string>\n" +
                        "\n" +
                        "  <string name=\"google_crash_reporting_api_key\" translatable=\"false\">AIzbSyCILMsOuUKwN3qhtxrPq7FFemDJUAXTyZ8</string>\n" +
                        "</resources>\n"
                    )
            ),
            doNotTranslateEsStrings // to make app multilingual
        ).run().expectClean()
    }

    fun testIssue33845() {
        // See http://code.google.com/p/android/issues/detail?id=33845
        val expected =
            """
            res/values/strings.xml:3: Error: "dateTimeFormat" is not translated in "de" (German) [MissingTranslation]
                <string name="dateTimeFormat">MM/dd/yyyy - HH:mm</string>
                        ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                        <resources xmlns:tools="http://schemas.android.com/tools" tools:locale="en">
                            <string name="dateFormat">MM/dd/yyyy</string>
                            <string name="dateTimeFormat">MM/dd/yyyy - HH:mm</string>
                        </resources>
                        """
            ).indented(),
            xml(
                "res/values-de/strings.xml",
                """
                    <resources>
                        <string name="dateFormat">dd.MM.yyyy</string>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-en-rGB/strings.xml",
                """
                    <resources>
                        <string name="dateFormat">dd/MM/yyyy</string>
                    </resources>
                    """
            ).indented()
        ).run().expect(expected)
    }

    fun testIssue33845b() {
        // Similar to issue 33845, but with some variations to the test data
        // See http://code.google.com/p/android/issues/detail?id=33845

        lint().files(
            xml(
                "res/values/styles.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                        "\n" +
                        "    <!-- DeleteThisFileToGetRidOfOtherWarning -->\n" +
                        "\n" +
                        "</resources>\n"
                    )
            ),
            xml(
                "res/values/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                        "<resources xmlns:tools=\"http://schemas.android.com/tools\" tools:locale=\"en\">\n" +
                        "\n" +
                        "    <string name=\"dateFormat\">defaultformat</string>\n" +
                        "\n" +
                        "</resources>\n"
                    )
            ),
            xml(
                "res/values-en-rGB/strings.xml",
                (
                    "" +
                        "<?xml version='1.0' encoding='UTF-8' standalone='no'?>\n" +
                        "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                        "\n" +
                        "    <string name=\"dateFormat\">ukformat</string>\n" +
                        "    <string name=\"sample\" tools:ignore=\"ExtraTranslation\">DeleteMeToGetRidOfOtherWarning</string>\n" +
                        "\n" +
                        "</resources>\n"
                    )
            )
        ).run().expectClean()
    }

    fun testEnglishRegionAndValuesAsEnglish1() {
        // tools:locale=en in base folder
        // Regression test for https://code.google.com/p/android/issues/detail?id=75879

        lint().files(
            xml(
                "res/values/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                        "<resources xmlns:tools=\"http://schemas.android.com/tools\" tools:locale=\"en\">\n" +
                        "\n" +
                        "    <string name=\"dateFormat\">defaultformat</string>\n" +
                        "    <string name=\"other\">other</string>\n" +
                        "\n" +
                        "</resources>\n"
                    )
            ),
            valuesEnRgbStrings
        ).run().expectClean()
    }

    fun testEnglishRegionAndValuesAsEnglish2() {
        // No tools:locale specified in the base folder: *assume* English
        // Regression test for https://code.google.com/p/android/issues/detail?id=75879

        val expected =
            """
            res/values/strings.xml:3: Error: "other" is not translated in "de" (German) or "en" (English) [MissingTranslation]
                <string name="other">other</string>
                        ~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                        <resources>
                            <string name="dateFormat">defaultformat</string>
                            <string name="other">other</string>
                        </resources>
                        """
            ).indented(),
            // Flagged because it's not the default locale:
            valuesDeRdeStrings,
            // Not flagged because it's the implicit default locale
            valuesEnRgbStrings
        ).run().expect(expected)
    }

    fun testEnglishRegionAndValuesAsEnglish3() {
        // tools:locale=de in base folder
        // Regression test for https://code.google.com/p/android/issues/detail?id=75879

        lint().files(
            xml(
                "res/values/strings.xml",
                (
                    "" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                        "<resources xmlns:tools=\"http://schemas.android.com/tools\" tools:locale=\"de\">\n" +
                        "\n" +
                        "    <string name=\"dateFormat\">defaultformat</string>\n" +
                        "    <string name=\"other\">other</string>\n" +
                        "\n" +
                        "</resources>\n"
                    )
            ),
            valuesDeRdeStrings
        ).run().expectClean()
    }

    fun testIgnoreStyles() {
        lint().files(
            xml(
                "res/values-v21/themes_base.xml",
                """
                <resources xmlns:tools="http://schemas.android.com/tools" tools:locale="de">
                    <style name="Base.V21.Theme.AppCompat" parent="Base.V7.Theme.AppCompat">
                    </style>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testResConfigs() {
        val expected =
            """
            src/main/res/values/strings.xml:25: Error: "menu_settings" is not translated in "cs" (Czech) or "de" (German) [MissingTranslation]
                <string name="menu_settings">Settings</string>
                        ~~~~~~~~~~~~~~~~~~~~
            src/main/res/values-cs/arrays.xml:3: Error: The array "security_questions" in values-cs has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [ExtraTranslation]
              <string-array name="security_questions">
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/main/res/values-de-rDE/strings.xml:11: Error: "continue_skip_label" is translated here but not found in default locale [ExtraTranslation]
                <string name="continue_skip_label">"Weiter"</string>
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/main/res/values-es/strings.xml:12: Error: The array "security_questions" in values-es has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [ExtraTranslation]
              <string-array name="security_questions">
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """

        lint().files(
            valuesStrings.toSrcMain(),
            valuesCsStrings.toSrcMain(),
            valuesDeDeStrings.toSrcMain(),
            valuesEsStrings.toSrcMain(),
            valuesEsUsStrings.toSrcMain(),
            valuesLandStrings.toSrcMain(),
            valuesCsArrays.toSrcMain(),
            doNotTranslateEsStrings.toSrcMain(),
            valuesNlNlStrings.toSrcMain(),
            gradle(
                """
                    apply plugin: 'com.android.application'

                    android {
                        defaultConfig {
                            resConfigs "cs"
                        }
                        flavorDimensions  "pricing", "releaseType"
                        productFlavors {
                            beta {
                                dimension "releaseType"
                                resConfig "en", "de"
                                resConfigs "nodpi", "hdpi"
                            }
                            normal { dimension "releaseType" }
                            free { dimension "pricing" }
                            paid { dimension "pricing" }
                        }
                    }"""
            )
        ).run().expect(expected)
    }

    fun TestFile.toSrcMain(): TestFile = xml("src/main/" + targetRelativePath, contents)

    fun testResConfigsIncremental() {
        val expected =
            """
            src/main/res/values/strings.xml:25: Error: "menu_settings" is not translated in "cs" (Czech) or "de" (German) [MissingTranslation]
                <string name="menu_settings">Settings</string>
                        ~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """

        lint().files(
            valuesStrings.toSrcMain(),
            valuesCsStrings.toSrcMain(),
            valuesDeDeStrings.toSrcMain(),
            valuesEsStrings.toSrcMain(),
            valuesEsUsStrings.toSrcMain(),
            valuesLandStrings.toSrcMain(),
            valuesCsArrays.toSrcMain(),
            doNotTranslateEsStrings.toSrcMain(),
            valuesNlNlStrings.toSrcMain(),
            gradle(
                """
                    apply plugin: 'com.android.application'

                    android {
                        defaultConfig {
                            resConfigs "cs"
                        }
                        flavorDimensions  "pricing", "releaseType"
                        productFlavors {
                            beta {
                                dimension "releaseType"
                                resConfig "en", "de"
                                resConfigs "nodpi", "hdpi"
                            }
                            normal { dimension "releaseType" }
                            free { dimension "pricing" }
                            paid { dimension "pricing" }
                        }
                    }"""
            )
        ).incremental("src/main/res/values/strings.xml").run().expect(expected)
    }

    fun testMissingBaseCompletely() {
        val expected =
            """
            res/values-cs/strings.xml:4: Error: "home_title" is translated here but not found in default locale [ExtraTranslation]
                <string name="home_title">"Domů"</string>
                        ~~~~~~~~~~~~~~~~~
            res/values-cs/strings.xml:5: Error: "show_all_apps" is translated here but not found in default locale [ExtraTranslation]
                <string name="show_all_apps">"Vše"</string>
                        ~~~~~~~~~~~~~~~~~~~~
            res/values-cs/strings.xml:6: Error: "menu_wallpaper" is translated here but not found in default locale [ExtraTranslation]
                <string name="menu_wallpaper">"Tapeta"</string>
                        ~~~~~~~~~~~~~~~~~~~~~
            res/values-cs/strings.xml:7: Error: "menu_search" is translated here but not found in default locale [ExtraTranslation]
                <string name="menu_search">"Hledat"</string>
                        ~~~~~~~~~~~~~~~~~~
            res/values-cs/strings.xml:10: Error: "wallpaper_instructions" is translated here but not found in default locale [ExtraTranslation]
                <string name="wallpaper_instructions">"Klepnutím na obrázek nastavíte tapetu portrétu"</string>
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            5 errors, 0 warnings
            """
        lint().files(valuesCsStrings).run().expect(expected)
    }

    fun testMissingSomeBaseStrings() {
        val expected =
            """
            res/values-es/strings.xml:4: Error: "home_title" is translated here but not found in default locale [ExtraTranslation]
                <string name="home_title">"Casa"</string>
                        ~~~~~~~~~~~~~~~~~
            res/values-es/strings.xml:5: Error: "show_all_apps" is translated here but not found in default locale [ExtraTranslation]
                <string name="show_all_apps">"Todo"</string>
                        ~~~~~~~~~~~~~~~~~~~~
            res/values-es/strings.xml:6: Error: "menu_wallpaper" is translated here but not found in default locale [ExtraTranslation]
                <string name="menu_wallpaper">"Papel tapiz"</string>
                        ~~~~~~~~~~~~~~~~~~~~~
            res/values-es/strings.xml:10: Error: "wallpaper_instructions" is translated here but not found in default locale [ExtraTranslation]
                <string name="wallpaper_instructions">"Puntee en la imagen para establecer papel tapiz vertical"</string>
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/values-es/strings.xml:12: Error: The array "security_questions" in values-es has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [ExtraTranslation]
              <string-array name="security_questions">
                            ~~~~~~~~~~~~~~~~~~~~~~~~~
            5 errors, 0 warnings
            """
        lint().files(
            strings14,
            valuesEsStrings
        ).run().expect(expected)
    }

    fun testSuspiciousLocale() {
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <resources xmlns:android="http://schemas.android.com/apk/res/android">
                    <string name="name">Name</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en/strings.xml",
                """
                <resources
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools" tools:locale="en-US">
                    <string name="name">Name (en)</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-nb/strings.xml",
                """
                <resources
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools" tools:locale="en">
                    <string name="name">Name (nb)</string>
                </resources>
                """
            ).indented()
        ).run().expect(
            """
                res/values-nb/strings.xml:3: Error: Suspicious tools:locale declaration of language en; the parent folder values-nb implies language nb [MissingTranslation]
                    xmlns:tools="http://schemas.android.com/tools" tools:locale="en">
                                                                                 ~~
                1 errors, 0 warnings
                """
        )
    }

    fun testConfigKeys() {
        // Some developer services create config files merged with your project, but in some
        // versions they were missing a translatable="false" entry. Since we know these aren't
        // keys you normally want to translate, let's filter them for users.
        lint().files(
            xml(
                "res/values/config.xml",
                """
                        <resources>
                            <string name="gcm_defaultSenderId">SENDER_ID</string>
                            <string name="google_app_id">App Id</string>
                            <string name="ga_trackingID">Analytics</string>
                        </resources>
                        """
            ).indented(),
            xml(
                "res/values/strings.xml",
                """
                        <resources>
                            <string name="app_name">My Application</string>
                        </resources>
                        """
            ).indented(),
            xml(
                "res/values-nb/strings.xml",
                """
                        <resources>
                            <string name="app_name">Min Applikasjon</string>
                        </resources>
                        """
            ).indented()
        ).run().expectClean()
    }

    fun testTranslatableAttributeWarning() {
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                    <resources>
                        <string name="name">base</string>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-en/strings.xml",
                """
                    <resources>
                        <string name="name" translatable="false">base</string>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-nb/strings.xml",
                """
                <resources xmlns:tools="http://schemas.android.com/tools">
                    <string name="name" translatable="false" tools:ignore="Untranslatable">base</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-sv/strings.xml",
                """
                    <resources xmlns:tools="http://schemas.android.com/tools">
                        <string name="name" translatable="false" tools:ignore="ExtraTranslation">base</string>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values/something.xml",
                """
                    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                        <!-- Version dependent font string, in v21 we added sans-serif-medium. [DO NOT TRANSLATE] -->
                        <string name="games_font_roboto_medium">sans-serif</string>
                    </resources>"""
            ).indented(),
            xml(
                "res/values-af/something.xml",
                """
                    <resources>
                        <string name="name">base</string>
                        <string name="games_font_roboto_medium" msgid="2018081468373942067">sans-serif-medium</string>
                    </resources>"""
            ).indented(),
            xml(
                "res/values-v21/something.xml",
                """
                    <resources>
                        <!-- Version specific font string for v21 and up. [DO NOT TRANSLATE] -->
                        <string name="games_font_roboto_medium" translatable="false">sans-serif-medium</string>
                    </resources>"""
            ).indented()
        ).run().expect(
            """
            res/values-en/strings.xml:2: Warning: The resource string "name" is marked as translatable="false", but is translated to "en" (English) here [Untranslatable]
                <string name="name" translatable="false">base</string>
                                    ~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testExtraResourcesOfOtherTypes() {
        // TODO: Make sure that with -vNN we don't complain if minSdkVersion >= NN
        lint().files(
            xml(
                "res/values/dimen.xml",
                """
                    <resources>
                        <dimen name="ok_dimen">base</dimen> <!-- ok -->
                        <style name="ok_style"></style> <!-- ok -->
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-land/dimen.xml",
                """
                    <resources>
                        <dimen name="ok_dimen">1pt</dimen> <!-- ok -->
                        <style name="ok_style"></style> <!-- ok -->
                        <dimen name="extra_dimen1">1pt</dimen> <!-- error -->
                        <style name="extra_style1"></style> <!-- ok -->
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-v21/dimen.xml",
                """
                    <resources>
                        <!-- We allow common scenario of having dedicated
                             resources for API levels, often used for theming -->
                        <dimen name="ok_extra_dimen">1pt</dimen> <!-- ok -->
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-land-v21/dimen.xml",
                """
                    <resources>
                        <dimen name="ok_extra_dimen">2pt</dimen> <!-- ok -->
                        <dimen name="extra_dimen2">1pt</dimen> <!-- error -->
                    </resources>
                    """
            ).indented(),

            xml(
                "res/drawable-mdpi/ok_drawable.xml",
                """
                    <resources>
                        <drawable name="color_drawable">#ffffffff</drawable>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/color/ok_color.xml",
                """<color xmlns:android="http://schemas.android.com/apk/res/android" android:color="#ff0000" />"""
            ).indented(),
            xml(
                "res/color-port/extra_color.xml",
                """<color xmlns:android="http://schemas.android.com/apk/res/android" android:color="#ff0000" />"""
            ).indented(),
            xml("res/layout-land/extra_layout.xml", """<merge/>""").indented()
        ).run().expect(
            """
            res/values-land-v21/dimen.xml:3: Error: The dimen "extra_dimen2" in values-land-v21 has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [MissingDefaultResource]
                <dimen name="extra_dimen2">1pt</dimen> <!-- error -->
                       ~~~~~~~~~~~~~~~~~~~
            res/values-land/dimen.xml:4: Error: The dimen "extra_dimen1" in values-land has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [MissingDefaultResource]
                <dimen name="extra_dimen1">1pt</dimen> <!-- error -->
                       ~~~~~~~~~~~~~~~~~~~
            res/color-port/extra_color.xml:1: Error: The color "extra_color" in color-port has no declaration in the base color folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [MissingDefaultResource]
            <color xmlns:android="http://schemas.android.com/apk/res/android" android:color="#ff0000" />
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout-land/extra_layout.xml:1: Error: The layout "extra_layout" in layout-land has no declaration in the base layout folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [MissingDefaultResource]
            <merge/>
            ~~~~~~~~
            4 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
                Fix for res/values-land-v21/dimen.xml line 2: Remove resource override:
                @@ -3 +3
                -     <dimen name="extra_dimen2">1pt</dimen> <!-- error -->
                Fix for res/values-land/dimen.xml line 3: Remove resource override:
                @@ -4 +4
                -     <dimen name="extra_dimen1">1pt</dimen> <!-- error -->
                """
        )
    }

    fun testNamespaces() {
        // Regression test for issue 74227702: Make sure we correctly handle
        // namespaced resources
        lint().files(
            xml("src/main/res/layout/activity_main.xml", """<merge/>""").indented(),
            gradle(
                """
                    buildscript {
                        dependencies {
                            classpath 'com.android.tools.build:gradle:3.2.0-alpha4'
                        }
                    }
                    android.aaptOptions.namespaced true
                    """
            ).indented()
        ).run().expectClean()
    }

    fun testExtraResourcesOfOtherTypesIncremental() {
        lint().files(
            xml(
                "res/values/dimen.xml",
                """
                    <resources>
                        <dimen name="ok_dimen">base</dimen> <!-- ok -->
                        <style name="ok_style"></style> <!-- ok -->
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-land/dimen.xml",
                """
                    <resources>
                        <dimen name="ok_dimen">1pt</dimen> <!-- ok -->
                        <style name="ok_style"></style> <!-- ok -->
                        <dimen name="extra_dimen1">1pt</dimen> <!-- error -->
                        <style name="extra_style1"></style> <!-- ok -->
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-v21/dimen.xml",
                """
                    <resources>
                        <!-- We allow common scenario of having dedicated
                             resources for API levels, often used for theming -->
                        <dimen name="ok_extra_dimen">1pt</dimen> <!-- ok -->
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values-land-v21/dimen.xml",
                """
                    <resources>
                        <dimen name="ok_extra_dimen">2pt</dimen> <!-- ok -->
                        <dimen name="extra_dimen2">1pt</dimen> <!-- error -->
                    </resources>
                    """
            ).indented()
        ).incremental("res/values-land/dimen.xml").run().expect(
            """
            res/values-land/dimen.xml:4: Error: The dimen "extra_dimen1" in values-land has no declaration in the base values folder; this can lead to crashes when the resource is queried in a configuration that does not match this qualifier [MissingDefaultResource]
                <dimen name="extra_dimen1">1pt</dimen> <!-- error -->
                       ~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testExtraResourcesOfOtherTypesIncremental2() {
        lint().files(
            xml(
                "res/values-v21/dimen.xml",
                """
                    <resources>
                        <!-- We allow common scenario of having dedicated
                             resources for API levels, often used for theming -->
                        <dimen name="ok_extra_dimen">1pt</dimen> <!-- ok -->
                    </resources>
                    """
            ).indented()
        ).incremental("res/values-v21/dimen.xml").run().expectClean()
    }

    fun testBitmaps() {
        // Regression test for https://issuetracker.google.com/37095605
        lint().files(
            image("res/drawable-mdpi/ok1.png", 48, 48),
            image("res/drawable-hdpi/ok1.png", 48, 48),
            image("res/drawable-en-hdpi/ok1.png", 48, 48).fill(0xff0000),
            image("res/drawable-da-xxxhdpi/error1.png", 48, 48).fill(0xff0000)
        ).run().expect(
            """
            res/drawable-da-xxxhdpi/error1.png: Error: The drawable "error1" in drawable-da-xxxhdpi has no declaration in the base drawable folder or in a drawable-densitydpi folder; this can lead to crashes when the drawable is queried in a configuration that does not match this qualifier [MissingDefaultResource]
            1 errors, 0 warnings
            """
        )
    }

    fun testSuppress() {
        // Regression test for
        // 80346518: tools:ignore="MissingTranslation" doesn't work for <resource element>

        lint().files(
            xml(
                "res/values/strings.xml",
                """
                    <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">
                      <string name="foo">Foo</string>
                    </resources>
                    """
            ),
            xml(
                "res/values-nb/strings.xml",
                """
                    <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="ExtraTranslation">
                      <string name="bar">Bar</string>
                    </resources>
                    """
            )
        ).run().expectClean()
    }

    fun test112410856() {
        // Regression test for
        // 112410856: Lint thinks text IDs with same id as layout id's need translation.

        lint().files(
            xml(
                "res/values/strings.xml",
                """
                    <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">
                      <string name="foo">Foo</string>
                      <string name="bar">Bar</string>
                    </resources>
                    """
            ),
            xml(
                "res/layout/foo.xml",
                """
                    <View/>
                    """
            ),
            xml(
                "res/values-nb/strings.xml",
                """
                    <resources>
                      <string name="bar">Bar</string>
                    </resources>
                    """
            )
        ).run().expectClean()
    }

    fun testIncrementalDefaultLocale() {
        // Regression test for https://issuetracker.google.com/142590628
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <resources xmlns:tools="http://schemas.android.com/tools" tools:locale="en">
                    <string name="app_name">My Application</string>
                    <string name="test">This is a test</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en-rGB/strings.xml",
                """
                <resources>
                    <string name="app_name">My Application</string>
                </resources>
                """
            ).indented()
        ).incremental("res/values/strings.xml").run().expectClean()
    }

    fun testNonTranslatableFile() {
        // Regression test for https://issuetracker.google.com/118332958 :
        // Allow translatable=false on the root element
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <string name="app_name">My Application</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en/strings.xml",
                """
                <resources>
                    <string name="app_name">My Application</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values/misc.xml",
                """
                <resources translatable="false">
                    <string name="misc">Misc</string>
                    <string name="test">Test</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-en-rUS/strings.xml",
                """
                <resources translatable="false">
                    <string name="app_name">My Application</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-v11/strings.xml",
                """
                <resources translatable="false">
                    <string name="app_name">My Application</string>
                </resources>
                """
            ).indented()
        ).run().expect(
            """
            res/values-en-rUS/strings.xml:1: Warning: This resource folder is marked as non-translatable yet is in a translated resource folder ("en" (English)) [Untranslatable]
            <resources translatable="false">
                       ~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
