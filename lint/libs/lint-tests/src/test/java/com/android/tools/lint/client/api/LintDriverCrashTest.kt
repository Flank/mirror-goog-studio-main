/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.VALUE_TRUE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.MainTest
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.truth.Truth.assertThat
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Files
import java.util.Locale

class LintDriverCrashTest : AbstractCheckTest() {
    fun testLintDriverError() {
        // Regression test for 34248502
        lint().files(
            xml("res/layout/foo.xml", "<LinearLayout/>"),
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .issues(CrashingDetector.CRASHING_ISSUE)
            .run()
            // Checking for manual substrings instead of doing an actual equals check
            // since the stacktrace contains a number of specific line numbers from
            // the lint implementation, including this test, which keeps shifting every
            // time there is an edit
            .check(
                {
                    assertThat(it).contains("Foo.java: Error: Unexpected failure during lint analysis of Foo.java (this is a bug in lint or one of the libraries it depends on)")
                    assertThat(it).contains("The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingDetector.")
                    assertThat(it).contains(
                        """
                        The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingDetector.
                        You can try disabling it with something like this:
                            android {
                                lintOptions {
                                    disable "_TestCrash"
                                }
                            }
                        """.trimIndent()
                    )

                    // It's not easy to set environment variables from Java once the process is running,
                    // so instead of attempting to set it to true and false in tests, we'll just make this
                    // test adapt to what's set in the environment. On our CI tests, it should not be
                    // set, so the doesNotContain() assertion will be used. For developers on the lint team
                    // it's typically set so the contains() assertion will be used.
                    val suggestion = "You can run with --stacktrace or set environment variable LINT_PRINT_"
                    if (System.getenv("LINT_PRINT_STACKTRACE") == VALUE_TRUE) {
                        assertThat(it).doesNotContain(suggestion)
                    } else {
                        assertThat(it).contains(suggestion)
                    }

                    assertThat(it).contains("ArithmeticException:LintDriverCrashTest＄CrashingDetector＄createUastHandler＄1.visitFile(LintDriverCrashTest.kt:")
                    assertThat(it).contains("1 errors, 0 warnings")
                }
            )
        LintDriver.clearCrashCount()
    }

    fun testSavePartialResults() {
        // Regression test for https://issuetracker.google.com/192484319
        // If a detector crashes, that should not invalidate any other results from the module
        val root = Files.createTempDirectory("lintjar").toFile()

        val projects = lint().files(
            manifest(
                """
                <manifest package="test.pkg"/>
                """
            ).indented(),
            source(
                "res/private_key.pem",
                """
                -----BEGIN RSA PRIVATE KEY-----
                -----END RSA PRIVATE KEY-----
                """
            ).indented(),
            java(
                """
                package test.pkg;
                class Foo {
                }
                """
            ).indented(),

            // Deliberately crashing lint check

            *JarFileIssueRegistryTest.lintApiStubs,
            bytecode(
                "lint.jar",
                source(
                    "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
                    "test.pkg.MyIssueRegistry"
                ),
                0x70522285
            ),
            bytecode(
                "lint.jar",
                kotlin(
                    """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyDetector : Detector(), OtherFileScanner {
                        override fun getApplicableFiles(): EnumSet<Scope> {
                            return EnumSet.of(Scope.OTHER)
                        }

                        override fun run(context: Context) {
                            //context.report(ISSUE, Location.create(context.file), "My message")
                            @Suppress("DIVISION_BY_ZERO", "UNUSED_VARIABLE") // Intentional crash
                            val x = 1 / 0
                        }

                        companion object {
                            @JvmField
                            val ISSUE = Issue.create(
                                id = "MyIssueId",
                                briefDescription = "My Summary",
                                explanation = "My full explanation.",
                                category = Category.LINT,
                                priority = 10,
                                severity = Severity.WARNING,
                                implementation = Implementation(MyDetector::class.java, EnumSet.of(Scope.OTHER))
                            )
                        }
                    }
                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = listOf(MyDetector.ISSUE)
                        override val api: Int = 9
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
                ).indented(),
                0xd89a7462,
                """
                test/pkg/MyDetector.class:
                H4sIAAAAAAAAAJ1WWVMbRxD+RgIdi4xlGWPwSeJLMjYr4yuxsBOMsVkihI0w
                jkOuZTWIRXsou7MK5Mm/Jb8gxlWxK65KUXnM78lzKj2LsADrYQlip3t6pru/
                6enpmb///eNPALfwC8NxwX2hNht1dW7zERfcEK6XBGO4Zri2qjs1zzVrqnBd
                y1ct0xFqrT1J1Zum2tGIM9yKojEv1rj32LR41dAdh5NmL0NiwnRM8YAhni8s
                ZZBESkEP0gw9Ys30GU6Uu6AsMeTqXEw2m5Zp6CsWl1bl5HyhvK63dDUQpqVO
                O4Fd5YImP+wmnyhHAV013CYvPSAjF8quV1fXuVjxdNPxSdNxhS5Ml/iKKyqB
                ZdGsQmSjSRxj6J1fnJleYBg9BJgMjmMgjRxOMPSHy7J0px4uK4mTDMc+WmoS
                wwwxd5XhUr68X6PULWIZnMYZBadwluGou5oP/Y6FUAspnGc413AFwVPXW7ZK
                KLnn6JaqOcKjwJiGn8QnDKeNNW402pGZ3mh63PcpWEu6FXCGK3uBzK+s0yJL
                eyRVaapekilxARcVfIpLlCJe4DAU85FiNeUSrg1BJhiSxk4nhQIlyV5cT3VP
                t0nNy2B0x881BrZBn8Zw/VCOGFIThtXO5rNdsvbilGs3dYdikMQ4w418uVsU
                H/FVPbAEWfWFF0i9Od1rcK+0czxuKbiJ2wzpD8YYznc7Ix1vtJ138VkaMXwe
                MT813w8oP2lJtyOFIJx/wOH9NCZAkUjPbYbDWi2FLxmUuc2RamDbureZwkOG
                Aeqv0kaM8I0m7Xx4nsZSeBSxCk3pgtddbzOJx1QyylplkWEs2ra1NQnsDLQ0
                nmA2os8qb3HPFOSzTKn1YnKholWeRHW7q0xuK5hPYw5PGcYjBdluWtzmzk7N
                SWLhwCGasnTfL3U5zTuJs6igiucMN//Hhibxgiq14XGKGcM/+Y8PaiTJobZF
                O1w4o+XpvhBS6YscC9qul/hGwddYpqKtVavPp6MW7V397/C9PIM/yDttz8Gf
                bdmPTW7V6LAd25XPcaHXdKGTLGa34nRhM9nQdckaJNowZa9IXO0Gw+r2q8tK
                bCjW+VIdfvuVEsvukLA3tP1qpG88VmT0xe6xvoe9f/2aiGXjs/3ZnlOpXE8u
                VkwUe2cGZ7PZ5KlYMTWeyKaJKjOD0tt41Czfc1HfiaRw8G1Aise7XuVgONIp
                cmMNQRfNQuAI0+aa0zJ9k14Dk52bmYrClFujpD1aNh1eCewV7i3KFwO9IMqu
                oVtLumfKfluYrpp1KkKBR/zFg3Y/XBb7HGQ0CTg8ffINolTdwDPCFwnDcNvE
                0kfAcINyoYdWkyA6LAs08T/RtiaIZokOy4RpyxSaoxOVcqlDryRqPeqpRJlM
                jatvobwmJga/bUS2gtrMzgT0EReGD/2Ih8oTNDtGNLeFwW0MvcO58mhu5B0u
                j/52wFJqj6Vc21IQzjm6DzSVJORJKq0/IyrztG80d/UdrvfErfsdgBJCgto0
                wVHIiDQ/QjKFTIzRqiSsvrYjyRVQpJEW8UnW9pWiEO6GwaYp8u/2e9x8yd7i
                zhvc28IXucncVG56C19llS08e4/qy1zsw1rfYul3fPsGP74OgUtUlwlPH72u
                +jFALk7QUgeJO0m/IdqPYZyhJ9EYjcfxc7grVE0J6V0a2QhX1sQm0XmSr1DA
                jWXENdQ0cGqxKpu6hjWYy2A+1tFYRsaX/5YP20fKxxEflBkDPq74cH0UfBR9
                3P0PwOUFtrQLAAA=
                """,
                """
                test/pkg/MyDetector＄Companion.class:
                H4sIAAAAAAAAAJVSTU8UQRB93bNfjIvMgiKgiB+rAY00EA9GDIkummyyqwno
                xoSDaXZbbHamm0z3Er3tSf+H/8CTiQez4eiPMtYMq1yMiTNJVderelU9r+bH
                z2/fAdzHbYZFr5wXR/0D0f6wrbzqepvWGzY5kkZbUwZjiA7lsRSxNAfixf4h
                VZQRMJQeaaP9FkOwvNKpoohSiALKDAX/TjuGpdY/O28yrC+3+tbH2ojD40Ro
                41VqZCy21Vs5iH3DGufTQcZqy7Sv0s2VTgieTZipd8+Sb5I8y7D6f90Yar8J
                beVlT3pJGE+OA5KGZabIwPoEvddZtEan3jrD1mgYhXyOhzwaDUNe4VlQOfkY
                zI2GG3yNPSlX+MnnEo/4ThQFC3yt8KD0+uRTIcPC0TDrssFoAorN3d1XTxnu
                tro2EdL0Uqt7wlsbO0G38qI3Fk3IIy2azg0UXXDmL6qWcYVh4o+0DJNnudW+
                p5U0bE8xTLW0Uc8Hyb5KX8r9mJDplu3KuCNTncVjsNo0RqWNWDqnaJHhrh2k
                XfVMZ7n5nYHxOlEd7TQVPzbGeulpqMM6LaeA7OH00v9A33iNIkGeZXLe+YrK
                lzx9nWwpB2u4QbZ6WoAJhEBE0uDcmHyPPB+TqxmZ5YTZU/CUkJ8mcZ5yAW5S
                FOakRVzFPOr5wCXcIv+Q8CmqjfYQNFFrYposZjJzoYmLmN0Dc7iEuT0UHUKH
                eYeSw4LD5V/RhavBMwMAAA==
                """,
                """
                test/pkg/MyIssueRegistry.class:
                H4sIAAAAAAAAAKVVW28bRRT+Zn13nWTjppC4TbO5FBw3ZJ30BnWa4iYtLLUT
                FLcWKE8be2smXu9GO2OLIiHlV/ADEI88ACIqAglFfeRHIc56XezaiSjwsHPm
                nDnX75yZ/ePPX38HcBNlhmlpCakfNRt6+bkhRNvasxpcSO95DIxBr7kt3XTq
                nsvrunRdW+g2d6Res7lFxDzi+pBRiCG6wR0uNxlC2eVqChFEkwgjxnBxINa2
                Ja2adL0YEgwRo1J5+pDheumcePWedj9iIYULSCWQxBiD1nQl6VG2tk163HWE
                vtXfP5YxTFBeNuW4+4xhKVs6NDumbptOQ989OCS1wnIgaktu6yXSI/+TSCeh
                4iJZcj+kYFBHtS7hrQQUvE3lUnIMzEhhBhlfdpksW9wpHvEUZgPRVYbsP2Na
                tZy6D43GsFIMNLXdI8vRKm7bq1nap57rJ31XK5Gh9pTQ1p4QtCKOBYYEodXR
                nbZtx7HE8NVgsRXpcadR+G8So9SD+bDT0imw5TmmrW9bz8y2LbcIZ+m1/SaV
                Ta9peYWg9+8kMY93CYhOtyaG3Hk9HqmfwF1GzkftOkNYfsGpAZnSefNaoMob
                ljR6nUpnRzrKcH9EuPEvJm6TPCyWXK+hH1rywDM5TZnpOK40g4nbceUOoU5a
                UUqk6A8D3QAjyKvcnYNgX+1BsULpvDEYDPf+Z+6Tr/pXtqRZN6VJMqXVCdFT
                wPwlQsPbJNGX3OfytKuvMXx3epxNKtNK8MXpU+NEQ0S1nizy6mz69HhdybMH
                kZffRhVV2ZtSQxklH/7s5TfbJIknT48z4XhEje5l1Fgmng6nlXwiH6fjcP84
                qV4gu9So3RjZTanjdDDxuoWqTvq5rjOsvQGcw0MDhrH+c7TalNSjCm84pmx7
                FsPlvbYjecsynA4X/MC2iv2O01RuuXVSmqB7aO20WweW98QkHRq/klsz7arp
                cZ/vCZPB9X3EfWam57g64hZrNPJhakEYaf95Ie4RcQrex0dEo5TxOtG0/8x0
                6WyP0m2hs0GdCNFIl/uYuK+RoB2g514gnvsJ479g6gTTOTVxgis5NXaCudxv
                mP88vchY+poaZS+QPcHKj93gBq05em6Bq5TYHMahYYrudgYLxC3SboHWJVwj
                uko6n5BmKgiH90jiA60jj1A3FZ04/yyS+xnTP/wdINoVzg0YR3rGAQJrr1XH
                cIN+YmzE4ZXvhxxqZzhkuHWm8dyw8fyZxrdxh7SGjVeGS1k4w3iwBAWPu+tD
                lIiaJP2A9O7uI2SgYGCDVtzzl00D9/HhPphAEQ/2kRZYFdgSiArMCGwL3BC4
                KXCpu08J6AJ5gVmBWwLLAjmB2wJ3/gJB4/Mq+wcAAA==
                """,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AZcylmJyfq5eYl1KUn5miV5Kfn1Os
                l5OZV6KXnJOZCqQSCzKF+JzB7PjiktKkYu8SJQYtBgDDO/ZuTQAAAA==
                """
            )
        ).testModes(TestMode.DEFAULT).createProjects(root)

        val projectDir = projects[0]
        val lintJar = File(projectDir, "lint.jar")
        assertTrue(lintJar.isFile)
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        try {
            MainTest.checkDriver(
                null, // not checking output; contains stacktraces with line numbers
                null,

                // Expected exit code
                LintCliFlags.ERRNO_SUCCESS,

                // Args
                arrayOf(
                    "--analyze-only",
                    "--check",
                    "UsesMinSdkAttributes",
                    "--check",
                    "MyIssueId",
                    "--lint-rule-jars",
                    lintJar.path,
                    projectDir.path
                ),
                null,
                null
            )

            // Make sure we really had a crash during that analysis
            assertTrue(LintDriver.crashCount > 0)

            MainTest.checkDriver(
                """
                app: Error: No .class files were found in project "app", so none of the classfile based checks could be run. Does the project need to be built first? [LintError]
                AndroidManifest.xml: Warning: Manifest should specify a minimum API level with <uses-sdk android:minSdkVersion="?" />; if it really supports all versions of Android set it to 1 [UsesMinSdkAttributes]
                1 errors, 1 warnings
                """.trimIndent(),
                "",

                // Expected exit code
                LintCliFlags.ERRNO_SUCCESS,

                // Args
                arrayOf(
                    "--report-only",
                    "--check",
                    "UsesMinSdkAttributes",
                    "--check",
                    "MyIssueId",
                    "--lint-rule-jars",
                    lintJar.path,
                    projectDir.path
                ),
                null,
                null
            )
        } finally {
            temporaryFolder.delete()
            LintDriver.clearCrashCount()
        }
    }

    fun testLinkageError() {
        // Regression test for 34248502
        lint().files(
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .issues(LinkageErrorDetector.LINKAGE_ERROR)
            .run()
            .expect(
                """
                    src/test/pkg/Foo.java: Error: Lint crashed because it is being invoked with the wrong version of Guava
                    (the Android version instead of the JRE version, which is required in the
                    Gradle plugin).

                    This usually happens when projects incorrectly install a dependency resolution
                    strategy in all configurations instead of just the compile and run
                    configurations.

                    See https://issuetracker.google.com/71991293 for more information and the
                    proper way to install a dependency resolution strategy.

                    (Note that this breaks a lot of lint analysis so this report is incomplete.) [LintError]
                    1 errors, 0 warnings"""
            )
        LintDriver.clearCrashCount()
    }

    fun testUnitTestErrors() {
        // Regression test for https://issuetracker.google.com/74058591
        // Make sure the test itself fails with an error, not just an exception pretty printed
        // into the output as used to be the case
        try {
            lint().files(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings("ALL") class Foo {
                        }
                        """
                )
            )
                .allowSystemErrors(true)
                .allowExceptions(false)
                .issues(LinkageErrorDetector.LINKAGE_ERROR)
                .run()
                .expect(
                    "<doesn't matter, we shouldn't get this far>"
                )
            fail("Expected LinkageError to be thrown")
        } catch (e: LinkageError) {
            // OK
            LintDriver.clearCrashCount()
        }
    }

    override fun getIssues(): List<Issue> = listOf(
        CrashingDetector.CRASHING_ISSUE,
        DisposedThrowingDetector.DISPOSED_ISSUE, LinkageErrorDetector.LINKAGE_ERROR
    )

    override fun getDetector(): Detector = CrashingDetector()

    class CrashingDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    @Suppress("DIVISION_BY_ZERO", "UNUSED_VARIABLE") // Intentional crash
                    val x = 1 / 0
                    super.visitFile(node)
                }
            }

        companion object {
            @Suppress("LintImplTextFormat")
            val CRASHING_ISSUE = Issue
                .create(
                    "_TestCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(CrashingDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }

    // Regression test for https://issuetracker.google.com/123835101

    fun testHalfUppercaseColor2() {
        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    """
          <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:height="800dp"
              android:viewportHeight="800"
              android:viewportWidth="800"
              android:width="800dp">
            <path
                android:fillColor="#ffe000"
                android:pathData="M644.161,530.032 L644.161,529.032
          C644.161,522.469,638.821,517.129,632.258,517.129 L24.807,517.129 L24.807,282.871
          L775.194,282.871 L775.194,517.129 L683.872,517.129
          C677.309,517.129,671.969,522.469,671.969,529.032 L671.969,530.032
          L644.161,530.032 Z"/>
            <path
                android:fillColor="#fff000"
                android:pathData="M644.161,530.032 L644.161,529.032
          C644.161,522.469,638.821,517.129,632.258,517.129 L24.807,517.129 L24.807,282.871
          L775.194,282.871 L775.194,517.129 L683.872,517.129
          C677.309,517.129,671.969,522.469,671.969,529.032 L671.969,530.032
          L644.161,530.032 Z"/>
            <path
                android:fillColor="#ffe000"
                android:pathData="M683.871,516.129 L774.193,516.129 L774.193,283.871 L25.807,283.871
          L25.807,516.129 L632.258,516.129
          C639.384,516.129,645.161,521.906,645.161,529.032 L670.968,529.032
          C670.968,521.906,676.745,516.129,683.871,516.129 Z"/>
          </vector>"""
                ).indented()
            )
            .issues(ColorCasingDetector.ISSUE_COLOR_CASING)
            .run()
            .expect(
                """
                res/drawable/drawable.xml:7: Warning: Should be using uppercase letters [_ColorCasing]
                      android:fillColor="#ffe000"
                                         ~~~~~~~
                res/drawable/drawable.xml:14: Warning: Should be using uppercase letters [_ColorCasing]
                      android:fillColor="#fff000"
                                         ~~~~~~~
                res/drawable/drawable.xml:21: Warning: Should be using uppercase letters [_ColorCasing]
                      android:fillColor="#ffe000"
                                         ~~~~~~~
                0 errors, 3 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for res/drawable/drawable.xml line 7: Convert to uppercase:
                @@ -7 +7
                -       android:fillColor="#ffe000"
                +       android:fillColor="#FFE000"
                Fix for res/drawable/drawable.xml line 14: Convert to uppercase:
                @@ -14 +14
                -       android:fillColor="#fff000"
                +       android:fillColor="#FFF000"
                Fix for res/drawable/drawable.xml line 21: Convert to uppercase:
                @@ -21 +21
                -       android:fillColor="#ffe000"
                +       android:fillColor="#FFE000"
                """
            )
    }

    fun testAbsolutePaths() {
        if (CURRENT_PLATFORM == PLATFORM_WINDOWS) {
            // This check does not run on Windows
            return
        }

        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    "<test/>"
                ).indented()
            )
            .issues(AbsPathTestDetector.ABS_PATH_ISSUE)
            .run()
            .expect(
                """
                Found absolute path
                    TESTROOT/default/app/res/drawable/drawable.xml
                in a reported error message; this is discouraged because absolute
                paths do not play well with baselines, shared HTML reports, remote
                caching, etc.
                """,
                java.lang.AssertionError::class.java
            )

        // Allowing absolute paths
        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    "<test/>"
                ).indented()
            )
            .issues(AbsPathTestDetector.ABS_PATH_ISSUE)
            .allowAbsolutePathsInMessages(true)
            .run()
            .expectCount(1, Severity.WARNING)
    }

    // Invalid detector which includes absolute paths in error messages which should not be done
    class AbsPathTestDetector : ResourceXmlDetector() {
        override fun appliesTo(folderType: ResourceFolderType) = true
        override fun afterCheckFile(context: Context) {
            context.report(ABS_PATH_ISSUE, Location.create(context.file), "found error in " + context.file + "!")
        }

        companion object {
            val ABS_PATH_ISSUE = Issue.create(
                "_AbsPath",
                "Sample",
                "Sample",
                Category.CORRECTNESS, 5, Severity.WARNING,
                Implementation(AbsPathTestDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
            )
        }
    }

    class ColorCasingDetector : ResourceXmlDetector() {
        override fun appliesTo(folderType: ResourceFolderType) = true
        override fun getApplicableElements(): List<String> = ALL
        override fun visitElement(context: XmlContext, element: Element) {
            element.attributes()
                .filter { it.nodeValue.matches(COLOR_REGEX) }
                .filter { it.nodeValue.any { c -> c.isLowerCase() } }
                .forEach {
                    val fix = fix()
                        .name("Convert to uppercase")
                        .replace()
                        // .range(context.getValueLocation(it as Attr))
                        .text(it.nodeValue)
                        .with(it.nodeValue.toUpperCase(Locale.US))
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE_COLOR_CASING, it, context.getValueLocation(it as Attr),
                        "Should be using uppercase letters", fix
                    )
                }
        }

        companion object {
            val COLOR_REGEX = Regex("#[a-fA-F\\d]{3,8}")
            @Suppress("LintImplTextFormat")
            val ISSUE_COLOR_CASING = Issue.create(
                "_ColorCasing",
                "Raw colors should be defined with uppercase letters.",
                "Colors should have uppercase letters. #FF0099 is valid while #ff0099 isn't since the ff should be written in uppercase.",
                Category.CORRECTNESS, 5, Severity.WARNING,
                Implementation(ColorCasingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
            )
            internal fun Node.attributes() = (0 until attributes.length).map { attributes.item(it) }
        }
    }

    class DisposedThrowingDetector : LayoutDetector(), XmlScanner {

        override fun getApplicableElements(): Collection<String> {
            return arrayListOf("LinearLayout")
        }

        override fun visitElement(context: XmlContext, element: Element) {
            throw AssertionError("Already disposed: $this")
        }

        companion object {
            @Suppress("LintImplTextFormat")
            val DISPOSED_ISSUE = Issue.create(
                "_TestDisposed", "test", "test", Category.LINT,
                10, Severity.FATAL,
                Implementation(
                    DisposedThrowingDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
        }
    }

    class LinkageErrorDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    throw LinkageError(
                        "loader constraint violation: when resolving field " +
                            "\"QUALIFIER_SPLITTER\" the class loader (instance of " +
                            "com/android/tools/lint/gradle/api/DelegatingClassLoader) of the " +
                            "referring class, " +
                            "com/android/ide/common/resources/configuration/FolderConfiguration, " +
                            "and the class loader (instance of " +
                            "org/gradle/internal/classloader/VisitableURLClassLoader) for the " +
                            "field's resolved type, com/google/common/base/Splitter, have " +
                            "different Class objects for that type"
                    )
                }
            }

        companion object {
            @Suppress("LintImplTextFormat")
            val LINKAGE_ERROR = Issue
                .create(
                    "_LinkageCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(LinkageErrorDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }
}
