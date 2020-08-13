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

package com.android.tools.lint.client.api

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.SdCardDetector
import com.android.tools.lint.detector.api.Detector

class ConfigurationHierarchyTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SdCardDetector()
    }

    fun testLinXmlInSourceFolders() {
        // Checks that lint.xml can be applied in different folders in a hierarchical way
        lint().files(
            kotlin(
                """
                package test.pkg1.subpkg1
                class MyTest {
                    val s: String = "/sdcard/mydir" // OK: suppressed via test/lint.xml
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg2.subpkg1
                class MyTest {
                    val s: String = "/sdcard/mydir" // Error: severity set via test/pkg2/lint.xml
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg2.subpkg2
                class MyTest {
                    val s: String = "/sdcard/mydir" // Warning: severity set via test/pkg2/subpkg2
                }
                """
            ).indented(),
            xml(
                "src/main/kotlin/test/lint.xml",
                """
                <lint>
                    <issue id="SdCardPath" severity="ignore" />
                </lint>
                """
            ).indented(),
            xml(
                "src/main/kotlin/test/pkg2/lint.xml",
                """
                <lint>
                    <issue id="SdCardPath" severity="error" />
                </lint>
                """
            ).indented(),
            xml(
                "src/main/kotlin/test/pkg2/subpkg2/lint.xml",
                """
                <lint>
                    <issue id="SdCardPath" severity="warning" />
                </lint>
                """
            ).indented(), // Trigger src/main/java source sets
            gradle("")
        ).issues(SdCardDetector.ISSUE).run().expect(
            """
            src/main/kotlin/test/pkg2/subpkg1/MyTest.kt:3: Error: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
                val s: String = "/sdcard/mydir" // Error: severity set via test/pkg2/lint.xml
                                 ~~~~~~~~~~~~~
            src/main/kotlin/test/pkg2/subpkg2/MyTest.kt:3: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
                val s: String = "/sdcard/mydir" // Warning: severity set via test/pkg2/subpkg2
                                 ~~~~~~~~~~~~~
            1 errors, 1 warnings
            """
        )
    }

    /** Manifest with a number of problems */
    private val manifest = manifest(
        """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                 package="com.example.helloworld"
                 android:versionCode="@dimen/versionCode"
                 android:versionName="@dimen/versionName">
                <uses-feature android:name="android.hardware.camera"/>
                <uses-feature android:name="android.hardware.camera"/>
               <application android:label="@string/app_name">
               </application>
               <uses-sdk android:targetSdkVersion="24" />
               <uses-sdk android:minSdkVersion="15" />
               <uses-library android:name="android.test.runner" android:required="false" />
            </manifest>
            """
    ).indented()

    /** The set of issues to be looked for in [manifest] */
    private val manifestIssues = arrayOf(
        ManifestDetector.ORDER,
        ManifestDetector.DUPLICATE_USES_FEATURE,
        ManifestDetector.ILLEGAL_REFERENCE,
        ManifestDetector.MULTIPLE_USES_SDK,
        ManifestDetector.WRONG_PARENT
    )

    fun testLintXmlSeverityInheritance() {
        // Tests the inheritance of severity among libraries and app modules and shared dirs
        val indirectLib = project(
            manifest,
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- overrides inherited -->
                    <issue id="ManifestOrder" severity="error" />
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.library'")
        ).name("indirectLib")

        val lib = project(
            manifest,
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- This will turn it off in lib and in indirect lib. It will not affect app. -->
                    <!-- BUG: Not working in indirectlib -->
                    <issue id="WrongManifestParent" severity="ignore" />
                    <!-- overrides inherited -->
                    <issue id="ManifestOrder" severity="error" />
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.library'")
        ).dependsOn(indirectLib).name("lib")

        val main = project(
            manifest,
            xml(
                "../lint.xml",
                """
                <lint>
                    <!-- This is in a parent of all; will work everywhere -->
                    <issue id="DuplicateUsesFeature" severity="ignore" />
                    <issue id="ManifestOrder" severity="ignore" />
                    <issue id="IllegalResourceRef" severity="ignore"/>
                </lint>
            """
            ).indented(),
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- This will turn it off both in app and in lib -->
                    <issue id="MultipleUsesSdk" severity="ignore" />
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.application'")
        ).dependsOn(lib).name("app")

        lint().issues(*manifestIssues).projects(main).run().expect(
            """
            app/src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library android:name="android.test.runner" android:required="false" />
                ~~~~~~~~~~~~
            indirectLib/src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library android:name="android.test.runner" android:required="false" />
                ~~~~~~~~~~~~
            indirectLib/src/main/AndroidManifest.xml:9: Error: <uses-sdk> tag appears after <application> tag [ManifestOrder]
               <uses-sdk android:targetSdkVersion="24" />
                ~~~~~~~~
            lib/src/main/AndroidManifest.xml:9: Error: <uses-sdk> tag appears after <application> tag [ManifestOrder]
               <uses-sdk android:targetSdkVersion="24" />
                ~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    fun testLintXmlSuppressPathInheritance() {
        // Checks the inheritance semantics of suppress paths in lint.xml files.
        // This has a different implementation in LintXmlConfiguration than severity,
        // so this is checked separately.

        val indirectLib = project(
            manifest,
            xml(
                "lint.xml",
                """
                <lint>
                    <issue id="ManifestOrder">
                        <ignore path="src/main/AndroidManifest.xml" />
                    </issue>
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.library'")
        ).name("indirectLib")

        val lib = project(
            manifest,
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- This will turn it off in lib and in indirect lib. It will not affect app. -->
                    <issue id="WrongManifestParent">
                        <ignore path="src/main/AndroidManifest.xml" />
                    </issue>
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.library'")
        ).dependsOn(indirectLib).name("lib")

        val main = project(
            manifest,
            xml(
                "../lint.xml",
                """
                <lint>
                    <!-- This is in a parent of all; will work everywhere -->
                    <issue id="DuplicateUsesFeature">
                        <ignore path="src/main/AndroidManifest.xml" />
                    </issue>
                    <issue id="IllegalResourceRef">
                        <ignore regexp="must be a literal integer" />
                    </issue>
                </lint>
            """
            ).indented(),
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- This will turn it off both in app, lib and indirectlib -->
                    <issue id="MultipleUsesSdk">
                        <ignore path="src/main/AndroidManifest.xml" />
                    </issue>
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.application'")
        ).dependsOn(lib).name("app")

        lint().issues(*manifestIssues).projects(main).run().expect(
            """
            app/src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library android:name="android.test.runner" android:required="false" />
                ~~~~~~~~~~~~
            indirectLib/src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library android:name="android.test.runner" android:required="false" />
                ~~~~~~~~~~~~
            app/src/main/AndroidManifest.xml:9: Warning: <uses-sdk> tag appears after <application> tag [ManifestOrder]
               <uses-sdk android:targetSdkVersion="24" />
                ~~~~~~~~
            lib/src/main/AndroidManifest.xml:9: Warning: <uses-sdk> tag appears after <application> tag [ManifestOrder]
               <uses-sdk android:targetSdkVersion="24" />
                ~~~~~~~~
            2 errors, 2 warnings
            """
        )
    }

    fun testFlagsAndLintXmlInteraction() {
        // Checks that when we have a merged DSL configuration, those are applied correctly,
        // especially when there is both a lint.xml file and a manual lintConfig(xmlfile)
        // option specified in the same place
        lint().files(
            manifest,
            gradle(
                """
                apply plugin: 'com.android.application'
                android {
                    lintOptions {
                        // TODO: Test checkAllWarnings and warningsAsErrors also working!
                        //checkAllWarnings true
                        //warningsAsErrors true

                        // Also enabled by lint.xml in same folder; this setting should win
                        disable 'IllegalResourceRef'
                        // Also enabled by lint.xml in src/main folder: that setting should win
                        disable 'MultipleUsesSdk'
                        lintConfig file("default-lint.xml")
                        // Also set in default-lint.xml, but this setting should win
                        disable 'DuplicateUsesFeature'
                        checkTestSources true
                    }
                }
                """
            ).indented(),
            xml(
                "default-lint.xml",
                """
                <lint>
                    <!-- Conflicts with build.gradle setting; Gradle wins -->
                    <issue id="DuplicateUsesFeature" severity="fatal" />
                    <!-- Only configured here: should be applied -->
                    <issue id="ManifestOrder" severity="ignore" />
                    <!-- Configured both by default-lint.xml and lint.xml in the same folder:
                         this configuration wins -->
                    <issue id="OldTargetApi" severity="ignore" />
                </lint>
            """
            ).indented(),
            xml(
                "../lint.xml",
                """
                <lint>
                    <issue id="AllowBackup" severity="ignore" />
                </lint>
                """
            ).indented(),
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- Defined in same folder as build.gradle. Gradle wins and turns it off. -->
                    <issue id="IllegalResourceRef" severity="fatal" />
                    <!-- Should be inherited into sources (no conflict in build.gradle) -->
                    <issue id="WrongManifestParent" severity="ignore" />
                    <!-- Also configured by lint-default.xml in same folder: that one wins -->
                    <issue id="OldTargetApi" severity="error" />
                </lint>
                """
            ).indented(),
            xml(
                "src/lint.xml",
                """
                <lint>
                    <issue id="MissingApplicationIcon" severity="ignore" />
                </lint>
            """
            ).indented(),
            xml(
                "src/main/lint.xml",
                """
                <lint>
                    <!-- Overrides setting in build.gradle because it's closer to the source -->
                    <issue id="MultipleUsesSdk" severity="fatal" />
                </lint>
            """
            ).indented()
        ).issues(*manifestIssues).run().expect(
            """
src/main/AndroidManifest.xml:10: Error: There should only be a single <uses-sdk> element in the manifest: merge these together [MultipleUsesSdk]
   <uses-sdk android:minSdkVersion="15" />
    ~~~~~~~~
    src/main/AndroidManifest.xml:9: Also appears here
1 errors, 0 warnings
            """
        )
    }
}
