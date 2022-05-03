/*
 * Copyright (C) 2022 The Android Open Source Project
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

class LocaleConfigDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return LocaleConfigDetector()
    }

    fun testDocumentationExample() {
        lint().files(
            manifest(
                """
                <manifest
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <application android:localeConfig="@xml/locale_config"/>
                </manifest>
                """
            ).indented(),
            xml(
                "res/xml/locale_config.xml",
                """
                <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
                    <locale android:name="en-us"/>
                    <locale android:name="nor-NOR"/>
                    <locale android:name="pt"/>
                </locale-config>
                """
            ).indented(),
            xml(
                "res/values-en/strings.xml",
                """
                <resources>
                    <string name="hello">Hello</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-ar/strings.xml",
                """
                <resources>
                    <string name="hello">أهلا</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-nb/strings.xml",
                """
                <resources>
                    <string name="hello">Hallo</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-b+es+419/strings.xml",
                """
                <resources>
                    <string name="hello">Hola</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-b+zh+Hans+SG/strings.xml",
                """
                <resources>
                    <string name="hello">你好</string>
                </resources>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: The language ar (Arabic) is present in this project, but not declared in the localeConfig resource [UnusedTranslation]
                <application android:localeConfig="@xml/locale_config"/>
                                                   ~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:4: Warning: The language es (Spanish) is present in this project, but not declared in the localeConfig resource [UnusedTranslation]
                <application android:localeConfig="@xml/locale_config"/>
                                                   ~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:4: Warning: The language nb (Norwegian Bokmål) is present in this project, but not declared in the localeConfig resource [UnusedTranslation]
                <application android:localeConfig="@xml/locale_config"/>
                                                   ~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:4: Warning: The language zh (Chinese) is present in this project, but not declared in the localeConfig resource [UnusedTranslation]
                <application android:localeConfig="@xml/locale_config"/>
                                                   ~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        ).verifyFixes().window(1).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 4: Add ar to locale_config.xml:
            res/xml/locale_config.xml:
            @@ -2 +2
              <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
            +     <locale android:name="ar"/>
                  <locale android:name="en-us"/>
            Fix for AndroidManifest.xml line 4: Add es to locale_config.xml:
            res/xml/locale_config.xml:
            @@ -3 +3
                  <locale android:name="en-us"/>
            +     <locale android:name="es"/>
                  <locale android:name="nor-NOR"/>
            Fix for AndroidManifest.xml line 4: Add nb to locale_config.xml:
            res/xml/locale_config.xml:
            @@ -3 +3
                  <locale android:name="en-us"/>
            +     <locale android:name="nb"/>
                  <locale android:name="nor-NOR"/>
            Fix for AndroidManifest.xml line 4: Add zh to locale_config.xml:
            res/xml/locale_config.xml:
            @@ -5 +5
                  <locale android:name="pt"/>
            +     <locale android:name="zh"/>
              </locale-config>
            """
        )
    }

    fun testCustomXmlnsPrefix() {
        // xmlns:a -- note how we have a: instead of android: -- this test makes sure the quickfix also uses this prefix
        lint().files(
            manifest(
                """
                <manifest
                    xmlns:a="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <application a:localeConfig="@xml/locale_config"/>
                </manifest>
                """
            ).indented(),
            xml(
                "res/xml/locale_config.xml",
                """
                <locale-config xmlns:a="http://schemas.android.com/apk/res/android">
                    <locale a:name="en-us"/>
                </locale-config>
                """
            ).indented(),
            xml(
                "res/values-en/strings.xml",
                """
                <resources>
                    <string name="hello">Hello</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values-nb/strings.xml",
                """
                <resources>
                    <string name="hello">Hallo</string>
                </resources>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: The language nb (Norwegian Bokmål) is present in this project, but not declared in the localeConfig resource [UnusedTranslation]
                <application a:localeConfig="@xml/locale_config"/>
                                             ~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        ).verifyFixes().window(1).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 4: Add nb to locale_config.xml:
            res/xml/locale_config.xml:
            @@ -3 +3
                  <locale a:name="en-us"/>
            +     <locale a:name="nb"/>
              </locale-config>
            """
        )
    }
}
