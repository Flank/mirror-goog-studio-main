/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.manifest

import com.android.ide.common.blame.SourceFile
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ManifestHelperTest {

    @Test
    fun testFindingOriginalManifestFilePosition() {
        val filePath = "/usr/src/MyApplication/app/src/main/AndroidManifest.xml"
        val mergedManifestFile =
            File("/usr/src/MyApplication/app/build/intermediates/merged_manifests/debug/AndroidManifest.xml")
        verifyWithGivenPaths(filePath, mergedManifestFile)
    }


    @Test
    fun testFindingOriginalManifestFilePositionWithSpace() {
        val filePath = "/usr/src/[path] with space/My Application/app/src/main/AndroidManifest.xml"
        val mergedManifestFile =
            File("/usr/src/[path] with space/My Application/app/build/intermediates/merged_manifests/debug/AndroidManifest.xml")
        verifyWithGivenPaths(filePath, mergedManifestFile)
    }

    private fun verifyWithGivenPaths(filePath: String, mergedManifestFile: File) {
        val outputList = """
            1<?xml version="1.0" encoding="utf-8"?>
            2<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            3    package="com.example.myapplication"
            4    android:versionCode="1"
            5    android:versionName="1.0" >
            6
            7    <uses-sdk
            8        android:minSdkVersion="15"
            8-->$filePath
            9        android:targetSdkVersion="28" />
            9-->$filePath
            10
            11    <application
            11-->$filePath:5:5-19:19
            12        android:allowBackup="true"
            12-->$filePath:6:9-6:35
            13        android:appComponentFactory="androidx.core.app.CoreComponentFactory"
            13-->[androidx.core:core:1.0.1] /usr/.gradle/caches/transforms-3/cb5e0295e6631df8cf1172ae152a4ad4/transformed/core-1.0.1/AndroidManifest.xml:22:18-22:86
            14        android:debuggable="true"
            15        android:icon="@mipmap/ic_launcher"
            15-->$filePath:7:9-7:43
            16        android:label="@string/app_name1"
            16-->$filePath:8:9-8:42
            17        android:roundIcon="@mipmap/ic_launcher_round"
            17-->$filePath:9:9-9:54
            18        android:supportsRtl="true"
            18-->$filePath:10:9-11
            19        android:theme="@style/AppTheme" >
            19-->$filePath:11:9-11:40
            20        <activity android:name="com.example.myapplication.MainActivity" >
            20-->$filePath:12:9-18:20
            20-->$filePath:12:19-12:47
            21            <intent-filter>
            21-->$filePath:13:13-17:29
            22                <action android:name="android.intent.action.MAIN" />
            22-->$filePath:14:17
            22-->$filePath:14:25-14:66
            23
            24                <category android:name="android.intent.category.LAUNCHER" />
            24-->$filePath:16
            24-->$filePath:16:27-16:74
            25            </intent-filter>
            26        </activity>
            27        android:appComponentFactory="androidx.core.app.CoreComponentFactory"
            27-->[androidx.core:core:1.0.1] /usr/[path] with space/.gradle/caches/transforms-3/cb5e0295e6631df8cf1172ae152a4ad4/transformed/core-1.0.1/AndroidManifest.xml:22:18-22:86
            28    </application>
            29
            30</manifest>
        """.trimIndent().split("\n")
        checkSourcePosition(9, outputList, filePath, SourcePosition.UNKNOWN, mergedManifestFile)

        checkSourcePosition(
            11,
            outputList,
            filePath,
            SourcePosition(4, 4, -1, 18, 18, -1),
            mergedManifestFile
        )

        checkSourcePosition(
            18,
            outputList,
            filePath,
            SourcePosition(9, 8, -1, 9, 10, -1),
            mergedManifestFile
        )

        checkSourcePosition(
            22,
            outputList,
            filePath,
            SourcePosition(13, 16, -1, -1, -1, -1),
            mergedManifestFile
        )

        checkSourcePosition(
            24,
            outputList,
            filePath,
            SourcePosition(15, -1, -1, -1, -1, -1),
            mergedManifestFile
        )

        checkSourcePosition(
            13,
            outputList,
            "/usr/.gradle/caches/transforms-3/cb5e0295e6631df8cf1172ae152a4ad4/transformed/core-1.0.1/AndroidManifest.xml",
            SourcePosition(21, 17, -1, 21, 85, -1),
            mergedManifestFile
        )

        checkSourcePosition(
            27,
            outputList,
            "/usr/[path] with space/.gradle/caches/transforms-3/cb5e0295e6631df8cf1172ae152a4ad4/transformed/core-1.0.1/AndroidManifest.xml",
            SourcePosition(21, 17, -1, 21, 85, -1),
            mergedManifestFile
        )

        var oldPos = SourceFilePosition(SourceFile(mergedManifestFile), SourcePosition(27, -1, -1))

        assertThat(findOriginalManifestFilePosition(outputList, oldPos)).isEqualTo(oldPos)

        oldPos =
            SourceFilePosition(
                SourceFile(File("/usr/src/MyApplication/app/src/res/layout/layout.xml")),
                SourcePosition(23, -1, -1)
            )

        assertThat(findOriginalManifestFilePosition(outputList, oldPos)).isEqualTo(oldPos)
    }

    private fun checkSourcePosition(
        lineNumber: Int,
        outputList: List<String>,
        filePath: String,
        expectedSourcePosition: SourcePosition,
        originalFile: File
    ) {
        val oldPos =
            SourceFilePosition(SourceFile(originalFile), SourcePosition(lineNumber - 1, -1, -1))
        val newPos = findOriginalManifestFilePosition(outputList, oldPos)
        assertThat(newPos).isEqualTo(
            SourceFilePosition(
                File(filePath),
                expectedSourcePosition
            )
        )
    }
}
