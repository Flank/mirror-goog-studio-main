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

import com.android.tools.lint.detector.api.Detector

class StartDestinationDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = StartDestinationDetector()

    fun testValuesOk() {
        lint().files(
            xml(
                "res/navigation/navigation.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android"
                             app:startDestination="@id/foo">
                               <fragment android:id="@+id/foo"/>
                           </navigation>"""
            ).indented()
        ).run().expectClean()
    }

    fun testIncludeOk() {
        lint().files(
            xml(
                "res/navigation/navigation.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android"
                             app:startDestination="@id/includedId">
                               <include app:graph="@navigation/foo"/>
                           </navigation>"""
            ).indented(),
            xml(
                "res/navigation/foo.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android"
                             android:id='@+id/includedId'
                             app:startDestination="@id/foo2">
                               <fragment android:id="@+id/foo2"/>
                           </navigation>"""
            ).indented()
        ).incremental("res/navigation/navigation.xml").run().expectClean()
    }

    fun testIncludeInvalid() {
        lint().files(
            xml(
                "res/navigation/navigation.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android"
                             app:startDestination="@id/includedId">
                               <include app:graph="@navigation/foo"/>
                           </navigation>"""
            ).indented(),
            xml(
                "res/navigation/foo.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android"
                             android:id='@+id/includedId2'
                             app:startDestination="@id/foo2">
                               <fragment android:id="@+id/foo2"/>
                           </navigation>"""
            ).indented()
        ).incremental("res/navigation/navigation.xml").run().expect(
            "" +
                "res/navigation/navigation.xml:5: Warning: Invalid start destination @id/includedId [InvalidNavigation]\n" +
                "                             app:startDestination=\"@id/includedId\">\n" +
                "                                                   ~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings"
        )
    }

    fun testNoChildren() {
        lint().files(
            xml(
                "res/navigation/navigation.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android">
                           </navigation>"""
            ).indented()
        ).run().expectClean()
    }

    fun testStartDestinationAbsent() {
        lint().files(
            xml(
                "res/navigation/navigation.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android">
                               <fragment android:id="@+id/foo"/>
                           </navigation>"""
            ).indented()
        ).run().expect(
            "" +
                "res/navigation/navigation.xml:2: Warning: No start destination specified [InvalidNavigation]\n" +
                "                           <navigation\n" +
                "                            ~~~~~~~~~~\n" +
                "0 errors, 1 warnings"
        )
    }

    fun testStartDestinationInvalid() {
        lint().files(
            xml(
                "res/navigation/navigation.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                           <navigation
                             xmlns:app="http://schemas.android.com/apk/res-auto"
                             xmlns:android="http://schemas.android.com/apk/res/android"
                             app:startDestination="@id/bar">
                               <fragment android:id="@+id/foo"/>
                           </navigation>"""
            ).indented()
        ).run().expect(
            "" +
                "res/navigation/navigation.xml:5: Warning: Invalid start destination @id/bar [InvalidNavigation]\n" +
                "                             app:startDestination=\"@id/bar\">\n" +
                "                                                   ~~~~~~~\n" +
                "0 errors, 1 warnings"
        )
    }
}
