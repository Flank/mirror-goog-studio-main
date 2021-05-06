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

import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.MainTest.checkDriver
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.JavaPerformanceDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.SdCardDetector
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import org.junit.rules.TemporaryFolder
import java.io.File

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

    /** Manifest with a number of problems. */
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
                    <issue id="WrongManifestParent" severity="ignore" />
                    <!-- does not override inherited (would in a direct project, not a lib project -->
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

        val temp = TemporaryFolder()
        temp.create()
        lint()
            .issues(*manifestIssues)
            .projects(main)
            .rootDirectory(temp.root.canonicalFile)
            // TODO -- remove this after fixing bug listed below
            .testModes(TestMode.PARTIAL)
            .reportFrom(main)
            .run()
            .expect(
                // TODO: The second entry is wrong; we're not handling *indirect* configuration
                // chains in the merge incident setup
                """
                src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
                   <uses-library android:name="android.test.runner" android:required="false" />
                    ~~~~~~~~~~~~
                ../indirectLib/src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
                   <uses-library android:name="android.test.runner" android:required="false" />
                    ~~~~~~~~~~~~
                ../indirectLib/src/main/AndroidManifest.xml:9: Error: <uses-sdk> tag appears after <application> tag [ManifestOrder]
                   <uses-sdk android:targetSdkVersion="24" />
                    ~~~~~~~~
                ../lib/src/main/AndroidManifest.xml:9: Error: <uses-sdk> tag appears after <application> tag [ManifestOrder]
                   <uses-sdk android:targetSdkVersion="24" />
                    ~~~~~~~~
                4 errors, 0 warnings
                """
            )
        temp.delete()
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
                        <ignore regexp="AndroidManifest.xml" />
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
                        <ignore regexp="AndroidManifest.xml" />
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
                    <issue id="OldTargetApi" severity="hide" />
                    <issue id="AllowBackup" severity="hide" />
                    <issue id="MissingApplicationIcon" severity="hide" />
                    <!-- This is in a parent of all; will work everywhere -->
                    <issue id="DuplicateUsesFeature">
                        <ignore regexp="AndroidManifest.xml" />
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
                    <!-- This will turn it off in all of app, lib and indirectlib -->
                    <issue id="MultipleUsesSdk">
                        <ignore regexp="AndroidManifest.xml" />
                    </issue>
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.application'")
        ).dependsOn(lib).name("app")

        lint()
            .issues(*manifestIssues)
            .useTestConfiguration(false)
            .reportFrom(main)
            // TODO -- remove this after fixing bug listed below
            .testModes(TestMode.PARTIAL)
            .projects(main)
            .run()
            .expect(
                // TODO: Here the second result is wrong; somehow when computing the configuration
                // hierarchy in library merging were not comprehensively.
                """
            src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library android:name="android.test.runner" android:required="false" />
                ~~~~~~~~~~~~
            ../indirectLib/src/main/AndroidManifest.xml:11: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library android:name="android.test.runner" android:required="false" />
                ~~~~~~~~~~~~
            src/main/AndroidManifest.xml:9: Warning: <uses-sdk> tag appears after <application> tag [ManifestOrder]
               <uses-sdk android:targetSdkVersion="24" />
                ~~~~~~~~
            ../lib/src/main/AndroidManifest.xml:9: Warning: <uses-sdk> tag appears after <application> tag [ManifestOrder]
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
                        informational 'MultipleUsesSdk'
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
                         that one configuration wins -->
                    <issue id="OldTargetApi" severity="error" />
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
                    <!-- Also configured by lint-default.xml in same folder: this one wins -->
                    <issue id="OldTargetApi" severity="ignore" />
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
   <uses-sdk android:targetSdkVersion="24" />
    ~~~~~~~~
1 errors, 0 warnings
            """
        )
    }

    fun testOverrideAndFallbackConfigurations() {
        val project = getProjectDir(
            null,
            manifest().minSdk(1),
            xml(
                "lint.xml",
                """
                    <lint>
                        <!-- // Overridden in override.xml: this is ignored -->
                        <issue id="DuplicateDefinition" severity="fatal"/>
                        <!-- // Set in both fallback and here: this is used -->
                        <issue id="DuplicateIds" severity="ignore"/>
                        <!-- // Not set in fallback or override: this is used -->
                        <issue id="SdCardPath" severity="ignore"/>
                    </lint>
                    """
            ).indented(),
            xml(
                "fallback.xml",
                """
                <lint>
                    <issue id="DuplicateIds" severity="fatal"/>
                </lint>
                """
            ).indented(),
            xml(
                "override.xml",
                """
                <lint>
                    <issue id="DuplicateDefinition" severity="ignore"/>
                </lint>
                """
            ).indented(),
            xml(
                "res/layout/test.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                    <Button android:id='@+id/duplicated'/>    <Button android:id='@+id/duplicated'/></LinearLayout>
                """
            ).indented(),
            xml(
                "res/values/duplicates.xml",
                """
                <resources>
                    <item type="id" name="name" />
                    <item type="id" name="name" />
                </resources>
                """
            ).indented(),
            kotlin("val path = \"/sdcard/path\"")
        )
        checkDriver(
            "No issues found.",
            "", // Expected exit code
            LintCliFlags.ERRNO_SUCCESS,
            arrayOf<String>(
                "--quiet",
                "--disable",
                "LintError",
                "--disable",
                "UsesMinSdkAttributes,UnusedResources,ButtonStyle,UnusedResources,AllowBackup,LintError",
                "--config",
                File(project, "fallback.xml").path,
                "--override-config",
                File(project, "override.xml").path,
                project.path
            )
        )
    }

    fun testOverrideAndFallbackConfigurations2() {
        // Like testOverrideAndFallbackConfigurations, but here we don't have a local
        // lint.xml file; we want to make sure that we consult the fallback if not
        // specified in the override (since getParent() on overrides deliberately don't
        // return a fallback, in order to allow a local lint.xml lookup (which will
        // always consult the override first) to not automatically jump to the fallback
        // via getParent there, since we need to go back to the local lint file first.
        val project = getProjectDir(
            null,
            manifest().minSdk(1),
            xml(
                "fallback.xml",
                """
                <lint>
                    <issue id="DuplicateIds" severity="ignore"/>
                </lint>
                """
            ).indented(),
            xml(
                "override.xml",
                """
                <lint>
                    <issue id="DuplicateDefinition" severity="ignore"/>
                </lint>
                """
            ).indented(),
            xml(
                "res/layout/test.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                    <Button android:id='@+id/duplicated'/>    <Button android:id='@+id/duplicated'/></LinearLayout>
                """
            ).indented(),
            xml(
                "res/values/duplicates.xml",
                """
                <resources>
                    <item type="id" name="name" />
                    <item type="id" name="name" />
                </resources>
                """
            ).indented()
        )
        checkDriver(
            "No issues found.",
            "", // Expected exit code
            LintCliFlags.ERRNO_SUCCESS,
            arrayOf<String>(
                "--quiet",
                "--disable",
                "LintError",
                "--disable",
                "UsesMinSdkAttributes,UnusedResources,ButtonStyle,UnusedResources,AllowBackup",
                "--config",
                File(project, "fallback.xml").path,
                "--override-config",
                File(project, "override.xml").path,
                project.path
            )
        )
    }

    private fun checkDriver(
        expectedOutput: String,
        expectedError: String,
        expectedExitCode: Int,
        args: Array<String>
    ) {
        checkDriver(
            expectedOutput,
            expectedError,
            expectedExitCode,
            args,
            this::cleanup,
            null
        )
    }

    fun testProvisionalFiltering() {
        // Tests the inheritance of severity among modules when using provisional reporting
        val lib = project(
            manifest().minSdk(2),
            xml(
                "lint.xml",
                """
                <lint>
                    <issue id="UseValueOf" severity="error" />
                </lint>
                """
            ).indented(),
            gradle("apply plugin: 'com.android.library'"),
            kotlin(
                """
                    package test.pkg
                    fun test() {
                        val x = "/sdcard/warning"
                        val y = android.widget.GridLayout(null)
                        val z = java.lang.Integer(42)
                    }
                """
            )
        ).name("lib")

        val main = project(
            manifest().minSdk(5),
            xml(
                "lint.xml",
                """
                <lint>
                    <issue id="NewApi" severity="warning" />
                    <issue id="SdCardPath" severity="ignore" />
                    <issue id="UseValueOf" severity="warning" />
                </lint>
            """
            ).indented(),
            gradle("apply plugin: 'com.android.application'")
        ).dependsOn(lib).name("app")

        // The UseValueOf issue should be reported as error, since specifically configured for lib
        // The SdCardPath issue should be hidden, since only defined in app, and should inherit
        // The NewApi issue should have severity warning, as inherited from app

        lint()
            .issues(ApiDetector.UNSUPPORTED, SdCardDetector.ISSUE, JavaPerformanceDetector.USE_VALUE_OF)
            .reportFrom(main)
            .projects(lib, main)
            .run()
            .expect(
                """
                ../lib/src/main/kotlin/test/pkg/test.kt:5: Warning: Call requires API level 14 (current min is 5): android.widget.GridLayout() [NewApi]
                                        val y = android.widget.GridLayout(null)
                                                               ~~~~~~~~~~~~~~~~
                ../lib/src/main/kotlin/test/pkg/test.kt:6: Error: Use Integer.valueOf(42) instead [UseValueOf]
                                        val z = java.lang.Integer(42)
                                                ~~~~~~~~~~~~~~~~~~~~~
                1 errors, 1 warnings
                """
            )
    }

    // TODO: Multi projects
    // TODO: Warn if libraries use unconfigured
    // TODO: Test issue turned off in main
}
