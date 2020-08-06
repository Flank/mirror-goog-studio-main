/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector

class ArraySizeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ArraySizeDetector()
    }

    fun testArraySizes() {
        lint().files(mArrays, mArrays2, mArrays3, mArrays4, mStrings).run().expect(
            """
            res/values/arrays.xml:3: Warning: Array security_questions has an inconsistent number of items (3 in values-nl-rNL/arrays.xml, 4 in values-cs/arrays.xml) [InconsistentArrays]
                <string-array name="security_questions">
                ^
                res/values-cs/arrays.xml:3: Declaration with array size (4)
                res/values-es/strings.xml:12: Declaration with array size (4)
                res/values-nl-rNL/arrays.xml:3: Declaration with array size (3)
            res/values/arrays.xml:10: Warning: Array signal_strength has an inconsistent number of items (5 in values/arrays.xml, 6 in values-land/arrays.xml) [InconsistentArrays]
                <array name="signal_strength">
                ^
                res/values-land/arrays.xml:2: Declaration with array size (6)
            0 errors, 2 warnings
            """
        )
    }

    fun testMultipleArrays() {
        lint().files(
            xml(
                "res/values-it/stringarrays.xml",
                """
                <resources>
                    <string-array name="track_type_desc">
                        <item>Pendenza</item>
                    </string-array>
                    <string-array name="map_density_desc">
                        <item>Automatico (mappa leggibile su display HD)</item>
                    </string-array>
                    <string-array name="cache_size_desc">
                        <item>Piccolo (100)</item>
                    </string-array>
                </resources>
                """
            ).indented(),
            xml(
                "res/values/stringarrays.xml",
                """
                <resources>
                    <string-array name="map_density_desc">
                        <item>Automatic (readable map on HD displays)</item>
                        <item>1 map pixel = 1 screen pixel</item>
                        <item>1 map pixel = 1.25 screen pixels</item>
                        <item>1 map pixel = 1.5 screen pixels</item>
                        <item>1 map pixel = 2 screen pixels</item>
                    </string-array>
                    <string-array name="spatial_resolution_desc">
                        <item>5m/yd (fine, default)</item>
                    </string-array>
                </resources>
                """
            ).indented()
        ).run().expect(
            """
            res/values/stringarrays.xml:2: Warning: Array map_density_desc has an inconsistent number of items (5 in values/stringarrays.xml, 1 in values-it/stringarrays.xml) [InconsistentArrays]
                <string-array name="map_density_desc">
                ^
                res/values-it/stringarrays.xml:5: Declaration with array size (1)
            0 errors, 1 warnings
            """
        )
    }

    fun testArraySizesSuppressed() {
        lint().files(
            xml(
                "res/values/arrays.xml",
                """
                <resources>
                    <!-- Choices for Locations in SetupWizard's Set Time and Data Activity -->
                    <string-array name="security_questions">
                        <item>Favorite food?</item>
                        <item>City of birth?</item>
                        <item>Best childhood friend\'s name?</item>
                        <item>Highschool name?</item>
                    </string-array>

                    <array name="signal_strength">
                        <item>@drawable/ic_setups_signal_0</item>
                        <item>@drawable/ic_setups_signal_1</item>
                        <item>@drawable/ic_setups_signal_2</item>
                        <item>@drawable/ic_setups_signal_3</item>
                        <item>@drawable/ic_setups_signal_4</item>
                    </array>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-land/arrays.xml",
                """
                <resources xmlns:tools="http://schemas.android.com/tools">
                    <array name="signal_strength" tools:ignore="InconsistentArrays">
                        <item>@drawable/ic_setups_signal_0</item>
                        <item>@drawable/ic_setups_signal_1</item>
                        <item>@drawable/ic_setups_signal_2</item>
                        <item>@drawable/ic_setups_signal_3</item>
                        <item>@drawable/ic_setups_signal_4</item>
                        <item>@drawable/extra</item>
                    </array>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testArraySizesIncremental() {
        lint().files(
            xml(
                "res/values/arrays.xml",
                """
                <resources>
                    <!-- Choices for Locations in SetupWizard's Set Time and Data Activity -->
                    <string-array name="security_questions">
                        <item>Favorite food?</item>
                        <item>City of birth?</item>
                        <item>Best childhood friend\'s name?</item>
                        <item>Highschool name?</item>
                    </string-array>

                    <array name="signal_strength">
                        <item>@drawable/ic_setups_signal_0</item>
                        <item>@drawable/ic_setups_signal_1</item>
                        <item>@drawable/ic_setups_signal_2</item>
                        <item>@drawable/ic_setups_signal_3</item>
                        <item>@drawable/ic_setups_signal_4</item>
                    </array>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-cs/arrays.xml",
                """
                <resources xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                  <string-array name="security_questions">
                    <item>"Oblíbené jídlo?"</item>
                    <item>"Město narození."</item>
                    <item>"Jméno nejlepšího kamaráda z dětství?"</item>
                    <item>"Název střední školy"</item>
                  </string-array>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-land/arrays.xml",
                """
                <resources>
                    <array name="signal_strength">
                        <item>@drawable/ic_setups_signal_0</item>
                        <item>@drawable/ic_setups_signal_1</item>
                        <item>@drawable/ic_setups_signal_2</item>
                        <item>@drawable/ic_setups_signal_3</item>
                        <item>@drawable/ic_setups_signal_4</item>
                        <item>@drawable/extra</item>
                    </array>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-nl-rNL/arrays.xml",
                """
                <resources xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                  <string-array name="security_questions">
                    <item>"Favoriete eten?"</item>
                    <item>"Geboorteplaats?"</item>
                    <item>"Naam van middelbare school?"</item>
                  </string-array>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-es/strings.xml",
                """

                <resources xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
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
            ).indented()
        )
            .incremental("res/values/arrays.xml")
            .run().expect(
                """
                res/values/arrays.xml:3: Warning: Array security_questions has an inconsistent number of items (4 in values/arrays.xml, 3 in values-nl-rNL/arrays.xml) [InconsistentArrays]
                    <string-array name="security_questions">
                    ^
                res/values/arrays.xml:10: Warning: Array signal_strength has an inconsistent number of items (5 in values/arrays.xml, 6 in values-land/arrays.xml) [InconsistentArrays]
                    <array name="signal_strength">
                    ^
                0 errors, 2 warnings
                """
            )
    }

    // Sample code
    private val mArrays = xml(
        "res/values/arrays.xml",
        """
        <resources>
            <!-- Choices for Locations in SetupWizard's Set Time and Data Activity -->
            <string-array name="security_questions">
                <item>Favorite food?</item>
                <item>City of birth?</item>
                <item>Best childhood friend\'s name?</item>
                <item>Highschool name?</item>
            </string-array>

            <array name="signal_strength">
                <item>@drawable/ic_setups_signal_0</item>
                <item>@drawable/ic_setups_signal_1</item>
                <item>@drawable/ic_setups_signal_2</item>
                <item>@drawable/ic_setups_signal_3</item>
                <item>@drawable/ic_setups_signal_4</item>
            </array>
        </resources>

        """
    ).indented()

    // Sample code
    private val mArrays2 = xml(
        "res/values-cs/arrays.xml",
        """
        <resources xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
          <string-array name="security_questions">
            <item>"Oblíbené jídlo?"</item>
            <item>"Město narození."</item>
            <item>"Jméno nejlepšího kamaráda z dětství?"</item>
            <item>"Název střední školy"</item>
          </string-array>
        </resources>
        """
    ).indented()

    // Sample code
    private val mArrays3 = xml(
        "res/values-land/arrays.xml",
        """
        <resources>
            <array name="signal_strength">
                <item>@drawable/ic_setups_signal_0</item>
                <item>@drawable/ic_setups_signal_1</item>
                <item>@drawable/ic_setups_signal_2</item>
                <item>@drawable/ic_setups_signal_3</item>
                <item>@drawable/ic_setups_signal_4</item>
                <item>@drawable/extra</item>
            </array>
        </resources>

        """
    ).indented()

    // Sample code
    private val mArrays4 = xml(
        "res/values-nl-rNL/arrays.xml",
        """
        <resources xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
          <string-array name="security_questions">
            <item>"Favoriete eten?"</item>
            <item>"Geboorteplaats?"</item>
            <item>"Naam van middelbare school?"</item>
          </string-array>
        </resources>
        """
    ).indented()

    // Sample code
    private val mStrings = xml(
        "res/values-es/strings.xml",
        """

        <resources xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
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
    ).indented()
}
