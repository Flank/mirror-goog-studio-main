/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.detector.api.Detector

class RemoteViewDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return RemoteViewDetector()
    }

    private val kotlinSample = kotlin(
        """
        package test.pkg
        import android.widget.RemoteViews

        fun test(packageName: String) {
            val remoteView = RemoteViews(packageName, R.layout.test)
        }
        """
    ).indented()

    fun testBasic() {
        lint()
            .files(
                kotlinSample,
                xml(
                    "res/layout/test.xml",
                    """
                    <merge>
                        <Button />
                        <AdapterViewFlipper />
                        <FrameLayout />
                        <GridLayout />
                        <GridView />
                        <LinearLayout />
                        <ListView />
                        <RelativeLayout />
                        <StackView />
                        <ViewFlipper />
                        <AnalogClock />
                        <Button />
                        <Chronometer />
                        <ImageButton />
                        <ImageView />
                        <ProgressBar />
                        <TextClock />
                        <TextView />
                        <DatePicker />
                        <CheckBox />
                        <Switch />
                        <RadioButton />
                        <RadioGroup />
                        <androidx.appcompat.widget.AppCompatTextView />
                    </merge>
                    """
                ).indented(),
                rClass("test.pkg", "@layout/test")
            ).run().expect(
                """
                src/test/pkg/test.kt:5: Error: @layout/test includes views not allowed in a RemoteView: CheckBox, DatePicker, RadioButton, RadioGroup, Switch, androidx.appcompat.widget.AppCompatTextView [RemoteViewLayout]
                    val remoteView = RemoteViews(packageName, R.layout.test)
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testLayoutFolder31() {
        // http://b/200165599 Update RemoteViewDetector for new @RemoteViews added in API 31
        lint().files(
            manifest().minSdk(31),
            kotlinSample,
            xml(
                "res/layout-v31/test.xml",
                """
                <merge>
                    <CheckBox />
                    <Switch />
                    <RadioButton />
                    <RadioGroup />
                </merge>
                """
            ).indented(),
            rClass("test.pkg", "@layout/test")
        ).run().expectClean()
    }

    fun testMin31() {
        lint().files(
            kotlinSample,
            xml(
                "res/layout-v31/test.xml",
                """
                <merge>
                    <CheckBox />
                    <Switch />
                    <RadioButton />
                    <RadioGroup />
                </merge>
                """
            ).indented(),
            rClass("test.pkg", "@layout/test")
        ).run().expectClean()
    }
}
