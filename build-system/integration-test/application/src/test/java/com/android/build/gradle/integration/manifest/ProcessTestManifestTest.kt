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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ApkSubject.getManifestContent
import com.android.utils.FileUtils
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class ProcessTestManifestTest {
    @JvmField @Rule
    var project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
        .create()

    @Test
    fun build() {
        project.buildFile.appendText("""
            import com.android.build.api.variant.AndroidVersion

            androidComponents {
                beforeVariants(selector().all(), { variant ->
                    variant.minSdkVersion = new AndroidVersion(21, null)
                    variant.maxSdkVersion = 29
                    variant.targetSdkVersion = new AndroidVersion(22, null)
                })
            }
        """.trimIndent())
        FileUtils.createFile(
            project.file("src/androidTest/java/com/example/helloworld/TestReceiver.java"),
            """
                package com.example.helloworld;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                    }
                }
            """.trimIndent()
        )

        FileUtils.createFile(
            project.file("src/main/java/com/example/helloworld/MainReceiver.java"),
            """
                package com.example.helloworld;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class MainReceiver extends BroadcastReceiver {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                    }
                }
            """.trimIndent()
        )

        FileUtils.createFile(
            project.file("src/androidTest/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld.test">
                    <application>
                        <receiver android:name="com.example.helloworld.TestReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )

        // Replace android manifest with the one containing a receiver reference
        project.file("src/main/AndroidManifest.xml").delete()
        FileUtils.createFile(
            project.file("src/main/AndroidManifest.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="com.example.helloworld"
                            android:versionCode="1"
                            android:versionName="1.0">

                    <application android:label="@string/app_name">
                        <activity android:name=".HelloWorld"
                                  android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>

                        <receiver android:name="com.example.helloworld.MainReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )

        project.executor().run("assembleDebugAndroidTest")

        val manifestContent = getManifestContent(project.testApk.file)
        assertManifestContent(manifestContent, "com.example.helloworld.TestReceiver")
        assertManifestContent(manifestContent, "com.example.helloworld.MainReceiver")
        assertManifestContent(manifestContent, "A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=21")
        assertManifestContent(manifestContent, "A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=22")
        assertManifestContent(manifestContent, "A: http://schemas.android.com/apk/res/android:maxSdkVersion(0x01010271)=29")
    }

    fun assertManifestContent(manifestContent: Iterable<String>, stringToAssert: String) {
        manifestContent.forEach { if (it.trim().contains(stringToAssert)) return }
        fail("Cannot find $stringToAssert in ${manifestContent.joinToString(separator = "\n")}")
    }
}
