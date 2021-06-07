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
package com.android.tools.lint.client.api

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.util.Arrays

class JarFileIssueRegistryTest : AbstractCheckTest() {
    override fun lint(): TestLintTask = TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())

    fun testError() {
        val loggedWarnings = StringWriter()
        val client = createClient(loggedWarnings)
        getSingleRegistry(client, File("bogus"))
        assertThat(loggedWarnings.toString()).contains(
            "Could not load custom lint check jar files: bogus"
        )
    }

    fun testCached() {
        val targetDir = TestUtils.createTempDirDeletedOnExit().toFile()
        val file1 = base64gzip(
            "lint.jar",
            CustomRuleTest.LINT_JAR_BASE64_GZIP
        ).createFile(targetDir)
        val file2 = jar("unsupported.jar").createFile(targetDir)
        assertTrue(file1.path, file1.exists())
        val loggedWarnings = StringWriter()
        val client = createClient(loggedWarnings)
        val registry1 = getSingleRegistry(client, file1) ?: fail()
        val registry2 = getSingleRegistry(client, File(file1.path)) ?: fail()
        assertSame(registry1, registry2)
        val registry3 = getSingleRegistry(client, file2)
        assertThat(registry3).isNull()

        assertEquals(1, registry1.issues.size)
        assertEquals("UnitTestAppCompatMethod", registry1.issues[0].id)

        // Access detector state. On Java 7/8 this will access the detector class after
        // the jar loader has been closed; this tests that we still have valid classes.
        val detector = registry1.issues[0].implementation.detectorClass.newInstance()
        val applicableCallNames = detector.getApplicableCallNames()
        assertNotNull(applicableCallNames)
        assertTrue(applicableCallNames!!.contains("getActionBar"))

        assertEquals(
            "Custom lint rule jar " + file2.path + " does not contain a valid " +
                "registry manifest key (Lint-Registry-v2).\n" +
                "Either the custom jar is invalid, or it uses an outdated API not " +
                "supported this lint client\n",
            loggedWarnings.toString()
        )

        // Make sure we handle up to date checks properly too
        val composite = CompositeIssueRegistry(
            Arrays.asList<IssueRegistry>(registry1, registry2)
        )
        assertThat(composite.isUpToDate).isTrue()

        assertThat(registry1.isUpToDate).isTrue()
        file1.setLastModified(file1.lastModified() + 2000)
        assertThat(registry1.isUpToDate).isFalse()
        assertThat(composite.isUpToDate).isFalse()
    }

    fun testDeduplicate() {
        val targetDir = TestUtils.createTempDirDeletedOnExit().toFile()
        val file1 = base64gzip(
            "lint1.jar",
            CustomRuleTest.LINT_JAR_BASE64_GZIP
        ).createFile(targetDir)
        val file2 = base64gzip(
            "lint2.jar",
            CustomRuleTest.LINT_JAR_BASE64_GZIP
        ).createFile(targetDir)
        assertTrue(file1.path, file1.exists())
        assertTrue(file2.path, file2.exists())

        val loggedWarnings = StringWriter()
        val client = createClient(loggedWarnings)

        val registries = JarFileIssueRegistry.get(client, listOf(file1, file2), null)
        // Only *one* registry should have been computed, since the two provide the same lint
        // class names!
        assertThat(registries.size).isEqualTo(1)
    }

    fun testGetDefaultIdentifier() {
        val targetDir = TestUtils.createTempDirDeletedOnExit().toFile()
        val file1 = base64gzip(
            "lint1.jar",
            CustomRuleTest.LINT_JAR_BASE64_GZIP
        ).createFile(targetDir)
        assertTrue(file1.path, file1.exists())

        val loggedWarnings = StringWriter()
        val client = createClient(loggedWarnings)

        val registry = JarFileIssueRegistry.get(client, listOf(file1), null).first()
        val vendor = registry.vendor
        assertNotNull(vendor)
        assertEquals("android.support.v7.lint.appcompat", vendor.identifier)
        assertEquals(
            "Android Open Source Project (android.support.v7.lint.appcompat)",
            vendor.vendorName
        )
        assertEquals(
            "https://issuetracker.google.com/issues/new?component=192731",
            vendor.feedbackUrl
        )
    }

    override fun getDetector(): Detector? {
        fail("Not used in this test")
        return null
    }

    private fun getSingleRegistry(client: LintClient, file: File): JarFileIssueRegistry? {
        val list = listOf(file)
        val registries = JarFileIssueRegistry.get(client, list, null)
        return if (registries.size == 1) registries[0] else null
    }

    private fun createClient(loggedWarnings: StringWriter): LintClient {
        return object : TestLintClient() {
            override fun log(exception: Throwable?, format: String?, vararg args: Any) {
                if (format != null) {
                    loggedWarnings.append(String.format(format, *args) + '\n')
                }
            }

            override fun log(
                severity: Severity,
                exception: Throwable?,
                format: String?,
                vararg args: Any
            ) {
                if (format != null) {
                    loggedWarnings.append(String.format(format, *args) + '\n')
                }
            }
        }
    }

    private fun fail(): Nothing {
        error("Test failed")
    }

    fun testNewerLintOk() {
        // Tests what happens when the loaded lint rule was compiled with
        // a newer version of the lint apis than the current host, but the
        // API accesses all seem to be okay
        val root = Files.createTempDirectory("lintjar").toFile()

        lint().files(
            *apiStubs,
            bytecode(
                "lint.jar",
                source(
                    "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
                    "test.pkg.MyIssueRegistry"
                )
            ),
            bytecode(
                "lint.jar",
                kotlin(
                    """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = emptyList()
                        override val api: Int = 10000
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
                ).indented(),
                """
                    test/pkg/MyIssueRegistry.class:
                    H4sIAAAAAAAAAJ1UW08bRxT+Zn1bb0xYHELAhMYkbWKckDX0HhNSAkm7qqEV
                    pFYrnhZ76o693kU7Y1f0iV/RH1D1sQ+tVJSqlSqUx/yoqmfXSwEDEunDzplz
                    +c75Zs6cff3Pn38DWITNMKm4VNZup2Wt79lS9vgmbwmpgr0MGIPV8LuW4zUD
                    XzQt5fuutFzhKavhCk7C2RXWECjBkBahSTKYtbbTd6yeEq5VI3+V4fGQaal2
                    QYUmV7yh/OC4RnWZ8HdqftCy2lztBI7wJCE9XzlK+LTf8NVGz3UpKtviyo5J
                    5EtzZ2k8OWN8MyI5pJDOQkOOIam+E1SnULvoJqlegsAMjO47TdRWQiVRmrNz
                    MDEWphknR1d4kSMkvx7tc5gYuCfJ3ede0w8YyhcRPdGTehQbX0Q9Bj6gM18a
                    mkMB02Hpt6j0kvCEWo4o13MoYtZAErcZih1fEZ4eievSHUVNWD3ef64yeJs4
                    8O6u2gvvOIe7MAy8g3sMpctSyWCOuK8MIotf7HKvuOX3ggYvfhn4bSr1qFgj
                    YPEr4lh8QS2QOu5TVWpc3/LoPeiYZ/ihNOi363gta0sFwmtV/5/FrsWnbve7
                    FhXmgee41hr/1um5apWOrYJe+F7WnaDDg+rgxiwDZVQYxo7A61w5TUc51COt
                    20/QNLJwSYaLRradyIZwoYliHTI1Fxh+PtwvGdqkNvh0+kydZIJkMbaljnyT
                    h/uLWoU9Tb36Ka2Z2ua4mSholeTXr35cI4tuHO4XknrKTG8WzExBzyfzWiVb
                    0cmdPHYb5hXC5c7iRgg3bl4lx+hphGmOhVwXGRYu8dqG5wQM999gEGm6h2bt
                    YUdR77dEy3NUL+AM05s9T4kut72+kGLH5SvHfwya3VW/SUGj9IL4Rq+7w4MX
                    DsVQ3prfcNy6E4hQj43G4OE9F6EyFSeun0mLBZqbZNhN5MP/BGlPw7Yig1WS
                    aTqkTjIfDn8kJ2JJI0e+kzEpklkY9A7WSLNIY6G1/DtGfo1SPouDQcmf05ob
                    BOAqRkkOyl45lZLOhmu0Die8/stQQv2chAw3zgVPDYOz54JvYoaihsG3ho9i
                    nAM+fQT6B8UsvqdSYdx8+SXulP9A6QAj5bF75gGul83MAabKf6H8Tf4BY/mH
                    Zpq9xMIBbv32X8G7BB8kTlO7MnRXOt1PFpNEYoa0WUQ/rROE5mNCGj6N1hV8
                    RrJNvkUi++42Ejbes/G+jQ/w4ZH2kY2P8Yi2qG6DSSzh8TbyEqMSyxKzEqbE
                    Exlarkmkov0nElkJQ2JC4oZEQWJa4qbEzL/b/KBivgcAAA==
                    """,
                """
                    META-INF/main.kotlin_module:
                    H4sIAAAAAAAAAGNgYGBmYGBgBGIWIGYCYgYuYy7F5PxcvcS8lKL8zBS9kvz8
                    nGK9nMy8Er3knMxUIJVYkCnE5wxmxxeXlCYVe5coMWgxAAANsEImTQAAAA==
                    """

            )
        ).testModes(TestMode.DEFAULT).createProjects(root)

        val lintJar = File(root, "app/lint.jar")
        assertTrue(lintJar.exists())

        lint().files(
            source( // instead of xml: not valid XML below
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
            ).indented()
        )
            .clientFactory { createGlobalLintJarClient(lintJar) }
            .testModes(TestMode.DEFAULT)
            .allowObsoleteLintChecks(false)
            .issueIds("MyIssueId")
            .run()
            .expectClean()
    }

    fun testNewerLintBroken() {
        // Tests what happens when the loaded lint rule was compiled with
        // a newer version of the lint apis than the current host, and
        // referencing some unknown APIs
        val root = Files.createTempDirectory("lintjar").toFile()

        lint().files(
            *apiStubs,
            bytecode(
                "lint.jar",
                source(
                    "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
                    "test.pkg.MyIssueRegistry"
                )
            ),
            bytecode(
                "lint.jar",
                kotlin(
                    """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = emptyList()
                        override val api: Int = 10000
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
                ).indented(),
                """
                    test/pkg/MyIssueRegistry.class:
                    H4sIAAAAAAAAAJ1UW08bRxT+Zn1bb0xYHELAhMYkbWKckDX0HhNSAkm7qqEV
                    pFYrnhZ76o693kU7Y1f0iV/RH1D1sQ+tVJSqlSqUx/yoqmfXSwEDEunDzplz
                    +c75Zs6cff3Pn38DWITNMKm4VNZup2Wt79lS9vgmbwmpgr0MGIPV8LuW4zUD
                    XzQt5fuutFzhKavhCk7C2RXWECjBkBahSTKYtbbTd6yeEq5VI3+V4fGQaal2
                    QYUmV7yh/OC4RnWZ8HdqftCy2lztBI7wJCE9XzlK+LTf8NVGz3UpKtviyo5J
                    5EtzZ2k8OWN8MyI5pJDOQkOOIam+E1SnULvoJqlegsAMjO47TdRWQiVRmrNz
                    MDEWphknR1d4kSMkvx7tc5gYuCfJ3ede0w8YyhcRPdGTehQbX0Q9Bj6gM18a
                    mkMB02Hpt6j0kvCEWo4o13MoYtZAErcZih1fEZ4eievSHUVNWD3ef64yeJs4
                    8O6u2gvvOIe7MAy8g3sMpctSyWCOuK8MIotf7HKvuOX3ggYvfhn4bSr1qFgj
                    YPEr4lh8QS2QOu5TVWpc3/LoPeiYZ/ihNOi363gta0sFwmtV/5/FrsWnbve7
                    FhXmgee41hr/1um5apWOrYJe+F7WnaDDg+rgxiwDZVQYxo7A61w5TUc51COt
                    20/QNLJwSYaLRradyIZwoYliHTI1Fxh+PtwvGdqkNvh0+kydZIJkMbaljnyT
                    h/uLWoU9Tb36Ka2Z2ua4mSholeTXr35cI4tuHO4XknrKTG8WzExBzyfzWiVb
                    0cmdPHYb5hXC5c7iRgg3bl4lx+hphGmOhVwXGRYu8dqG5wQM999gEGm6h2bt
                    YUdR77dEy3NUL+AM05s9T4kut72+kGLH5SvHfwya3VW/SUGj9IL4Rq+7w4MX
                    DsVQ3prfcNy6E4hQj43G4OE9F6EyFSeun0mLBZqbZNhN5MP/BGlPw7Yig1WS
                    aTqkTjIfDn8kJ2JJI0e+kzEpklkY9A7WSLNIY6G1/DtGfo1SPouDQcmf05ob
                    BOAqRkkOyl45lZLOhmu0Die8/stQQv2chAw3zgVPDYOz54JvYoaihsG3ho9i
                    nAM+fQT6B8UsvqdSYdx8+SXulP9A6QAj5bF75gGul83MAabKf6H8Tf4BY/mH
                    Zpq9xMIBbv32X8G7BB8kTlO7MnRXOt1PFpNEYoa0WUQ/rROE5mNCGj6N1hV8
                    RrJNvkUi++42Ejbes/G+jQ/w4ZH2kY2P8Yi2qG6DSSzh8TbyEqMSyxKzEqbE
                    Exlarkmkov0nElkJQ2JC4oZEQWJa4qbEzL/b/KBivgcAAA==
                    """,
                """
                    META-INF/main.kotlin_module:
                    H4sIAAAAAAAAAGNgYGBmYGBgBGIWIGYCYgYuYy7F5PxcvcS8lKL8zBS9kvz8
                    nGK9nMy8Er3knMxUIJVYkCnE5wxmxxeXlCYVe5coMWgxAAANsEImTQAAAA==
                    """
            ),
            bytecode(
                "lint.jar",
                kotlin(
                    """
                package test.pkg
                class Helper : com.android.tools.lint.detector.api.DeletedInterface {
                }
                """
                ).indented(),
                """
                    test/pkg/Helper.class:
                    H4sIAAAAAAAAAJ1Qy04bMRQ9nrxgSJvwDqUUlsACQ4TY8JCgCHWkAFKpsmHl
                    zBgwmdjR2GGdb+EPWCGxQBHLfhTieiibLpHlY59zr61zz9/Xp2cATaww1Jy0
                    jve71/yXTPsyq4Ax1G/FneCp0Nf8vHMrY1dBgWE7Nj0udJIZlXBnTGp5qrTj
                    iXTUYjIu+oofy5RoEmknsysRywpKDOU9pZU7YCisrrWrqGAsRBHjDEV3oyzD
                    ZOs/F7te6xpH//NT6UQinCAt6N0VyDjzUPQQkNbJNXggk6xLUrLFsDQahmHQ
                    CPI9GjZGw2awyY5KL/floO7fJU2GndZnZiIn4+8+N7qOhvhpEklJtpSWZ4Ne
                    R2Z/RCclZaplYpG2RaY8/yeGF2aQxfJEebLwe6Cd6sm2soqqh1obJ5wy2mIL
                    AWVE2fmBaVFohIvEeM6B0vojwgefAr4TlnMxwBJh9b0BE3Tz2o8cv2GZzn2q
                    faHa10sUItQi1CNMYuqDTUeYwSxdMXcJZjGPBpUsqhYLFmNv+k/OETwCAAA=
                    """
            ),
        ).testModes(TestMode.DEFAULT).createProjects(root)

        val lintJar = File(root, "app/lint.jar")
        assertTrue(lintJar.exists())

        lint().files(
            source( // instead of xml: not valid XML below
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
            ).indented()
        )
            .clientFactory { createGlobalLintJarClient(lintJar) }
            .testModes(TestMode.DEFAULT)
            .allowObsoleteLintChecks(false)
            .issueIds("MyIssueId")
            .run()
            .expectContains(
                """
                lint.jar: Warning: Requires newer lint.

                Lint found an issue registry (test.pkg.MyIssueRegistry)
                which was compiled against a newer version of lint
                than this one.

                This often works just fine, but some basic verification
                shows that the lint check jar references (for example)
                the following API which is not valid in the version of
                lint which is running:
                com.android.tools.lint.detector.api.DeletedInterface

                To use this lint check, upgrade to a more recent version
                of lint.

                Version of Lint API this lint check is using is 10000.
                The Lint API version currently running is 10 (7.0+). [ObsoleteLintCustomCheck]
                0 errors, 1 warnings"""
            )
    }

    fun testIncompatibleRegistry() {
        val root = Files.createTempDirectory("lintjar").toFile()

        lint().files(
            *apiStubs,
            bytecode(
                "lint.jar",
                source(
                    "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
                    "test.pkg.MyIssueRegistry"
                )
            ),
            bytecode(
                "lint.jar",
                kotlin(
                    """
                    package test.pkg
                    import com.android.tools.lint.detector.api.*
                    class Incompatible1 : DeletedInterface {
                    }
                    """
                ).indented(),
                """
                    test/pkg/Incompatible1.class:
                    H4sIAAAAAAAAAJ1QTW8TMRSct5uPsgSaFkhToJQj7aFuIsSFD6kFIa20gFRQ
                    Lj05u6a42djR2sk5v4V/wAmJA4p67I+qeN7CAYkTWnnsmXn2vnmXVz9+Ahji
                    MaHnlfNiNjkTqcntdCa9Hpdq0AYRuudyIUUpzZn4MD5XuW8jJjzlMiFNUVld
                    CG9t6USpjReF8lxiKyFnWrxRJdMiNV5Vn2Wu2mgSWi+00f4VIX6yN+qgjbUE
                    DdwgNPwX7Qj97N/NPCdsZBPr+TfinfKykF6yFk0XMcegAI0AEWvjWkMA7pUm
                    LBUDws5qmSRRP6rXatlfLYfRIR03L762om64VwwJz7L/icaddP9q92DiOdJr
                    WyjCeqaNej+fjlX1SbJJ2MxsLsuRrHTgv8Xko51XuXqrA9k+mRuvp2qkXXjv
                    yBjr+WlrHAaIeGI8yZCbPx4h4wNmouZAc/87km9hGHjI2KrFGDuMnesC3ORT
                    8B/VeB+7vL9k7xZ7t08Rp1hPORI2sPmH3UlxF/f4iN4pyGELfbYcOg7bDmu/
                    ANjxTV9RAgAA
                    """,
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
                            context.report(ISSUE, Location.create(context.file), "My message")
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
                """
                test/pkg/MyDetector.class:
                H4sIAAAAAAAAAJ1W6VLbVhT+rgDLFg5xTEIgK00gMSGxDFmaxjQtIRBEDaSY
                kFLapsIWRqDFlWQKXem+L8/QJ2hIJ2GamQ7Tn32FvkZ/d3qubHZPR854fJej
                e8757lnvX//+/geAXvzM0OxpricXFwvy6MptzdNynu2IYAwXc7Ypq1besfW8
                7Nm24cqGbnlyvnJIVou6vM1Rx3AlCMe4N685Q7qhZXOqZWnE2cAQL2hef7Fo
                6Dl11tD4V5fhSKIrs6AuqXLJ0w150CqZWc1LM9yqRu/LBFGezdlFLX2ThJzN
                2E5BXtC8WUfVLZc4LdtTPd2m9ZjtjZUMg051BRYqopGhYXxyeHCCobsGMFEc
                QFMEURxkaPKvZahWwb+WiEMMh/ZdVUQzg2DPMXQmMrs50tUsFsURtEg4jKMM
                RyvEpD2X8PUnfchdYbQxnFq0PYIpLyyZMqHVHEs1ZMXyHDKQnnNFHGc4npvX
                cosVCw0uFx3NdcloU6pR0hjO7wQ0PrtAl03voGS5qEK6ayqKkzgl4QROM9R7
                8zr3dqZKIJIL6pySxZBKBDLpgE2wlz3SwCDmypswOkn6Tth3VUc1ic2J4nwZ
                RoJ8p2Sz9waD+k5x3RL33QV0RyDgYsB0ydg5P8ZEJBkiA7ZZVC3aMlwPpHWT
                vWOLkyCk0BOBjF6CXoOJRFwh089RplHYlV2k2zLPPBJ5Dc9HcBXXGa49GywR
                NxhCOUdTPZJ/M7FbQVdNlyU8fXhRQho3GaTRlXaTQk4taGG8TNAdrWg7Xkde
                m1NLhsfwd22BUoOzawO9P+qD8RNlSF9OK/vziGfNLQxI5JjbDJdquiY5o0+3
                dI8sWJfggu5gWEI9FJ53O7J+ZMkc0jUjTwzhvpxRYTldLTV3xCAVqU0ho5qn
                5lVPJZpgLtVRk2F8qOeDQLRZnwY+UMNgi0TK9zDMbayek4RWYfsf3l5vrEpC
                rDz5u9aN1fbGXiHF6C/cYI23Gv78JSTE6kaaYvXHwvH6uJAKpRqGW0ZiMfGY
                kAr3hmIRmqXhFq6N8iQZyHY7atC1QAx7OxsxNldtYGA4+b8WFfEWQ08iU60e
                3y5HOnnW9ZwS5xtVnUXSVnbr2xIeQOVVYZQXplzAHuaHuAhK1qvBU2JPGSpE
                MId5qmujK/5nJR/GQjllsyXTVJ2VMAyGw7Sfoyrcri0XKb79dEmGYQUsoQNU
                Ugq2syKiSAUso4xNBnXoJieBdeBG8A68gDqz2pLm6B7pXKK+cr9/YkwZuxNU
                7SYzqV3GSgTv4j2G3kBGNouGZmqWV+kZH+xpsAOG6rrpKiFWDoWPJHyIjxku
                P4NDRXzC8E+iShULQqnJG0ptVgwWnrssF7DhbPb0Pnwm4VN8znBgOzmTixQs
                xydKlqebmmIt6a5OL9X+7VcjBeOAnacEOkg1XBsrmbOaM8lfs/S65V3BmFId
                ne8rxEhWL1Dwlxxad+yVu/VC2aUgqvCy4nudv4+lrF1ycv5rmaGtImJqHzD0
                UB2op5oTormNv1ho/RUVYIn2r/gziE4Fo0IP0cxpnEdEmIr117S7QTOVLsTW
                ENtAfB2t0/Fj62j/lVd2fFPhA53/lsZo+Syewxmav/PPRHZJpyc4OojKZU+R
                HoHmU93xc+vo6l7DpTVc7v4NLzzGS/F+FguzdQw+3NLEkYfAH5EHfG0tZe6K
                Nr7qxBBp+J7WIttSSE2PRq7wMpgPsOHCE4zslltGGNpxi4aK3DAyWwJMNPqa
                rj7Fg2n2BLOPkF+DHl+Mm3F7DaWYtIb3n+LD6biwZa8nWH2MLx7h0kMfDVd4
                jozSiENoQpyM1UzjYfodwVG6UhuNJ9CKJH2vww8+PEbFXSBXteFHH/SX+Inm
                14g+RjDHZ1Cn4K6CVxVMILu5m1RwD1O0xP0ZMJeOT88g6uKMi9ddzLgYdiG6
                eMNFk4uzLt500eliyEXqP03yDqIoDgAA
                """,
                """
                test/pkg/MyDetector＄Companion.class:
                H4sIAAAAAAAAAJVSTU8UQRB93bNfjIsM4AegiB+ooAkNnDQYEl00mWTBBHRj
                wsH07rbY7Ew3me4l8bYn/R/+A08mHsyGoz/KWDMsciEmXqq63qtX1VXdv37/
                +AlgHUsM8145L456B2L705byquNtttiw6ZE02poqGEN0KI+lSKQ5EK/bh5RR
                RcBQeaaN9psMwdJyq44yKiFKqDKU/EftGBaa/6y8wbC21OxZn2gjDo9ToY1X
                mZGJ2FIfZD/xDWucz/q5altmPZVtLLdC8LzD9GLnnHyfFizDyv9VY5g8E2wr
                L7vSS8J4ehzQalhuSrnhhLULDLmhwVmPoO4aw+ZwEIV8hoc8Gg5CXuN5UDv5
                HMwMB+t8lb2o1vjJ1wqP+G4UBXN8tfSk8u7kSynHwuEgr7LOqBfK8d7e25cM
                j5sdmwppupnVXeGtTZyg+3nRHa1PyCMtYuf6iq46fcF+q7jFMPZ3yQzj59xK
                z9PjNGxXMUw0tVE7/bStsjeynRAy1bQdmbRkpvN4BNZjY1TWSKRzip403LP9
                rKNe6Zyb3e0br1PV0k5T8nNjrJeemjqs0TOV8o2R5/nPoBnvUiTI08AoP/qO
                2reCvke2UoATWCRbP03AGEIgotXg0kj8lDwfiesXi6+dJpyKi9M4LhMf4D5F
                YVFgAbcxiweF/g4ekm8QPkG50T6CGJMxpmJM48pZdDWmutfpiJl9MEfiuX2U
                HUKHGw4Vh5sO838A/VBd0VIDAAA=
                """,
                """
                test/pkg/MyIssueRegistry.class:
                H4sIAAAAAAAAAJ1UXU8bVxA9d/3tGFgMIWCS4IQ0MU6aBfodk6QEknYbAxUk
                qBVPC964C+tdtPfaEn3iV/QHVH3sQ1sVpWqlCuUxP6rqWXspYINE+rB37p2Z
                M3PuzOx9+8+ffwOYxYrAqLKlMnZ36sbSnill0161645UwV4KQsDY8huG5dUC
                36kZyvddabiOp4wt17EprF3H6ALFBJJOqJICenXballGUzmuUaW9IvCwSzVX
                PSdDzVb2lvKD4xyVR8RPVv2gbmzbajOwHE8S6fnKUo7P/bKvlpuuS69M3VZm
                RCJfmuql8bhH+W5EckggmYGGnEBcfecwT6F6XiWZL0awgDBZHFKbDw+x0pSZ
                g47BMMwwDQ3HaxtC8kvtfQ4jHfMozS3bq/mBQPk8oid6st72jQqxHgHv8c4X
                huZQwHiY+jpTzzmeox61Ka/nUMSNLOK4KTB04saLUZlSuCWQMNfWXj4VuPsO
                Rc3hNu5k8B5KAsUdX9GP0+e69Gt3d+F4/1ylUCYvl+VdeSVwq9Rppmt5dWNl
                c5tulZ6m53AP72dxF/cFShetQwrTLNx8x7O4smt7xTW/GWzZxa8DP8zzoFgl
                sPiSBSq+YDVkGrMsOy/YMjwOYxofCnx/kt+aChyvXvl/GrMaVWa71TCY2A48
                yzUW7VdW01ULLI0KmmFdl6xgxw4qnXZ9nMUMPhEYPAIv2cqqWcrigGiNVoxP
                gQiXeLho1G22dQgX/s5ih6rajMBPh/ulrDaqdb40Pz1NGaMsRrrEkW30cH9W
                mxZPEm9+TGq6tjqsxwradPybNz8sUpPOHu4X4umEnlwt6KlCOh/Pa9OZ6TTN
                8WNzVr9EXK4X10fcsN5Pw8BphK4PhlzZgZkLjHr3TwqBvuNBvr+j2Mg1p+5Z
                qhnYAuOrTU85Ddv0Wo50Nl17/vjt4Suw4NfoNMBxsJebjU07eGHRh+9P1d+y
                3HUrcMJzpMx2puiZEx7GosDrPWHZN42/GluDfPji8PRl2COkYFImyThNmQ+f
                kbYciSR/XtpO+iQoM8iyqV/xZPAkQm35d/T90g75PHIGrqHKNddxQD8GKDtp
                L50KybthiGt3wMs/dwW8fkZAgStngse6wRNngq+SpdYDnui+SvEM8Okr8DWL
                WOyxPqGfUX6NyfJvmPoDxgH6ynrmAJfLeuoAY+W/MPNt/gMh8h/pSfEanx5g
                4tf/Ut5mAJBZyC5Ofv28+DD5F5hkgmkmcROlU5SMiJKGpfb6BZYpt2n7jHQf
                bCBmomJizsRDPDo6PTbxOea5xZMNCIkFLG4gLzEg8VTihoQu8UyGmiGJRHt/
                RyIjkZUYkbgiUZAYl7gqce1fPTVL2woIAAA=
                """,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGIWIGYCYgYuYy7F5PxcvcS8lKL8zBS9kvz8
                nGK9nMy8Er3knMxUIJVYkCnE5wxmxxeXlCYVe5coMWgxAAANsEImTQAAAA==
                """
            )
        ).testModes(TestMode.DEFAULT).createProjects(root)

        val lintJar = File(root, "app/lint.jar")
        assertTrue(lintJar.exists())

        lint().files(
            source( // instead of xml: not valid XML below
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">LibraryProject</string>
                <<<<<<< HEAD
                    <string name="string1">String 1</string>
                =======
                    <string name="string2">String 2</string>
                >>>>>>> branch-a
                    <string name="string3">String 3</string>

                </resources>
                """
            ).indented()
        )
            .clientFactory { createGlobalLintJarClient(lintJar) }
            .testModes(TestMode.DEFAULT)
            .allowObsoleteLintChecks(false)
            .issueIds("MyIssueId")
            .run()
            .expectContains(
                """
                lint.jar: Warning: Library lint checks out of date.

                Lint found an issue registry (test.pkg.MyIssueRegistry)
                which was compiled against an older version of lint
                than this one.

                This often works just fine, but some basic verification
                shows that the lint check jar references (for example)
                the following API which is no longer valid in this
                version of lint:
                com.android.tools.lint.detector.api.DeletedInterface

                Recompile the checks against the latest version, or if
                this is a check bundled with a third-party library, see
                if there is a more recent version available.

                Version of Lint API this lint check is using is 9.
                The Lint API version currently running is 10 (7.0+). [ObsoleteLintCustomCheck]
                0 errors, 1 warnings"""
            )
            .expectMatches(
                """
                .*/app/lint\Q.jar: Warning: Library lint checks out of date.

                Lint found an issue registry (test.pkg.MyIssueRegistry)
                which was compiled against an older version of lint
                than this one.\E"""
            )
    }

    private val apiStubs = arrayOf<TestFile>(
        kotlin(
            "src/detector_stubs.kt",
            """
            @file:Suppress("unused", "UNUSED_PARAMETER", "PackageDirectoryMismatch")
            package com.android.tools.lint.detector.api
            import com.android.tools.lint.client.api.*
            import java.io.File
            import java.util.*
            enum class Severity { FATAL, ERROR, WARNING, INFORMATIONAL, IGNORE }
            data class Category  constructor(
                val parent: Category?,
                val name: String,
                private val priority: Int
            ) {
                companion object {
                    @JvmStatic fun create(name: String, priority: Int): Category =
                        Category(null, name, priority)
                    @JvmField val LINT = create("Lint", 110)
                }
            }
            class LintFix
            open class Location protected constructor(
                val file: File,
                val start: Position?,
                val end: Position?
            ) {
                var message: String? = null
                var clientData: Any? = null
                open var visible = true
                open var secondary: Location? = null
                var source: Any? = null
                fun isSelfExplanatory(): Boolean = false
                fun isSingleLine(): Boolean = false
                companion object {
                    @JvmStatic
                    fun create(file: File): Location = Location(file, null, null)
                }
            }
            open class Context(
                @JvmField val file: File,
                private var contents: CharSequence? = null
            ) {
                fun report(incident: Incident): Unit = error("Stub")
                @JvmOverloads
                open fun report(
                    issue: Issue,
                    location: Location,
                    message: String,
                    quickfixData: LintFix? = null
                ) {
                    error("stub")
                }
            }
            abstract class Detector : FileScanner {
                open fun run(context: Context) {}
                override fun beforeCheckFile(context: Context) { }
                override fun afterCheckFile(context: Context) { }
            }
            interface FileScanner {
                fun beforeCheckFile(context: Context)
                fun afterCheckFile(context: Context)
            }
            class Implementation @SafeVarargs constructor(
                detectorClass: Class<out Detector?>?,
                scope: EnumSet<Scope>?,
                vararg analysisScopes: EnumSet<Scope>?
            ) {
                constructor(detectorClass: Class<out Detector?>?, scope: EnumSet<Scope>?) : this(
                    detectorClass,
                    scope,
                    Scope.EMPTY
                )
            }
            class Incident(val issue: Issue, location: Location, message: String)
            class Issue {
                companion object {
                    @JvmStatic
                    fun create(
                        id: String,
                        briefDescription: String,
                        explanation: String,
                        category: Category,
                        priority: Int,
                        severity: Severity,
                        implementation: Implementation
                    ): Issue {
                        error("Stub")
                    }

                    fun create(
                        id: String,
                        briefDescription: String,
                        explanation: String,
                        implementation: Implementation,
                        moreInfo: String? = null,
                        category: Category = Category.LINT,
                        priority: Int = 5,
                        severity: Severity = Severity.WARNING,
                        enabledByDefault: Boolean = true,
                        androidSpecific: Boolean? = null,
                        platforms: EnumSet<Platform>? = null,
                        suppressAnnotations: Collection<String>? = null
                    ): Issue {
                        error("Stub")
                    }
                }
            }
            interface OtherFileScanner : FileScanner {
                fun getApplicableFiles(): EnumSet<Scope>
            }
            enum class Platform {
                ANDROID;
                companion object {
                    @JvmField
                    val ANDROID_SET: EnumSet<Platform> = EnumSet.of(ANDROID)
                    @JvmField
                    val UNSPECIFIED: EnumSet<Platform> = EnumSet.noneOf(Platform::class.java)
                }
            }
            abstract class Position {
                abstract val line: Int
                abstract val offset: Int
                abstract val column: Int
            }
            enum class Scope {
                RESOURCE_FILE, RESOURCE_FOLDER, ALL_RESOURCE_FILES, JAVA_FILE, CLASS_FILE,
                MANIFEST, JAVA_LIBRARIES, OTHER;
                companion object {
                    @JvmField val ALL: EnumSet<Scope> = EnumSet.allOf(Scope::class.java)
                    @JvmField val RESOURCE_FILE_SCOPE: EnumSet<Scope> = EnumSet.of(RESOURCE_FILE)
                    @JvmField val JAVA_FILE_SCOPE: EnumSet<Scope> = EnumSet.of(JAVA_FILE)
                    @JvmField val CLASS_FILE_SCOPE: EnumSet<Scope> = EnumSet.of(CLASS_FILE)
                    @JvmField val EMPTY: EnumSet<Scope> = EnumSet.noneOf(Scope::class.java)
                }
            }
            """
        ),
        kotlin(
            "src/client_stubs.kt",
            """
                @file:Suppress("unused")
                package com.android.tools.lint.client.api
                import com.android.tools.lint.detector.api.*
                const val CURRENT_API = 10
                data class Vendor
                @JvmOverloads
                constructor(
                    val vendorName: String? = null, val identifier: String? = null,
                    val feedbackUrl: String? = null, val contact: String? = null
                )
                abstract class IssueRegistry
                protected constructor() {
                    open val api: Int = -1
                    open val minApi: Int
                        get() {
                            return api
                        }
                    abstract val issues: List<Issue>
                    open val vendor: Vendor? = null
                }
            """
        ).indented(),
        // The following classes are classes or methods or fields which don't
        // exist. This is so that we can compile our custom lint jars with APIs
        // that look like lint APIs but aren't found by the verifier
        kotlin(
            """
            package com.android.tools.lint.detector.api
            interface DeletedInterface
            enum class TextFormat {
                RAW, TEXT;
                fun deleted() { }
                @JvmField val deleted = 42
            }
            """
        ).indented()
    )
}

fun createGlobalLintJarClient(lintJar: File) =
    object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
        override fun findGlobalRuleJars(): List<File> = listOf(lintJar)
        override fun findRuleJars(project: Project): List<File> = emptyList()
    }

fun createProjectLintJarClient(lintJar: File) =
    object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
        override fun findGlobalRuleJars(): List<File> = emptyList()
        override fun findRuleJars(project: Project): List<File> = listOf(lintJar)
    }
