/*
 * Copyright (C) 2013 The Android Open Source Project
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

class WrongCaseDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WrongCaseDetector()
    }

    fun testBasic() {
        //noinspection all // Sample code
        lint().files(
            xml(
                "res/layout/case.xml",
                """
                <Merge xmlns:android="http://schemas.android.com/apk/res/android" >

                    <Fragment android:name="foo.bar.Fragment" />
                    <Include layout="@layout/foo" />
                    <RequestFocus />

                </Merge>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/case.xml:1: Error: Invalid tag <Merge>; should be <merge> [WrongCase]
            <Merge xmlns:android="http://schemas.android.com/apk/res/android" >
             ~~~~~
            res/layout/case.xml:3: Error: Invalid tag <Fragment>; should be <fragment> [WrongCase]
                <Fragment android:name="foo.bar.Fragment" />
                 ~~~~~~~~
            res/layout/case.xml:4: Error: Invalid tag <Include>; should be <include> [WrongCase]
                <Include layout="@layout/foo" />
                 ~~~~~~~
            res/layout/case.xml:5: Error: Invalid tag <RequestFocus>; should be <requestFocus> [WrongCase]
                <RequestFocus />
                 ~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }
}
