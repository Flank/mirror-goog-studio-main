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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.util.Scanner

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
                    variant.minSdk = 21
                    variant.maxSdk = 29
                    variant.targetSdk = 22
                })
            }

            android.packagingOptions.jniLibs.useLegacyPackaging = false
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
        assertManifestContentContainsString(manifestContent, "com.example.helloworld.TestReceiver")
        assertManifestContentContainsString(manifestContent, "com.example.helloworld.MainReceiver")
        assertManifestContentContainsString(manifestContent, "A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=21")
        assertManifestContentContainsString(manifestContent, "A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=22")
        assertManifestContentContainsString(manifestContent, "A: http://schemas.android.com/apk/res/android:maxSdkVersion(0x01010271)=29")
        assertManifestContentContainsString(
            manifestContent,
            "A: http://schemas.android.com/apk/res/android:extractNativeLibs(0x010104ea)=false"
        )
        assertManifestContentContainsString(
            manifestContent,
            "http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true"
        )

        // The manifest shouldn't contain android:debuggable if we set the testBuildType to release.
        project.buildFile.appendText("\n\nandroid.testBuildType \"release\"\n\n")
        project.executor().run("assembleReleaseAndroidTest")
        val releaseManifestContent =
            getManifestContent(project.getApk(GradleTestProject.ApkType.ANDROIDTEST_RELEASE).file)
        assertManifestContentDoesNotContainString(releaseManifestContent, "android:debuggable")
    }

    @Test
    fun testManifestOverlays() {
        project.buildFile.appendText("""
            android {
                flavorDimensions "app", "recents"

                productFlavors {
                    flavor1 {
                        dimension "app"
                    }

                    flavor2 {
                        dimension "recents"
                    }
                }
            }
        """.trimIndent())
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
        FileUtils.createFile(
            project.file("src/androidTestFlavor1/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld.test">
                    <application
                        android:allowBackup="true">
                    </application>
                </manifest>
            """.trimIndent()
        )
        FileUtils.createFile(
            project.file("src/androidTestFlavor2/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld.test">
                    <application
                        android:supportsRtl="true">
                    </application>
                </manifest>
            """.trimIndent()
        )
        FileUtils.createFile(
            project.file("src/androidTestDebug/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld.test">
                    <application
                        android:isGame="false">
                    </application>
                </manifest>
            """.trimIndent()
        )
        project.executor().run("assembleFlavor1Flavor2DebugAndroidTest")
        val manifestContent = project.file("build/intermediates/packaged_manifests/flavor1Flavor2DebugAndroidTest/AndroidManifest.xml")
        // merged from androidTestDebug
        assertThat(manifestContent).contains("android:isGame=\"false\"")
        // merged from androidTestFlavor2
        assertThat(manifestContent).contains("android:supportsRtl=\"true\"")
        // merged from androidTestFlavor1
        assertThat(manifestContent).contains("android:allowBackup=\"true\"")
    }

    @Test
    fun testNonUniquePackageNames() {
        project.buildFile.appendText("""
            android {
                namespace "allowedNonUnique"
            }
        """.trimIndent())
        FileUtils.createFile(
                project.file("src/androidTest/AndroidManifest.xml"),
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="allowedNonUnique.test">
                    <application
                        android:isGame="false">
                    </application>
                </manifest>
            """.trimIndent())
        val result = project.executor().run("processDebugAndroidTestManifest")
        val manifestContent = project.file("build/intermediates/packaged_manifests/debugAndroidTest/AndroidManifest.xml")
        result.stdout.use {
            ScannerSubject.assertThat(it).doesNotContain("Package name 'allowedNonUnique.test' used in:")
        }

    }

    private fun assertManifestContentContainsString(
        manifestContent: Iterable<String>,
        stringToAssert: String
    ) {
        manifestContent.forEach { if (it.trim().contains(stringToAssert)) return }
        fail("Cannot find $stringToAssert in ${manifestContent.joinToString(separator = "\n")}")
    }

    private fun assertManifestContentDoesNotContainString(
        manifestContent: Iterable<String>,
        stringToAssert: String
    ) {
        manifestContent.forEach {
            if (it.trim().contains(stringToAssert)) {
                fail("$stringToAssert found in ${manifestContent.joinToString(separator = "\n")}")
            }
        }
    }
}
