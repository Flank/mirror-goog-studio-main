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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class DosLineEndingDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DosLineEndingDetector()
    }

    fun test() {
        //noinspection all // Sample code
        lint().files(
            base64gzip(
                "res/layout/crcrlf.xml",
                "" +
                    "PFJlbGF0aXZlTGF5b3V0IHhtbG5zOmFuZHJvaWQ9Imh0dHA6Ly9zY2hlbWFz" +
                    "LmFuZHJvaWQuY29tL2Fway9yZXMvYW5kcm9pZCINCiAgICB4bWxuczp0b29s" +
                    "cz0iaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vdG9vbHMiDQogICAgYW5k" +
                    "cm9pZDpsYXlvdXRfd2lkdGg9Im1hdGNoX3BhcmVudCINCiAgICBhbmRyb2lk" +
                    "OmxheW91dF9oZWlnaHQ9Im1hdGNoX3BhcmVudCIgPg0KDQ0KICAgIDxUZXh0" +
                    "Vmlldw0KICAgICAgICBhbmRyb2lkOmxheW91dF93aWR0aD0id3JhcF9jb250" +
                    "ZW50Ig0KICAgICAgICBhbmRyb2lkOmxheW91dF9oZWlnaHQ9IndyYXBfY29u" +
                    "dGVudCINCiAgICAgICAgYW5kcm9pZDpsYXlvdXRfY2VudGVySG9yaXpvbnRh" +
                    "bD0idHJ1ZSINCiAgICAgICAgYW5kcm9pZDpsYXlvdXRfY2VudGVyVmVydGlj" +
                    "YWw9InRydWUiDQogICAgICAgIGFuZHJvaWQ6dGV4dD0iSGVsbG8iDQogICAg" +
                    "ICAgIHRvb2xzOmNvbnRleHQ9Ii5NYWluQWN0aXZpdHkiIC8+DQoNCjwvUmVs" +
                    "YXRpdmVMYXlvdXQ+DQo="
            )
        ).run().expect(
            """
            res/layout/crcrlf.xml:4: Error: Incorrect line ending: found carriage return (\r) without corresponding newline (\n) [MangledCRLF]
                android:layout_height="match_parent" >
            ^
            1 errors, 0 warnings
            """
        )
    }

    fun testIgnore() {
        lint().files(
            base64gzip(
                "res/layout/crcrlf_ignore.xml",
                "" +
                    "PFJlbGF0aXZlTGF5b3V0IHhtbG5zOmFuZHJvaWQ9Imh0dHA6Ly9zY2hlbWFz" +
                    "LmFuZHJvaWQuY29tL2Fway9yZXMvYW5kcm9pZCINCiAgICB4bWxuczp0b29s" +
                    "cz0iaHR0cDovL3NjaGVtYXMuYW5kcm9pZC5jb20vdG9vbHMiDQogICAgYW5k" +
                    "cm9pZDpsYXlvdXRfd2lkdGg9Im1hdGNoX3BhcmVudCINCiAgICBhbmRyb2lk" +
                    "OmxheW91dF9oZWlnaHQ9Im1hdGNoX3BhcmVudCINCiAgICB0b29sczppZ25v" +
                    "cmU9Ik1hbmdsZWRDUkxGIiA+DQoNDQogICAgPFRleHRWaWV3DQogICAgICAg" +
                    "IGFuZHJvaWQ6bGF5b3V0X3dpZHRoPSJ3cmFwX2NvbnRlbnQiDQogICAgICAg" +
                    "IGFuZHJvaWQ6bGF5b3V0X2hlaWdodD0id3JhcF9jb250ZW50Ig0KICAgICAg" +
                    "ICBhbmRyb2lkOmxheW91dF9jZW50ZXJIb3Jpem9udGFsPSJ0cnVlIg0KICAg" +
                    "ICAgICBhbmRyb2lkOmxheW91dF9jZW50ZXJWZXJ0aWNhbD0idHJ1ZSINCiAg" +
                    "ICAgICAgYW5kcm9pZDp0ZXh0PSJAc3RyaW5nL2FwcF9uYW1lIg0KICAgICAg" +
                    "ICB0b29sczpjb250ZXh0PSIuTWFpbkFjdGl2aXR5IiAvPg0KDQogICAgDQog" +
                    "ICAgDQogICAgDQogICAgDQo8L1JlbGF0aXZlTGF5b3V0Pg0K"
            )
        ).run().expectClean()
    }

    fun testNegative() {
        // Make sure we don't get warnings for a correct file
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """

                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >

                    <include
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        layout="@layout/layout2" />

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }
}
