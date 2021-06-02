/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class ApiDetectorProvisionalTest : AbstractCheckTest() {
    @Suppress("LintDocExample")
    fun testBasicProvisional() {
        // Here we have a library with minSdk 2, which has two violations; one
        // for API level 4, one for API level 14. When we analyze this from an
        // app module with API level 8, only the second one should be reported.
        // This test makes sure that the provisional mechanism (which divides
        // this setup into a separate library and a consuming app module with
        // the given provisional manifest, and then performs the analysis
        // separately on each project, and then runs the filtering code on
        // the serialized results from the library) correctly works, and correctly
        // updates the incident error messages etc.
        val lib = project(
            manifest().minSdk(2),
            kotlin(
                """
                import android.graphics.drawable.BitmapDrawable
                import android.widget.GridLayout

                fun test(resources: android.content.res.Resources) {
                   val layout = GridLayout(null) // requires API 14
                   val drawable = BitmapDrawable(resources) // requires API 4
                }
                """
            )
        ).name("library")

        val app = project(manifest().minSdk(8)).dependsOn(lib)

        lint().projects(app, lib)
            .reportFrom(app)
            .run()
            .expect(
                """
                ../library/src/test.kt:6: Error: Call requires API level 14 (current min is 8): android.widget.GridLayout() [NewApi]
                                   val layout = GridLayout(null) // requires API 14
                                                ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testDisabledIssues() {
        val lib = project(
            manifest().minSdk(2),
            kotlin(
                """
                import android.widget.GridLayout
                import android.graphics.drawable.BitmapDrawable

                fun test(resources: android.content.res.Resources) {
                    val layout = GridLayout(null) // requires API 14
                    val path = "/sdcard/path"
                    val ok = java.lang.Boolean(true)
                    val drawable = BitmapDrawable(resources) // requires API 4
                }
                """
            ),
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- Normally enabled; disabled while analyzing this module -->
                    <issue id="UseValueOf" severity="hide" />
                    <!-- Disable here, but *also* in app; that should not be a warning -->
                    <issue id="UseSparseArrays" severity="hide" />
                </lint>
                """
            ).indented()
        ).name("lib")

        val app = project(
            manifest().minSdk(8),
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- This issue was not explicitly enabled in library but is off by default -->
                    <issue id="StopShip" severity="error" />
                    <!-- This issue is normally enabled but was disabled in the module -->
                    <issue id="UseValueOf" severity="error" />
                    <!-- The issue is implicitly on in both cases -->
                    <issue id="HardcodedText" severity="error" />
                    <!-- Disabled only in the app; we should not merge results in -->
                    <issue id="NewApi2" severity="hide" />
                    <!-- Disabled only in the app; we should not merge results in.
                         This is similar to the NewApi case but sdcardpaths are
                         reported as definite incidents whereas NewApi is reported
                         provisionally -->
                    <issue id="SdCardPath2" severity="hide" />
                    <!-- Don't complain about issues suppressed in both library and app -->
                    <issue id="UseSparseArrays" severity="hide" />
                </lint>
                """
            ).indented()
        ).dependsOn(lib).name("app")

        lint().projects(app, lib)
            .issues(
                CommentDetector.STOP_SHIP,
                JavaPerformanceDetector.USE_VALUE_OF,
                JavaPerformanceDetector.USE_SPARSE_ARRAY,
                HardcodedValuesDetector.ISSUE,
                ApiDetector.UNSUPPORTED,
                SdCardDetector.ISSUE
            )
            // Normally issues referenced by a test are all forced to
            // be enabled (to make it easy to test issues that are disabled
            // by default) via a special test configuration. However, here we
            // need to use a real configuration where issues can be disabled.
            .useTestConfiguration(false)
            .reportFrom(app)
            // Here we're testing that we get the extra UnknownIssueId errors
            // reported; those will only be used in partial analysis mode
            // so limit test runs to that
            .testModes(TestMode.PARTIAL)
            .run()
            .expect(
                """
                lint.xml:3: Warning: Issue StopShip was configured with severity error in app, but was not enabled (or was disabled) in library lib [CannotEnableHidden]
                    <issue id="StopShip" severity="error" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                lint.xml:5: Warning: Issue UseValueOf was configured with severity error in app, but was not enabled (or was disabled) in library lib [CannotEnableHidden]
                    <issue id="UseValueOf" severity="error" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                lint.xml:9: Warning: Unknown issue id "NewApi2". Did you mean 'NewApi' (Calling new methods on older versions) ? [UnknownIssueId]
                    <issue id="NewApi2" severity="hide" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                lint.xml:14: Warning: Unknown issue id "SdCardPath2". Did you mean 'SdCardPath' (Hardcoded reference to /sdcard) ? [UnknownIssueId]
                    <issue id="SdCardPath2" severity="hide" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                ../lib/src/test.kt:6: Error: Call requires API level 14 (current min is 8): android.widget.GridLayout() [NewApi]
                                    val layout = GridLayout(null) // requires API 14
                                                 ~~~~~~~~~~~~~~~~
                ../lib/src/test.kt:7: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
                                    val path = "/sdcard/path"
                                                ~~~~~~~~~~~~
                1 errors, 5 warnings
                """
            )
    }

    fun testOverrides() {
        val lib = project(
            manifest().minSdk(2),
            kotlin(
                """
                import android.widget.GridLayout
                import android.graphics.drawable.BitmapDrawable

                fun test(resources: android.content.res.Resources) {
                    val layout = GridLayout(null) // requires API 14
                    val path = "/sdcard/path"
                    val ok = java.lang.Boolean(true)
                    val drawable = BitmapDrawable(resources) // requires API 4
                }
                """
            ),
            xml(
                "lint.xml",
                """
                <lint>
                    <!-- Normally enabled; disabled while analyzing this module -->
                    <issue id="UseValueOf" severity="hide" />
                </lint>
                """
            ).indented(),
            gradle(
                """
                android {
                    lintOptions {
                        enable 'BogusId'
                    }
                }
                """
            )
        ).name("lib")

        val app = project(
            manifest().minSdk(8),
            xml(
                "lint-override.xml",
                """
                <lint>
                    <!-- This issue was not explicitly enabled in library but is off by default.
                         We don't warn about this because the override config will turn it on. -->
                    <issue id="StopShip" severity="error" />
                    <!-- This issue is normally enabled but was disabled in the module -->
                    <issue id="UseValueOf" severity="error" />
                    <!-- The issue is implicitly on in both cases -->
                    <issue id="HardcodedText" severity="error" />
                    <!-- Disabled only in the app; we should not merge results in -->
                    <issue id="NewApi" severity="hide" />
                    <!-- Disabled only in the app; we should not merge results in.
                         This is similar to the NewApi case but sdcardpaths are
                         reported as definite incidents whereas NewApi is reported
                         provisionally -->
                    <issue id="SdCardPath" severity="hide" />
                    <!-- Typos; this tests that we do validation of issues in the
                         override file and that they're only reported once, not
                         for every project including the override config. -->
                    <issue id="NewApi2" severity="hide" />
                    <issue id="SdCardPath2" severity="hide" />
                </lint>
                """
            ).indented()
        ).dependsOn(lib).name("app")

        lint().projects(app, lib)
            .issues(
                CommentDetector.STOP_SHIP,
                JavaPerformanceDetector.USE_VALUE_OF,
                HardcodedValuesDetector.ISSUE,
                ApiDetector.UNSUPPORTED,
                SdCardDetector.ISSUE
            )
            // TODO: Try setting lint.xml file above library and make sure it's
            // not inherited  "../lint.xml" for one of the issues that
            // is explicitly overridden in lint-overrides. E.g. if I
            // disable on of them.
            .useTestConfiguration(false)
            .reportFrom(app)
            // We're testing specific error messages created in partial analysis
            // mode when you disable in upstream libraries
            .testModes(TestMode.PARTIAL)
            .run()
            // The problem seems to be that we don't run validation on override
            // configurations
            .expect(
                """
                lint-override.xml:4: Warning: Issue StopShip was configured with severity error in app, but was not enabled (or was disabled) in library lib [CannotEnableHidden]
                    <issue id="StopShip" severity="error" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                lint-override.xml:6: Warning: Issue UseValueOf was configured with severity error in app, but was not enabled (or was disabled) in library lib [CannotEnableHidden]
                    <issue id="UseValueOf" severity="error" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                lint-override.xml:19: Warning: Unknown issue id "NewApi2". Did you mean 'NewApi' (Calling new methods on older versions) ? [UnknownIssueId]
                    <issue id="NewApi2" severity="hide" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                lint-override.xml:20: Warning: Unknown issue id "SdCardPath2". Did you mean 'SdCardPath' (Hardcoded reference to /sdcard) ? [UnknownIssueId]
                    <issue id="SdCardPath2" severity="hide" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
            )
    }

    override fun getDetector(): Detector = ApiDetector()
}
