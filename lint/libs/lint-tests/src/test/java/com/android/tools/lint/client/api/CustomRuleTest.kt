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

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.lint.checks.infrastructure.TestFiles.classpath
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.toBase64gzip
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Project
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files

class CustomRuleTest {

    @Test
    fun testProjectLintJar() {
        val expected = "" +
                "src/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [UnitTestAppCompatMethod]\n" +
                "        getActionBar();                    // ERROR\n" +
                "        ~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [UnitTestAppCompatMethod]\n" +
                "        startActionMode(null);             // ERROR\n" +
                "        ~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [UnitTestAppCompatMethod]\n" +
                "        requestWindowFeature(0);           // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarVisibility(true);    // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminate(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminateVisibility(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 6 warnings\n"
        lint().files(
                classpath(),
                manifest().minSdk(1),
                appCompatTestSource,
                appCompatTestClass)
                .allowObsoleteLintChecks(false)
                .client(object : TestLintClient() {
                    override fun findGlobalRuleJars(): List<File> = emptyList()

                    override fun findRuleJars(project: Project): List<File> = listOf(lintJar)
                }).customRules(lintJar).allowMissingSdk().run().expect(expected)
    }

    @Test
    fun testProjectLintJarWithServiceRegistry() {
        // Like testProjectLintJar, but the custom rules are loaded via
        // META-INF/services/*IssueRegistry loading instead of a manifest key
        val expected = "" +
                "src/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [UnitTestAppCompatMethod]\n" +
                "        getActionBar();                    // ERROR\n" +
                "        ~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [UnitTestAppCompatMethod]\n" +
                "        startActionMode(null);             // ERROR\n" +
                "        ~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [UnitTestAppCompatMethod]\n" +
                "        requestWindowFeature(0);           // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarVisibility(true);    // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminate(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminateVisibility(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 6 warnings\n"

        lint().files(
                classpath(),
                manifest().minSdk(1),
                appCompatTestSource,
                appCompatTestClass)
                .allowObsoleteLintChecks(false)
                .client(object : TestLintClient() {
                    override fun findGlobalRuleJars(): List<File> = emptyList()

                    override fun findRuleJars(project: Project): List<File> = listOf(
                            lintJarWithServiceRegistry)
                }).customRules(lintJarWithServiceRegistry).allowMissingSdk().run().expect(expected)
    }

    @Test
    fun testProjectIsLibraryLintJar() {
        val expected = "" +
                "src/main/java/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [UnitTestAppCompatMethod]\n" +
                "        getActionBar();                    // ERROR\n" +
                "        ~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [UnitTestAppCompatMethod]\n" +
                "        startActionMode(null);             // ERROR\n" +
                "        ~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [UnitTestAppCompatMethod]\n" +
                "        requestWindowFeature(0);           // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarVisibility(true);    // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminate(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminateVisibility(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 6 warnings\n"
        lint().files(
                classpath(),
                manifest().minSdk(1),
                gradle(""
                        + "apply plugin: 'com.android.library'\n"
                        + "dependencies {\n"
                        + "    compile 'my.test.group:artifact:1.0'\n"
                        + "}\n"),

                appCompatTestSource,
                appCompatTestClass)
                .incremental("bin/classes/test/pkg/AppCompatTest.class")
                .allowDelayedIssueRegistration()
                .issueIds("UnitTestAppCompatMethod")
                .allowObsoleteLintChecks(false)
                .modifyGradleMocks { _, variant ->
                    val dependencies = variant.mainArtifact.dependencies
                    val library = dependencies.libraries.iterator().next()
                    Mockito.`when`(library.lintJar).thenReturn(lintJar)
                }.allowMissingSdk().run().expect(expected)
    }

    @Test
    fun `Load lint custom rules from locally packaged lint jars (via lintChecks)`() {
        // Regression test for https://issuetracker.google.com/65941946
        // Copy lint.jar into build/intermediates/lint and make sure the custom rules
        // are picked up in a Gradle project

        val expected = "" +
                "src/main/java/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [UnitTestAppCompatMethod]\n" +
                "        getActionBar();                    // ERROR\n" +
                "        ~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [UnitTestAppCompatMethod]\n" +
                "        startActionMode(null);             // ERROR\n" +
                "        ~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [UnitTestAppCompatMethod]\n" +
                "        requestWindowFeature(0);           // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarVisibility(true);    // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminate(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/main/java/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminateVisibility(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 6 warnings\n"

        // Copy lint.jar into build/intermediates/lint/
        val listener = object : LintListener {
            override fun update(driver: LintDriver,
                    type: LintListener.EventType,
                    project: Project?,
                    context: Context?) {
                if (type == LintListener.EventType.REGISTERED_PROJECT) {
                    val buildFolder = project?.gradleProjectModel?.buildFolder ?: return
                    val lintFolder = File(buildFolder, "intermediates/lint")
                    lintFolder.mkdirs()
                    lintJar.copyTo(File(lintFolder, lintJar.name))
                }
            }
        }
        lint().files(
                classpath(),
                manifest().minSdk(1),
                gradle(""
                        + "apply plugin: 'com.android.library'\n"
                        + "dependencies {\n"
                        + "    compile 'my.test.group:artifact:1.0'\n"
                        + "}\n"),

                appCompatTestSource,
                appCompatTestClass)
                .incremental("bin/classes/test/pkg/AppCompatTest.class")
                .allowDelayedIssueRegistration()
                .issueIds("UnitTestAppCompatMethod")
                .allowObsoleteLintChecks(false)
                .listener(listener)
                .allowMissingSdk()
                .run()
                .expect(expected)
    }

    @Test
    fun testGlobalLintJar() {
        val expected = "" +
                "src/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [UnitTestAppCompatMethod]\n" +
                "        getActionBar();                    // ERROR\n" +
                "        ~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [UnitTestAppCompatMethod]\n" +
                "        startActionMode(null);             // ERROR\n" +
                "        ~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [UnitTestAppCompatMethod]\n" +
                "        requestWindowFeature(0);           // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarVisibility(true);    // ERROR\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminate(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [UnitTestAppCompatMethod]\n" +
                "        setProgressBarIndeterminateVisibility(true);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 6 warnings\n"
        lint()
                .allowObsoleteLintChecks(false)
                .files(classpath(), manifest().minSdk(1), appCompatTestSource, appCompatTestClass)
                .client(object : TestLintClient() {
                    override fun findGlobalRuleJars(): List<File> = listOf(lintJar)

                    override fun findRuleJars(project: Project): List<File> = emptyList()
                }).customRules(lintJar).allowMissingSdk().run().expect(expected)
    }

    @Test
    fun testLegacyLombokJavaLintRule() {
        val expected = """
project0: Warning: Lint found one or more custom checks using its older Java API; these checks are still run in compatibility mode, but this causes duplicated parsing, and in the next version lint will no longer include this legacy mode. Make sure the following lint detectors are upgraded to the new API: googleio.demo.MyDetector [ObsoleteLintCustomCheck]
src/test/pkg/Test.java:5: Warning: Did you mean bar? [MyId]
        foo(5);
        ~~~~~~
0 errors, 2 warnings
"""

        //noinspection all // Sample code
        lint().files(
                classpath(),
                manifest().minSdk(1),
                java("" +
                        "package test.pkg;\n" +
                        "\n" +
                        "public class Test {\n" +
                        "    public void foo(int var) {\n" +
                        "        foo(5);\n" +
                        "    }\n"
                        + "}"))
                .client(object : TestLintClient() {
                    override fun findGlobalRuleJars(): List<File> = listOf(oldLintJar)

                    override fun findRuleJars(project: Project): List<File> = emptyList()
                })
                .issueIds("MyId")
                .allowMissingSdk()
                .allowObsoleteLintChecks(false)
                .allowCompilationErrors()
                .run()
                .expect(expected)
    }

    @Test
    fun testLegacyPsiJavaLintRule() {
        val expected = """
project0: Warning: Lint found one or more custom checks using its older Java API; these checks are still run in compatibility mode, but this causes duplicated parsing, and in the next version lint will no longer include this legacy mode. Make sure the following lint detectors are upgraded to the new API: com.example.google.lint.MainActivityDetector [ObsoleteLintCustomCheck]
src/test/pkg/Test.java:5: Error: Did you mean bar instead ? [MainActivityDetector]
        foo(5);
        ~~~~~~
1 errors, 1 warnings
         """

        lint().files(
                classpath(),
                manifest().minSdk(1),
                java("" +
                        "package test.pkg;\n" +
                        "\n" +
                        "public class Test {\n" +
                        "    public void foo(int var) {\n" +
                        "        foo(5);\n" +
                        "    }\n"
                        + "}"))
                .client(object : TestLintClient() {
                    override fun findGlobalRuleJars(): List<File> = listOf(psiLintJar)

                    override fun findRuleJars(project: Project): List<File> = emptyList()
                })
                .issueIds("MainActivityDetector")
                .allowMissingSdk()
                .allowCompilationErrors()
                .allowObsoleteLintChecks(false)
                .run()
                .expect(expected)
    }

    private // Sample code
    val appCompatTestSource = java("" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.v7.app.ActionBarActivity;\n" + "\n" +
                    "public class AppCompatTest extends ActionBarActivity {\n" +
                    "    public void test() {\n" +
                    "        getActionBar();                    // ERROR\n" +
                    "        getSupportActionBar();             // OK\n" + "\n" +
                    "        startActionMode(null);             // ERROR\n" +
                    "        startSupportActionMode(null);      // OK\n" + "\n" +
                    "        requestWindowFeature(0);           // ERROR\n" +
                    "        supportRequestWindowFeature(0);    // OK\n" + "\n" +
                    "        setProgressBarVisibility(true);    // ERROR\n" +
                    "        setProgressBarIndeterminate(true);\n" +
                    "        setProgressBarIndeterminateVisibility(true);\n" + "\n" +
                    "        setSupportProgressBarVisibility(true); // OK\n" +
                    "        setSupportProgressBarIndeterminate(true);\n" +
                    "        setSupportProgressBarIndeterminateVisibility(true);\n" + "    }\n" +
                    "}\n")

    private val appCompatTestClass = base64gzip("bin/classes/test/pkg/AppCompatTest.class", "" +
                    "H4sIAAAAAAAAAJVU21ITQRA9E0ICcRTkjqAogmwisuIF1AASolRRFS1LqKSK" +
                    "t0kyhSNhd92dhPJb/ApfYpUPfoAfZdmzhFzKxOg+nLmd7j7d07M/f33/AeAR" +
                    "XscRYZjSMtC2d3piZzwv6555Qh/RThxRBks4Zd9VZTuoep7ra7u2aQvPszMl" +
                    "rVxnT/hmUlP6M0NsSzlK7zAMWMk8QzTrlmUCAxjmGESMYSSnHPm2elaU/pEo" +
                    "ViTDWM4tiUpe+MqsG5tR/UEFDDO57qrShkFjAgyjHNcxxsBPpG4KYpi1krlL" +
                    "2R1a08ZogmMSUwzjZHR4kVSb7VKbba+UQzczHLO4QVkFWlx6eEMZM2xbTQ81" +
                    "Jc/t1tlSVlQqRVE6TSd7UULf8xw3cYsuJvTdIfIixKHVTeQ/ROvNDgPf5riD" +
                    "RaqRLz9VqcgF5ZTd830pdNU3V2MdJI8Nb4lj2fDmGv7ed6Eb3gqHhSQVK5D6" +
                    "ne+e+DIIqIB5FaiiqoRdE7WOk3nDvc+xarhzndwDpyy19M+UI3Toc43DNrzl" +
                    "v/BaAYzFOqdWJ4uFoHnjXcUY7hOOp4a72JX7h5hNjmeGvtqf3p504tCt+iW5" +
                    "r8JX0NHfax9FTVBLHziO9LMVEQQyiGOb6vMfNx7HS0qhvwHDUMtkj+6/X+PG" +
                    "8YphugeLeoceP8wXoRk9esI4rWwaGY2DqW8Y+hoeJwhjF5u4Qsgbc46rNDJc" +
                    "axp/CZ0BmVQdIwWC8UKK1TEd4lwhNVDHQoh3C6loHfcMpAw8MPDQwGMDG63A" +
                    "G2GQOIUbonUCoyRhivbmKewirVbot7KKMaxjgriTSNPpLqbbhGYaQiN4HuIL" +
                    "4oAsItjCDnZjw8TImvE3oCvXeGsFAAA=")

    companion object {

        @JvmField
        val LINT_JAR_BASE64_GZIP = "" +
                "" +
                "H4sIAAAAAAAAAJ1XeTTUbRse+/Lal8hWiAYzZJkyxtK8tjTW0XgTKo1RY2Jk" +
                "KWvZIrLEWEeEERHZl8RERHZibBlZssVYC9n6fMt73urr7Xznu895/vidc13X" +
                "/Tz3777PuS9zBAMjOwDAygrI09NHAA6CHvDvYDs4Jvrn4GAjUwMlBoD5d8Df" +
                "IUpCVgcAm4Mj8C3QBG5qZKBveU7RxGDdpKPdGAFW7OVEgOW7OrrLkCf6Vcen" +
                "11o7FHo5z5p0gxTl8vvBqqXIMx0IubZu414FRTDwjZJ8r4LqYDud/Ft3ENC1" +
                "qxt8BrQKUlQ0uYudmJ6cpv/hHt9emPXg2Ls4uOGxDv91329xPN/g3D1dXfFu" +
                "Hr/E8/8Ef/PULynCP6dcx7r8d6o64XA+q4NiTx7UUekXPLirqy7e2dXeQw/j" +
                "gUF74N0U0dft3d0T/ti50zcg+DW/BDxscZRV5MRdKaBkOIPy3dLEkDiRFvul" +
                "NUC7EbHSxvz6a95YGelW78U6z6JKkk2Vi4IHdaBuKNhZhkaPy3zXNuBC3Vnl" +
                "/nRnKMY3TQHWzJPruWuU7dutNTvq/nLWd6nOZ+FlUS3zuMQuocWDldoTrB51" +
                "WII5phZgGSCTcXQ8EmATwB90/wpPWTL/iSNB3L9Jbq028GiHcIet8PrFR/kb" +
                "17cw+f1ef5xJU3bckl4bPK6qqd3OoS10LLglpYF/cTU7oFt11ZrOjG2LHnfb" +
                "or6PnnxsvJ0+nXdFkO6S4AqErptd/fh4bcAso59d/Trcj+E9x6SNV2BLVfAW" +
                "F1k0y9NPKWR5KijgSPEmt/fuvZYY6BYhQiVuvAf6oGTNgtIxkpgeVZOIUbfQ" +
                "PUW9ZuVeEYkbtqjIMCREW/b7qBfA0s+YNU3Of1jpaSo5HxGs5nohDUUKdZrJ" +
                "isAtOEaXEycT56vz56PzDSymiuFGLUjBFw0T2sRbhN/TTbNDY3L5Il75mUTK" +
                "CnKl/nGPoPs4FoaL/CTzNrz/shswUTYGnNfUGhsSly5MCpwoCd/XA2nFZQXW" +
                "81L7qSQxkRGYAXRr0iox6IJORuqIubJO3jDW823P0IWCSKfI/MqubUxmd9qA" +
                "OHOfip5piXnx9VMavni23POZPFVrTBSaAoMBgBXaiWInJk4XF9WRFEO0PFWc" +
                "JS3dsHOHT96xhURKQoosGPmga8WKNTW84j3iSeXBEemqqKprltm+VkAx65SH" +
                "pA6gaiHGMRnHpVQ+KGg3ai4eVvrRad2lOwAzmzXwASuS3idLyifE1FTmJMSH" +
                "so6iIMyQWyNtA5lxauJ9lmi2KexeNJEks5aCZBXjKtC6XrkyzQpd9NlE8biv" +
                "XVQTW8pH6zclRIZhbdJwJKnyGeNOWV2Cm3F7WUVQpuMTSna1k6OjOdgLlF0X" +
                "tpUPk+vsccwrCEVYVVUVdeBbGpvI0odJHVsPrgjSIgeKVOWrCjKchMpmix+L" +
                "IGRgZbbk6Hu3xWZXLXl1Gu+LEfFp0bqGVEwD3KG/q7oGI2GZ9yJ9prg9CK50" +
                "3EasU8veBpq6ZiJPqiGZfQxdUnS9VucNxJZqvvNOe1OEWqCucvmUzw+NpGTN" +
                "VKNrUipmtiFFbl62fCX3krxqQZMMc9M9F9EWTpk8hYzdImVqj+49QhvpMVtL" +
                "JOnbNih51UpNomwFlRs0nR1E4a1GlDab2oT3x5ToihvznhQFNY4RRZ6+Klmg" +
                "Ug47ykSd43RVX7TKKcyKTbgfLVs1Vvcu1lMqLMcRgzvronV6j+/W02rlReVL" +
                "TZBa2UnWJaFVxO7ZpqJVDeW0vPBqLnl5MoeqoCMT9uQnUSRaUE90wy89eTkV" +
                "lpwZ/bUcnWLhaGWOZblf6iM9Fnf0qjU0mC1kukmZJpUASHC1gjh+PqxhbfDk" +
                "Uc5nVDL0vOKSszTXDWuJMrCPlcH09ERVSw6EkNjtMYo1kZV1OCsSqo19uvHq" +
                "omwlNmBfXKGo9bFdY6xQQdZifE9qv0Ml9MQR1JbyrRzMLEq30rhzPjYXZDMw" +
                "Q/Fr6Nloy3V6kFs3da4i2JIio4+2cyNGK6SgMhZpa6X21qrRs0mdy2+eTXdq" +
                "zIIrBa4JxbyP8GLAr22l3Xs/BR65uFAjUHxinTifDwJI3OFZNdwNpx2+M7Mq" +
                "xHvn9Xv1UR1azd0loHWXUyiqZ/FZ6TvFk/qtntAvO21eBuEBI36D5amn9HbF" +
                "vx4dGOTvioqaWbnQf8tXlBxnYYK3FMTPyw6/I5ToVpOWSA+TT6k9FInk7Pd9" +
                "fYEMUHuv3oiuLFTsKaCj2LdAaUG0GI7RC1e1IM+YCj3FT39Ilr8IeWgTHYd5" +
                "6RG/eGlBDlFhHL9Y9tk6dGyfoqTjocfrrulbcy/hoI5mnib+GkAEBGnK+4KR" +
                "os6Po1owWBYsAccaISArwy+Ox6PUYC/UVPbdhfGXaOMxJhrPCh1HRQeJNcCS" +
                "ReJdxB8M8rxc3ZRb9gp2Q4Mn29sEB66axd32H3uRf7eW0Hm4j4rVmHE6mupQ" +
                "kP/04ZTHyWlQKldJ85arfMK93LC5ye7Mp+TUS8Pb4sKe7p97Nxx8DWV1DiE4" +
                "ZcKiB2F9pRizuhe6oTfh8utbpyPJ6G0CQzibjLZfENnypJWg2yvQjGAUxc0G" +
                "EiHR7us0p7yyQByTZl6VQ+PGJr4WiRjlI0pzQMcGmTH+Ai/nt/LKshN1HEQp" +
                "12/2lFZkQzE35gqf4/qXuWHz/GRRMZ966Y4lTUDlJNDHaANQgdcN2C7J1zbb" +
                "Q6P8A7dX828/2PZH7fF85kftGay9KVmAar1HxAV/mpmjxdg2OydpJhzT0v3E" +
                "PDKuC5PITjyPfLVz4jSeK1hYHjYnD1sBZ028jvCvJ3haDW/LVzaqwy90bBwz" +
                "Gj5v4z4u4jfFiTx9eNOoVmUpiRGjeZQp4cZFdrmpoP4ntoFeT3euJexUlu4Q" +
                "0k0jHNjfPuZ5/lTA68GHwUZyUm8/jXvnsSfi/B2Jd437Cte0Y0zSEEM8G0rw" +
                "K8H4/WUaIin5Y/MSYLATEegaUN4tecz544zSZb1JhaB3KRlmfPJBh2TVgUwK" +
                "QZ3GxQh2FKNN8uXg/vOHVpDVqdzr+Ay7kY8vQ2HNoq/Kb19SP/3b2ADYQ/GK" +
                "79WhqhBYI31ViDhIl1OC0VJPmMfbS6qbOcs0+U1Z6pM+eGs0j76GHI0F2BsG" +
                "pNQx6upYpx2tb2YFJmQQc8RG4l8ztbWg1dYY4ZscdJughg1Ng7l2R4GiKUnu" +
                "SUM2sr4aoTyJ1fB0YoNjocXaG+/CsBNLJTMaEs3SAjI4rYhNy2hvW8ylM/Gh" +
                "DF5E/wlzeu4JuYdZh8x6A7K490Q1Y1rttrgr36buE+cH++GWfbeQUMLCG3Dq" +
                "4nW9uKTidbFd8n1ebNLgqEDsktNMUvGOptd7pi2Ra9kU0WGh2I145psG2ZVI" +
                "nLuek1IFUQrJgktr9O+wblJlM8zualsZe+uazLlMmljnkUYOru1yCilAvXn4" +
                "tFQF3BLZndlTH+gqSFKZ0lQ8iG4ndM+Ihlin95Rem2bySYfHqiizJK2ZN00l" +
                "vybEJ4j2ay/Ys3DnyoaInNI5feqYi2hVoLZtBkr++aOIxL2NMxbYlx6bUlxq" +
                "LIdurKBGVRW/CN2YUxlzZ9o8uwmeWEEM3W5V0D/0SUMJBR6iSwlyzhQw9YbE" +
                "N5rVRgXQTKnVwqz7R75f0VSy0S5JdADA+sF+p/a/rGhG7u6eGCTmKtbdw837" +
                "P3sa0g4hChfQIhlVGp2iGBQ69yk1hGUzGcJXJLPih4KKgWqFy4TO4mhjx0Sq" +
                "dcOdx2Sg9hbDrsXbF6lD8TmPq7k3x577dj2EXZ1Z2p85VC+r3W/e/Lvg/fP5" +
                "E0yJWrWfViIySwsj3WAcZYUbvLp5OJY4e0VzAwmHRrIx+T45x1S6OLPHUMri" +
                "i88o6Dk6QaWA5RUH31Nmzc7JIdnoRy43CiOnHk1V48JKXHewxFa8CjIP0aXB" +
                "QTkT7aOHLx9pCFcX1HQ/ux5wFJqRKyVtm/igY0ngMoyV9kDniQbOU4NP7Mxx" +
                "3av8ilde/xbYxUWzWhgGsU3/kWBG3d05h3seEsOuyV3kr7ba3GhzZHkl3Z52" +
                "g8Vh/GuyUv9Z5DaEGtK7X9Q0tnWdY3rv4nKLUKhZTHGgPXbSftDJBIDh/HLJ" +
                "LCG83GPgw4BKQaWBC85e6vaQjuTlqx9Di4P46p7sptK125G2QqweqpBs972Y" +
                "blrsPkMWCHTKOwns8QLM8ugpnprS50zHhAl1iA5b9MYud81XUapo3yx7h+bn" +
                "dCHU1HFzBB29AMPfO6A/LQMNDvjW5vxI+9EP/RnZgXJ/445+lZj1u8Ql33Tc" +
                "r1g837GGf9Knv2Lzf8dmo/tpl/9KQPg7Ac2fC/xrTH5U+dEB/VU8j1+o/I0f" +
                "+lH8x9n9S1zot/9vks0RTMz//kusANV/vp7zn1//AOTe4kZJDwAA"

        @JvmField
        val LINT_JAR_SERVICE_REGISTRY_BASE64_GZIP = "" +
                "H4sIAAAAAAAAAJ1XBzSc2xYevVy9RBkkCBE1WoLR5mqR0WVcgiQyZmIMRgyJ" +
                "GjWE6IwySvQWYnQRhBCiS3TRRQujJoiW565337qJm1jvvfOvs/71/+v7vn3O" +
                "XnvvtbcBhISUGgCgpAQAVDUggONFDPj3ojreuhrXwWLaepoSJACDH4Cl3sSr" +
                "wGMAx/Fm+R6oC9bT1tQwvi6uq7ml29mhAxETf0cLERPu7uwpM7rULz01NzNH" +
                "fELse6vHfwBWDtZOaKT1P4x+j2P4DodxcXREOzmfimf+Cf7+lVMp7D+n2CEd" +
                "/mmqjj2YyeTYYzPHzpA4hQd2dFRD2ztaOavDneEwZ7STOMzOCoPB/rHv3TfI" +
                "+q2gRGzE8Bwl56VHfEK8wSSSj0rjAqI5W61WNwEd2rhKcwO7N4xRAvxtbit1" +
                "LsWVmeZVDiLO44N1w/72AgRiVPqH9kGH8f0N+s/ewxEeKSKgFoZclwPtLI8e" +
                "pYUxzKsFj9U69+VXxbXkUzwHMa3OlOO9/nJhHDzkEbUAYx+BtHNToQBzH2a/" +
                "J3cYyhKYL531o/+Nd3ejkUE5gD5ondEzNsxLp6GVzPP3hgtkioJTxsTKYlPS" +
                "isodNMps5/1bExuZVzayfHqkN8yI9Kl2iVEPDRv6iOvPT3UQpzKusxLdYl2X" +
                "JeqhlrswVeuzQOpp2bAF9iSZpJkxd/VtrfLfpasHZrh4SgSszfr5nMXv0Lsd" +
                "PG6NkN+NCZGKnuqVjyzZNBzoHI1LDauJg8sZql0ZtzHBVISiRgwr0rRiwo37" +
                "3eUKQalX9Ztnlj6u9zaXmIb4yzjeSIFmBtrOZ4SglhHh5biZuKXqgqXwAk3D" +
                "WTxYu9WI9WXjtDLuQczvqXpZgRG5TCGvPXVDBVnpkv54HKOWHQVChX4WeB/c" +
                "f9tJKE4wQiy/uS0qIDqVPdN3uiT4SF1UKTrDt4FxvH88k4tzFKQpvztjEud3" +
                "QyUtadRAUiV/BOnyvnf4RmGobWhBZfcePL0nZZCbvE9KXa/EAG93RcEDTZVr" +
                "ms5QtUk2QBAh0QRQyndBqXFxc/jiukzxACUXKXteYyfkIsdlbwvZUF7ZYkNS" +
                "JvlNvHhNDSN3L3d8uX9IqjS0ysY4y8NEiMssMTmzU0i6CI5IQNFJlA+xWo4Z" +
                "cAeVfrLdcujxgS9kDH5Ecqb2CWYWxETUVOZgYwMpx6Cy5LIPRtsH06NluPuM" +
                "YVSzyMNwXKbAZqIRJRddoZJd5focpfyK+w6UAbN5U4ZrtQCm0YwNDUKap6Ay" +
                "+crndboE1WKcdDrKKvzSEXkDWdW2CISBmKtoVl3QbgHoYlcvIr8wEGJSVVXc" +
                "iW5taq7n58js3I28w0oIHSyWFq4qTLNlK1vAZ3NCBEBlFvXhjx9yLWwYM6o0" +
                "PeHCoVPC1bTG4Y1g6/7u6ho4j3H+y9R5fIcfWOKCOVeXkpW5fNKmrnBmTab+" +
                "p8BVcUebOjchZKniB7eUt8XQ5fENOvfypeHRxIz5alhNYsX8nmyxk6sFU8nj" +
                "eNda0RmSxbnemzBD23SGItIezjKZp4+fwrTVyc144jUsGiVca/lmoBasko2K" +
                "9tZAcJv2QLt5LXbyvAQRvik/r9ivaQLH+ex1yfL4AAdCIOw6raPciklOUUYU" +
                "9km4YNVE3YcoF76gHAQcdc1BSfWQ6cGzaskVyVvNsrWCM5SrbBuQg2vNxRsK" +
                "kin5wdV0wsL1NNKsCDLk5c9AIxirOnDbMzVhLQmUkB7+rRyWaIgwMUBSPCl1" +
                "55+IPnfXTN6fKmCuWZLAhwVgHU1kEV84FMw0857mfIEmyJuKr9rz090z4ykT" +
                "czfRnJubrmrNkY2J63EeQ+oKClpf4wxURj7bfn1TsBLpc8QtUtyWbdkUxVaY" +
                "sRLbm9RvXSl/6Sx0V/JBDnwBqlap07UUlStqPjg/4NnYu92eaxuZWzd7vcLf" +
                "eEBAA2bphAsXSYSmrRA2S63MpMMX4rvW3j6f61JYEKtksWGLmAxxJUFv7qY8" +
                "npwVG725XMOCv7SFWyoQBfB4M2xoHQQTOLznN9gYvd9Myo2pEGoerQqZddsG" +
                "QntXnpd+EL+s0eYi/3W/3VUz2GfUc6g86Yr6Afe3c4NDzN1hYfPrN/ofeADr" +
                "ow110cas6CXBkQ8xJWrVmauZyQlXZJI5Q2n7Pd7cqAfITMo1wSqLxHsLiQas" +
                "WuUJfoQImrEbd5Vkn5MVuXCrfkwQvimbbB4eDX/lHLtya/kipEIndqXsi1ng" +
                "xNGAhIqzOiNG0aPmMfbYj/ouul4KQhBZIz3Gl6QDcsyocUMS48JVoYkmWVET" +
                "ra+IC2EyoJcyUkcYdvQtwlSErsLzIsQYcAhXI1SygnsE+YNEmJGuZ+CBlYjl" +
                "8NDljnbWwbv60Q+9Jl4WPKqN6eLoG0cqzNueS7IuLHiWPOt8eU40ia6kZddR" +
                "GPs4N2hxpif9WX3SrZE9bnYXzJd329YeWoIqZyC0AkHhQ6C+Urh+3Uu1wPtg" +
                "4a1d1dB62F4MSTCVgLKnX73xZRNWp9ei86xhA07msiE8HR62i5Lry7gJfvKN" +
                "izDUxPS3Yk7tAkhpjuj5IXK4F8urpd38sqw4FWvggN393tKKLHn4vcWiF6j+" +
                "NXrQEnM9kMu9gb9zVRFQOSPkrr0NqECr+eyVFCjrH8KgXr57GwUPI/e8oIcM" +
                "X5ihh5qbb0uW5ZUmIdH+n+cXCREWLfbxitjzSmqfyUen1EA8WXGmRq/3L6mi" +
                "6fzZhUGLwqB1sYzpNyFeDTEuJiN7wpVNcuAbndvntUdMzTFTnJ6ztEaqHDva" +
                "tVKr8aRwxXNk2Hs3qS/O+vXnWfi6Ptu3we5Xlu7HpOqFWFO/z2Z48YzFNfLj" +
                "UFN9/Lt+Av1+tgvE1JvnQ9ORiI1yhG4KZJhhWwJ8xx99tEaAxCd8alkFDHVB" +
                "fB19ynt4z9t/mpe4rT4j4vchMU2fSdjvjKCcEJmIX5cOHkINJTVPuO3fb3pm" +
                "3ag6iX4LnWY5+ulVIKgF+Lr84S051d8mBsWcxe943B2uCgA1EVcFcIuq0fKQ" +
                "GquzM7i58vWQZ+glvC1LyusDt4UzaChcJFAIvQsSGqgjVVMxSznX0EIphE3D" +
                "5XCNxr4ha2+FyWySgndoiHZEG7cVNRc7ECzFs7z0M1pU9RoyMeXxlFqqcY2I" +
                "IsPNt25FQZdWS+YVeFr4WQRQSiE7xuFuFvBbV2MDSVxxXtMGxPTTF5Mzzui/" +
                "88mgPwQqRrRZ7tJXvk86wi0N9YON+x4YyccsvxVLWrFTj47Hb3Ed1D9hRMYP" +
                "jbFErdrOx+P3FV0nyXY5bbIGgCNsUdux5Pc1syqNUBh1W4kKHJ8RBSqlyavT" +
                "rFmaSiuru3194r1jAu1a5vQWA7/R0OYBLZuIvBsDk5I0i1MctT11UqSaCO84" +
                "WYqUM87pktpVYIBZam+pzRyZeyo4SkqSIn7ToHk24U1MLBbYr7xsRUGfKxjA" +
                "eUVF9cp5B2CVr7JFGlT4xdOQuMPtq4bIV847fHQyFGfurUPHpMW/st1blJrA" +
                "kO1c2xGbXocMP2wT0TjzWUECKjZMlOhnn86i5yYb26RfG+ZD0BuvZqc8Ovtj" +
                "iyaVBXOIJwIAto77O5n/pkXTxmBc4Ebwu0iMs5PbX32akSUECGZRytSu1L4y" +
                "oFlk3yfRGJRFpgVe582IHfbDC8kUrcV04cN1EHHjZo3e2fVCyrskB4bvXyYN" +
                "x+ZkV9PvTLzw6E4G3Z1fPZo/0yCo3G/Q8jvrE9OCabI4pdrP6yHppUWhTiCa" +
                "sqJtRrV8FEW0lbiBJo91U71O/ZP6HD1+fHqvFp/hV/cx0RcwrFQhxWsapmfk" +
                "il0zw4LhTx3uFYXOPp2tRgWVOO4jcW1oKaN8SLcCzcDVcHd1dPloY7AcqyLm" +
                "2pbPOfm0XD5+i7jIzlWW2yBKQqRKngLKRYGJ6+oFtbvM4nfe/ObbTUcwWR4R" +
                "pZr7A6s/frB/HfUiIIJakb7YS2ajpcn87Np6qhXhHoX11LcEif5rRnuy4wHv" +
                "joqbJ3btaOYOb661sgXqR+B9rZAzVkO2ugA47ddb+tjgcufBj4NShZWaDigr" +
                "vofDKry3734KxPsx1eUdJBF1WGbuBpgkS2VaHLmS3Tc8eG5UyNIlbMtyyAjQ" +
                "zycecFHkv643wR5TB+m0gG0f0Nd8A44D+xaoOxW/pLJBZy/8uodn+n42wcCd" +
                "7iNhcMw/unfwSLZX8DFy5HiDf8qAoe3F/woYcWc02g4j/mesHAcFEn78snJE" +
                "iv8QLdguJerXqizNG8q57Hh8lD0ZqETy9hxt3ArDWYTK8M5g5IeX0czDPegj" +
                "ilBMOzbBKVQn58IuS+A0oOUO8Iz29SDQDhXrNp96BSO2LA+Kju6DLxYiqPTM" +
                "L77A7Sdbts4/zx5zEp1oOeu+ilj2pSDkrfOq0lS2q5tqfkb/bt5TpH7bBh7V" +
                "Bvx2+I3YAEJEzELy6wHvP8MU4c9r/33vk7ST495/VpbvxV8Mf6cZpvzB8P3v" +
                "cvE0FsMPrNSfZPBpbOYf2N0/z//TBNh/ECAi+nUBOalycjb823nyp6j8YlI8" +
                "KX6yqv0tPkb9/9W405zA9IMT3tD8LFVO8k8m198n3Pkp/39MNQMIGfmfctTH" +
                "z8zxOSdp//z6F5mTOorXEAAA"

        private val LOMBOK_LINT_JAR_BASE64_GZIP = "" +
                "H4sIAAAAAAAAAI1WezgTeh+fy2xTaJp6c0m5TDQjIYWGMYYxM5dJHMZEzV0R" +
                "vee4tCG3yK0sl6hXCLnrOl1cNsQ0iYiG5DK3UHR6nafnfU7pPe97Pr/n+8fv" +
                "eT6f7/f7x/eGtxISFgUAwGDAuJklFrAJQcA3QDYNZ0Y0VsfaYDSEAPgfiI0x" +
                "3V7oTYL5psG+J+KMbbAYM3siEodZwrFZ1lbqyB4xK3W1LnZ3DUGz7/Db8cV2" +
                "9sEeMUtcNwKperuWYNHdbduN7WT3W2E7O/uXKpYRSCTuInYpeCFYYEvQrdn5" +
                "BAT4nPH2Dfgpu++J4t8TvbypP7OtdjTESgABgPPbAYC9P7FxEabeod7k0IBg" +
                "JPmMR0hIieOJczsdYL/T4LlK1qrFvYRi6SoTS1/QPOYaIcmX+Uu65y0Y04ph" +
                "bcOJzj0aNKEkeWH+3PyEbVqzo8eUC/LCx9W198FN3Up1yVQSxICdx5phNTyq" +
                "WT2+NIl6tCHyVu2hrSdsW9CQxRial74GjpCywpoGHU45EbyhF+0B42UPBkUp" +
                "tHrKluh1BCFUAkH58CpB9LPbifcg5aNukqpRWU8vGOAXmhI1YKbbaEHnsExi" +
                "z9gd//MO+YMOJwPnhJvJkSaVWkwE88Kb8sHz+8HSiLJ9ugGR9c54zbRcV/sd" +
                "7Utq9prEWkNH/0apicqrUvSzBDP/3K6rU4PBbwaS4nH6+MBm53f3JzSvZUhk" +
                "T5dwPVuljuo5kBWcKvXrlcMsrc8s+g2E7pkq0cp4Rqdw+mRazi4XyYHTbf9x" +
                "tvOw2ROV9clVCLuhP2XqZSh6rd6JhUzr7bkp96qI6sqk3FQR9c8oMVgVjkTc" +
                "NaPfk/rd4ohQ4aRgNbwTuvFgkDoNrMSagq9Fktqd2LNrBvUtgnPapDBQnOO8" +
                "wcSk3GGwV3m/RKzXUd0bHqAXchyJHFQPNVmBWKFLADp9Fk+pvTb7Gyxpr6Sq" +
                "3M3de7+sbSM77ljVf0GMMrObA6jTOwayLxHUMqaM0TncG+kkCc7BWlR173zc" +
                "rFaMkZFvtrZqhPBSyPVWM3DiK8PB8f6hL8XZTU7+euIk1LnG7RCLVK3mCJPX" +
                "yPhipszDZ/6vtTj0syHnMZQCWn8QLZESGyI1Lf2QVlpUxBRcUEgIv8jhVkVL" +
                "D8AjxK5V6SSN1tLo4cVvnxadxDWEK85qcaycrL/SREBIhlupQ0G5jEZdr9QI" +
                "RWaqYLT55ojlUnLtKy/fE1z9y0YjcnMvc/IpqdNEzH7gSRM1AzHZMKmRU/PG" +
                "M26zpYWNYfQmmOspO+6X/YbGwIsvjewJaxWJrdIuazbMEre8mVf4wAr5BUh7" +
                "nUfuXYVSUmgFxf0YWSpviGoTMafHK2OkxArowOmQufbrZqS8AU591kEJrGak" +
                "0TRnHXHm9rTNIbUDPitcqjM+o5KYRGe0RneIprB0+f3SWkKLeYm69+1ERWmd" +
                "Xf7tMC58m+OJsBj9dNUZPi9D4n1gm001hwrjfY6/falkzP9J4qfyrgstS+iA" +
                "fpN1ytBkA6oBJNzAJadM4ph+QwHQ3Z/GMzNLNvwi2jVh7OW4X8zXgSqvlAOq" +
                "4EtU4+G+yKbM4MKoMidb/as837sqEZdYGfLNjBOsihEELJubvLEo99ljz7A9" +
                "xMrtiU4OgrYt8jT8PqYG1IZ1jFx0O7X6KGf8sKcF0z33i3kfx8adjn+f2j68" +
                "WFPWs+IsMVv8nPSZJQvU8Tn/nFdn8ugW9CrHG8MPcpnSVtPGaByPXbwCN58X" +
                "eUAendg3XNzA3WV0lR09MTm/C4sdZ8mNO39oC9m+sYya7TF8XZ2yYnpgSXeW" +
                "0XoxJXPSSx/zbqhvWVch5HJ1m3z8ceayM++c6WQRuYf6VAHlGYU4lkZogP4a" +
                "09e3S5JDMMg91iyGG9sLNswXV2dEW2bRT/nEaboqjdTwSEWkAr5iHCpWddHy" +
                "uH4jb0byJro8JGe/S0pcDavRJMvhPvrXj3xoGJ/jL9sxkuOwIHCnxy/8tysU" +
                "tOdEy+qZA+lfUkdJSmiQj31sKbm0UF0WtXewJcH9cnO+9HDL6e7AxaOYf2LG" +
                "fP/1kDVyqEVTfjGM4ZlWLbZjwswaIxnnA+X3hic6/66Vtbb7x1GqR9axSxMA" +
                "AB5sTl/F/zJKsSEhYd4Ebx/fkNDgiG/zNJOAs5U5BJNFp1N7L8bBOo4UEiQg" +
                "Fs5XyEXLAKKJi5HC4zcDzpcUWzH3hi03DnYA+4RQBajbHwapwmBhycj5Gp/J" +
                "TpnUjUk+PxIwUuVmDM3vyHnmCsw/xPdgPlFAl95ICj5Ja6dM5xVWnQalF0of" +
                "UEzttXN/fquoElIK0oV9dn2qpp/KjLmHh6AV48uTa4lZEry2xSdlsUrY8YQk" +
                "4thYmS76aDqyNf5kQH1M4Zpq+IcrEwJ2Wia2ZphGbfhLvgaYsa9WEcneV+/3" +
                "mFF3bNRlj/m7vO2M5KZGooH8i7l0lxIfjt4VFJESvJLLqLAeNIdcpyvLs5wX" +
                "qj6QxcRoL49otDx2X4D6KZcaNIbPOIffaTxSz8YbvP/YtbJHdyRIn9bUeups" +
                "BdcUz8WX2ydoabeRMpSfz9d9QpmqWz9NXUcr1LsLp2oceHGv7SGkCrHTBfXC" +
                "l652w2WoEbie+RXJcSkUjSqG568U6KhAx+yVd+bAMfAqw13z/ppB9PZpUAJB" +
                "vP96beS7Fb+uUqhuBv/D183dKCAIE/rrpfsNOwCzxoDv9/5W2dYD4T8ojlb9" +
                "i3Ph7we+/n1l/S+Z+A+y1p8Kcqt26yXwZ9If/+9dsNXX1lb405eRyN9qDLwV" +
                "UOQPgcjmS9h0BAX/8fs37IxfaLAJAAA="

        private val PSI_LINT_JAR_BASE64_GZIP = "" +
                "H4sIAAAAAAAAAJVVd1DT6RYNVRCwANJVwCggSehkBUKRpSUUaZHqiiGGCCaU" +
                "wEpRiAgCg4AgoS6wUkSy4BIF8RkjBCPFaDAQ3VAkGlpWUIqCBWRx1jdKnjLz" +
                "7m++P77fnHO+O3fuvecQQkx8MwAgJQX4E+UBB6yFKODfkF47bg4+dmAXd0cD" +
                "McChdcD2GjTqM9pz7Sh+C3Szc3dxdPD2gbg5LrgxH7giwJDHcgjw/kdM1nUv" +
                "wwET3vh8D1P/sRzcjQWC6DXc8HJ+xOIgGp2Z4G4XDoLF8tjkdcB2RwKtsILB" +
                "qL1qmEYNogZ98zas9Venb5MUXzso/Ml1+QljZL9g0KdCTkZGoDfEKghhMXg8" +
                "RogiXCeV71MisDjC//BScy+rG63l7Lf2y3gDnlsIFmeHImDjsIT4n9EENIqA" +
                "j4agIkJiYgqR4d7qvipnWm1xckX3kYGNcZ6D6QBua5VS6oCpuERnrT+88WY0" +
                "5cC5zojF0DiPbWdSbeh3j0bVzUxl27SP8jFdSxEHO+i8fSZj5TDqZPyzBwu0" +
                "sruf3q94AEzeGaXJzkv76Q13jADTIw3SsEzgbPMRGxdev/grHcOyexhdoNF5" +
                "Fr84l28n/0tVMV1zV90h+kGN8Z+U5GRB3EU+hZiZtadMaeFvmmIswkUgSdK3" +
                "QnNuvcjJpzEC6AGbz5obqGQ8lPaojxc7zZ1zE1vZ6z8pma5P55Nnjl/4qP7H" +
                "BNulTnvnBM74l6nJVyF3B7cTiUnd3Uv+18ejyVroVFJedJr0tSndZp2BjoNF" +
                "Fc7XzNv2twY0RZo+2nzYkkLrLS3/05g71wsZQiEDpZ82ZNhePM1Wksu92upR" +
                "ftNdlRlbETxhBgp8Tw9XnBCwGbDtUhwR6CzC3CuAJH0VCGMs+WpRbZWGYxlN" +
                "nM0Wq+Db1B5Yy9UOveJ+x5fEqTxS8UvSUqD1VlGtOwsgGYu3UVc6w3k4VhN7" +
                "5k6OvSTOcb4sy+ktQW7sspPtCWxcf/wKNUwWOKFxk0y+BB6idSqnKTFbrotf" +
                "fDqO4J+k2u8fJhnn1JqS5mLViqL25sDV9P329kp4QreGDkVET5QEmk3gS+gy" +
                "ipjYBTu1cD9o5HtQklgRcgvzPI03VHea5xJNOrdYH9ZazFKzDp8pZkM5HuxQ" +
                "My6ybvD5vhifslt1fcH50MrIfq1Hce0Pg5r2jSRAoFic53TQzhvvntT0FBZA" +
                "+acuOe6I01DHWkkWvjlS4z4QXI3oVNzLbQ81eKjYVWBK6/D+jYbnv4NceD1n" +
                "r8xfzchd8J5iJLmSM7NOesOt3G+ef2blbCBoGaNo9oFPj7cFI9ix0ude1Cx0" +
                "pAQUeunCRhiS9yk6avGNrYj0NJN0IgcV0wesiqruOqxzV59r0jI0s/VY9tlk" +
                "8SuJ9EmwQOEh67GjK2a+4qU9HlK+Kzmh/Od820y9Z3mfRMbrbyAbSQh1MnrI" +
                "unTKE6+sTeEm5X4EHkk4XEjmtJzwqahOvGeyq+fZRfCKfCAEp1QJhvj2sqbZ" +
                "gxZ31EYtfq+jPMUNBsn8vegyktYALg+5EqOWCfvDoIc+d2C8q32U2y+RRWB7" +
                "S5MSHoSa1n7CGDtMZ+dQjeB3McZVl/pHzWDdAqiHzQy2t3/JN6G0jlMVq7kk" +
                "fz9mMEjTXtNBP08HJfOaDRkxoie2uZOJS3Mi/mEplsEpfhN0/TcrH9S0lCrc" +
                "K9t2vnxG2z5YTDDP2nobF6GwCGjco3B+qrK0OIFIdJ2WeIFPIOzuLJYzwlgs" +
                "Brve+SiF0mJJ4V4gw2Bnh7ovGwxf2OP0GGg4Z/HGnz5Q+dhVu4BZsyUqChiV" +
                "8Wa3SlKpqsW9WUQ2lV472rdqJfv+p3p1cWtVC/O0awefEtJN3hmTlpXXL6AQ" +
                "DpBXIgIAPF/bXuCNFlC8S0xMLNoLjcHGEKLjv+wer2CEup0iDGX9pKb5RMNK" +
                "a8A2Vx+NczQHEKNVMyLQsHSp5/hfR0qY3a8iwj4VfjgmsE+2TfYRjLjVO2yf" +
                "th5OnHx1YInPXP6wwtdJgfqRq886mYFCbuV0nZLnYVMwohwYZWxJAt4Y51GV" +
                "xJMMOGoNJNtgOmnuOb6jkXWq/3EuqYDr2ngUoFcLvYhxKrd+HxgdmGWGqVoq" +
                "XzcJvbNsau3a77pbUOxVmW0p2uL3UbuLepy8s6LhYn6Jzh779LEUCAP5HvT8" +
                "Ax4vspiF7N0i7yQm+M1EUNNvm7gzvsg51pES1sedPPG26UG+c3JN0nCtOn96" +
                "rEW9XltqlkpUPqV6tJZ42G/HX22TcNH5nExt+9KR0fgm1aFfnyBDRx0qHMe6" +
                "Xl+RkfbZcnukYKto4Grz7fb5lshNj1Qk866DPgTBSeoJmBiYxJNAhcwLlOZl" +
                "tg366OSTFp5Z7/KZebyk9uHsGVAlMLGDRomXiPJcbmpuVOrZMcA13wew4klp" +
                "sJ5rVUfPv7qkEXRswhI+CZ0KX+1L1TtNawdF4pozSpPXTEZEVFHsxzb+b2wD" +
                "zNgBvvVqYZqwqf83aoh6P7D4bxW+Z81fH6790mwbMWTXMXqE2nMjpsI65tvv" +
                "N/ZGVVJZJ6Ar8uPJEFYRtvSvRTu6gcqPDV5YX3hiv+o/l/g/5/cQQkLyM1Vq" +
                "7dNZk2Ns+nz7B9ep+6YCCgAA"

        @ClassRule @JvmField var temp = TemporaryFolder()
        init {
            temp.create()
        }
        private val lintJar: File = base64gzip("lint1.jar", LINT_JAR_BASE64_GZIP).createFile(temp.root)
        private val oldLintJar: File = base64gzip("lint2.jar", LOMBOK_LINT_JAR_BASE64_GZIP).createFile(temp.root)
        private val psiLintJar: File = base64gzip("lint3.jar", PSI_LINT_JAR_BASE64_GZIP).createFile(temp.root)
        private val lintJarWithServiceRegistry: File = base64gzip("lint4.jar",
                LINT_JAR_SERVICE_REGISTRY_BASE64_GZIP).createFile(temp.root)
    }
}
