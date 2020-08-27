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
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestResultChecker
import com.android.tools.lint.detector.api.Project
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

class CustomRuleTest {
    @Test
    fun testProjectLintJar() {
        val expected = expectedOutput
        lint().files(
            classpath(),
            manifest().minSdk(1),
            appCompatTestSource,
            appCompatTestClass
        )
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
        val expected = expectedOutput

        lint().files(
            classpath(),
            manifest().minSdk(1),
            appCompatTestSource,
            appCompatTestClass
        )
            .allowObsoleteLintChecks(false)
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = emptyList()

                override fun findRuleJars(project: Project): List<File> = listOf(
                    lintJarWithServiceRegistry
                )
            }).customRules(lintJarWithServiceRegistry).allowMissingSdk().run().expect(expected)
    }

    @Test
    fun testProjectIsLibraryLintJar() {
        lint().files(
            classpath(),
            manifest().minSdk(1),
            gradle(
                "" +
                    "apply plugin: 'com.android.library'\n" +
                    "dependencies {\n" +
                    "    compile 'my.test.group:artifact:1.0'\n" +
                    "}\n"
            ),

            appCompatTestSource,
            appCompatTestClass
        )
            .incremental("build/intermediates/javac/debug/classes/test/pkg/AppCompatTest.class")
            .allowDelayedIssueRegistration()
            .issueIds("UnitTestAppCompatMethod")
            .allowObsoleteLintChecks(true)
            .modifyGradleMocks { _, variant ->
                val dependencies = variant.mainArtifact.level2Dependencies
                val library = dependencies.androidLibraries.iterator().next()
                Mockito.`when`(library.lintJar).thenReturn(lintJar.path)
            }
            .allowMissingSdk().run().expect(expectedOutputGradle)
    }

    @Test
    fun testLintJarFromLintXml() {
        lint().files(
            classpath(),
            manifest().minSdk(1),
            gradle(
                "" +
                    "apply plugin: 'com.android.library'\n" +
                    "dependencies {\n" +
                    "    compile 'my.test.group:artifact:1.0'\n" +
                    "}\n"
            ),
            xml(
                "lint.xml",
                "<lint lintJars=\"" + lintJar.path + "\" />"
            ),
            appCompatTestSource,
            appCompatTestClass
        )
            .incremental("build/intermediates/javac/debug/classes/test/pkg/AppCompatTest.class")
            .allowDelayedIssueRegistration()
            .issueIds("UnitTestAppCompatMethod")
            .allowObsoleteLintChecks(true)
            .allowMissingSdk()
            .run()
            .expect(expectedOutputGradle)
    }

    @Test
    fun `Load lint custom rules from lintChecks`() {
        lint().files(
            classpath(),
            manifest().minSdk(1),
            gradle(
                "" +
                    "apply plugin: 'com.android.library'\n" +
                    "dependencies {\n" +
                    "    compile 'my.test.group:artifact:1.0'\n" +
                    "}\n"
            ),

            appCompatTestSource,
            appCompatTestClass
        )
            .incremental("build/intermediates/javac/debug/classes/test/pkg/AppCompatTest.class")
            .allowDelayedIssueRegistration()
            .issueIds("UnitTestAppCompatMethod")
            .allowObsoleteLintChecks(true)
            .modifyGradleMocks { androidProject, _ ->
                Mockito.`when`(androidProject.lintRuleJars).thenReturn(listOf(lintJar))
            }
            .allowMissingSdk()
            .run()
            .expect(expectedOutputGradle)
    }

    @Test
    fun testGlobalLintJar() {
        val expected = expectedOutput
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
        lint().files(
            classpath(),
            manifest().minSdk(1),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "public class Test {\n" +
                    "    public void foo(int var) {\n" +
                    "        foo(5);\n" +
                    "    }\n" +
                    "}"
            )
        )
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = listOf(oldLintJar)

                override fun findRuleJars(project: Project): List<File> = emptyList()
            })
            .issueIds("MyId")
            .allowMissingSdk()
            .allowObsoleteLintChecks(false)
            .allowCompilationErrors()
            .run()
            .check(
                TestResultChecker {
                    assertThat(it).contains(
                        "lint2.jar: Warning: Lint found one or more custom " +
                            "checks that could not be loaded."
                    )
                    assertThat(it).contains(
                        "The most likely reason for this is that it is using " +
                            "an older, incompatible or unsupported API in lint. Make sure these " +
                            "lint checks are updated to the new APIs."
                    )
                    assertThat(it).contains(
                        "The issue registry class is " +
                            "googleio.demo.MyIssueRegistry."
                    )
                    assertThat(it).contains(
                        "The class loading issue is " +
                            "com/android/tools/lint/detector/api/Detector＄JavaScanner:"
                    )
                    assertThat(it).contains("ClassLoader.defineClass1(ClassLoader.java:")
                    assertThat(it).contains("0 errors, 1 warnings")
                }
            )
    }

    @Test
    fun testLegacyPsiJavaLintRule() {
        lint().files(
            classpath(),
            manifest().minSdk(1),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "public class Test {\n" +
                    "    public void foo(int var) {\n" +
                    "        foo(5);\n" +
                    "    }\n" +
                    "}"
            )
        )
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = listOf(psiLintJar)

                override fun findRuleJars(project: Project): List<File> = emptyList()
            })
            .issueIds("MainActivityDetector")
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowObsoleteLintChecks(false)
            .run()
            .check(
                TestResultChecker {
                    assertThat(it).contains(
                        "lint3.jar: Warning: Lint found one or more custom " +
                            "checks that could not be loaded."
                    )
                    assertThat(it).contains(
                        "The most likely reason for this is that it is using " +
                            "an older, incompatible or unsupported API in lint. Make sure these " +
                            "lint checks are updated to the new APIs."
                    )
                    assertThat(it).contains(
                        "The issue registry class is " +
                            "com.example.google.lint.MyIssueRegistry."
                    )
                    assertThat(it).contains(
                        "The class loading issue is " +
                            "com/android/tools/lint/detector/api/Detector＄JavaPsiScanner:"
                    )
                    assertThat(it).contains("ClassLoader.defineClass1(ClassLoader.java:")
                    assertThat(it).contains("0 errors, 1 warnings")
                }
            )
    }

    @Test
    fun testOlderLintApi() {
        lint().files(
            classpath(),
            manifest().minSdk(1),
            java(
                "package test.pkg;\n" +
                    "public class Test {\n" +
                    "}"
            )
        )
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = listOf(lintApiLevel0)

                override fun findRuleJars(project: Project): List<File> = emptyList()
            })
            .issueIds("MainActivityDetector")
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowObsoleteLintChecks(false)
            .run()
            .check(
                TestResultChecker {
                    assertThat(it).contains(
                        "lint5.jar: Warning: Lint found an issue registry " +
                            "(com.example.google.lint.MyIssueRegistry) which is older than the " +
                            "current API level; these checks may not work correctly."
                    )
                    assertThat(it).contains(
                        "Recompile the checks against the latest version. " +
                            "Custom check API version is 0 (3.0 and older), current lint API " +
                            "level is "
                    )
                    assertThat(it).contains("0 errors, 1 warnings")
                }
            )
    }

    @Test
    fun testNewerLintApi() {
        lint().files(
            classpath(),
            manifest().minSdk(1),
            java(
                "package test.pkg;\n" +
                    "public class Test {\n" +
                    "}"
            )
        )
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = listOf(lintApiLevel1000.canonicalFile)

                override fun findRuleJars(project: Project): List<File> = emptyList()
            })
            .issueIds("MainActivityDetector")
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowObsoleteLintChecks(false)
            .rootDirectory(lintApiLevel1000.parentFile.canonicalFile)
            .run()
            .expect(
                "../lint6.jar: Warning: Lint found an issue registry " +
                    "(com.example.google.lint.MyIssueRegistry) which requires a newer API " +
                    "level. That means that the custom lint checks are intended for a" +
                    " newer lint version; please upgrade [ObsoleteLintCustomCheck]\n" +
                    "0 errors, 1 warnings"
            )
    }

    @Test
    fun testOlderLintApiWithSupportedMinApi() {
        // Current API set to 1000, but minApi 1 so should be compatible
        lint().files(
            classpath(),
            manifest().minSdk(1),
            java(
                "package test.pkg;\n" +
                    "public class Test {\n" +
                    "}"
            )
        )
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = listOf(lintApiLevel1000min1)

                override fun findRuleJars(project: Project): List<File> = emptyList()
            })
            .issueIds("MainActivityDetector")
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowObsoleteLintChecks(false)
            .run()
            .expectClean()
    }

    @Test
    fun testLintScannerXmlAll() {
        // In 3.1 we moved the Detector.XmlScanner.ALL constant up into super interface
        // XmlScannerConstants (because the whole Detector class was ported to Kotlin), and
        // in Kotlin you can't specify a constant on the interface like that (the constant super
        // interface is in Java). This test ensures that this is a backwards compatible
        // change: this is a custom detector compiled against 3.0 referencing the XmlScanner.ALL
        // constant.
        //  public java.util.Collection<java.lang.String> getApplicableElements();
        //    Code:
        //       0: getstatic     #6                  // Field ALL:Ljava/util/List;
        //       3: areturn
        lint().files(
            classpath(),
            manifest().minSdk(1),
            xml(
                "res/values/strings.xml",
                "<resources>\n" +
                    "<string name='test'>Test</string>\n" +
                    "</resources>"
            )
        )
            .client(object : TestLintClient() {
                override fun findGlobalRuleJars(): List<File> = listOf(lintXmlScannerAll30)
                override fun findRuleJars(project: Project): List<File> = emptyList()
            })
            .issueIds("ShortUniqueId")
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowObsoleteLintChecks(false)
            .run()
            .check(
                TestResultChecker {
                    assertThat(it).contains("res/values/strings.xml:2: Warning: All tags are now flagged: string [ShortUniqueId]")
                }
            )
    }

    private val expectedOutputGradle = "" +
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

    private val expectedOutput = expectedOutputGradle.replace("src/main/java/", "src/")

    private val appCompatTestSource = java(
        "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.support.v7.app.ActionBarActivity;\n\n" +
            "public class AppCompatTest extends ActionBarActivity {\n" +
            "    public void test() {\n" +
            "        getActionBar();                    // ERROR\n" +
            "        getSupportActionBar();             // OK\n\n" +
            "        startActionMode(null);             // ERROR\n" +
            "        startSupportActionMode(null);      // OK\n\n" +
            "        requestWindowFeature(0);           // ERROR\n" +
            "        supportRequestWindowFeature(0);    // OK\n\n" +
            "        setProgressBarVisibility(true);    // ERROR\n" +
            "        setProgressBarIndeterminate(true);\n" +
            "        setProgressBarIndeterminateVisibility(true);\n\n" +
            "        setSupportProgressBarVisibility(true); // OK\n" +
            "        setSupportProgressBarIndeterminate(true);\n" +
            "        setSupportProgressBarIndeterminateVisibility(true);\n    }\n" +
            "}\n"
    )

    private val appCompatTestClass = base64gzip(
        "build/intermediates/javac/debug/classes/test/pkg/AppCompatTest.class",
        "" +
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
            "4oAsItjCDnZjw8TImvE3oCvXeGsFAAA="
    )

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

        // LintStandaloneCustomRuleTest compiled with API_CURRENT == 0:
        private val LINT5_JAR = "" +
            "H4sIAAAAAAAAAJVWCTTU+x4fa/JGWUZckot0UUYvublKMpYyY8Y2V7qWMWam" +
            "aZjFMhhbVGi5cW1jSeiKbCFrE/KmN4kYYxslyhrCM0i6Qtxxbu+USd673//5" +
            "nt/5n/P5fL/f3+98N1uYiKgkACAhAcDfOQcD8EUY8Jds5yvcAmmqa4Ww1BMB" +
            "2G4AlocL85T5gO/4CvoSCDdFWFlaOCDBcMt5OLvFGqYL7pCC6epw2G0V9ge5" +
            "+oOjw6PCAsa+9CrKVwyF9JXDLzHATxgcDU3yJuK2xMoJYPEUCv5/UBQ3pxAJ" +
            "ZOpXPM9W0br3IgBAHf+1tLbgwYPMcVQchkrxBWOIaD8/umO4Q5+ldJgCszxm" +
            "UaYrh9S5DwG9sp0ruZdEgFtdPVhc8KhArNIqHBsZapBdztHISJ2VWSIpGZkd" +
            "mrA2OWFis8qj2lSLJB2mG2MyKiuW+yeneRmrS8sNWQDecN6BfQcTnxB3qWan" +
            "PtK6foCMAccEVfVbjZCVVHPnfMSz3NUu2cylRSdENwCPN9rv0mCkNCg4OaYs" +
            "K1lL7Qeaybf06uizkSodh4j/kpG4jz6lVtc+64JSkgI/0LBw7yE9z49EP4YB" +
            "CyyeB47Ksk6gg9PR7SApzkFH/7B6maoA2j3CDA379OcoTEhfwGDheImPLa9G" +
            "jYVlN2X1FFc3xtyRhv/i0bxwiQ7WtnOt7CO1+SPz5auC3IMDFH2vtcnfKQp5" +
            "X08y71y4XqgMfctxInfdx4Gk27jfXW7gVOCX4ayEC1NHYDBQ0qtAs9NPmMAq" +
            "iLrmM4kmUP1rG2yBzsU7ZY2jCPfa8+fMA7RiihVLcrgdN2NVWS4D7UCWFI8W" +
            "4A47vQhMGWA2xLlR68jo8uQrwpdLa6BzZRiLX6FCKpQRlWg7fxRyEkQ3Uqrn" +
            "zsnrGIEHs6MNVFC3m22dZIJQTXVFk9zhenbWjpYjLyNuPRRRmeq5VUO0E08/" +
            "lsSV6nw5ytGbB463aiVl009XhxKxqQM5x5scguaHSq7UWbLLvWbm13J0PPMn" +
            "aBGUw1x/p569ceakPtZUIe3j2lEbVnIsKbQoZwnBMlP5bfZhrOqsBosoy9kj" +
            "zYKY8BKdkXYtZWFZDm9mC354mP3stIj3EOMBfCgIhNA7+T4+TqlvxH4Rqq9U" +
            "+e+r1aXT/4nHTBjcxrx7kZ7kmggzGFSxe/6P0ulW35NBHtYXBy45hFgz+9S/" +
            "v4FKhGPxlGBW6LsU5otiSKtvdmd69Wu4zO44yNliN730dGVci4fWvuZbCq7Q" +
            "prNzYXsYCT3RzQvBxZlJeQonj3VdXVHpcAx52vv2/duRbmjVSs8hr51v3qQa" +
            "dsBwJp5nXKq6WhNdn+syz5jkEtTfM2q4IATTSjU6JapCDIqdKBKlryZZP8vL" +
            "+/F2pF/HUdoPEHBLvRFQ+Q9C6dKL2GM34jyG7EwOMiwHdsi5035dnSl8lVmv" +
            "Jz6RiYjqEVasPPTLZbKxSAj0yLj31ZKCHtq2lInCwN2ze/rz6EOSJQkpkvWv" +
            "pBCjHONGJmXtmpuhTMak5TgmywtbG7uzRpu7PSTi2VyaGZDDYe9Ba6UYXQy4" +
            "CU92bL/fqshrSP0QG67g8Ao+bMnWj9V+uXfmsGSpWxlkbjJQI4WD831hBvwt" +
            "b+fvaxfiLz/oMLtuzFr0u6lqCOEFAo/HoOzix05pS7/UFK67+FN2qvrghGGB" +
            "s1NMl30z4tbN5hWRjb2DkFv7M0MIALDh9w/dLXuHlZ+fP84ehyf4UX2DPjUQ" +
            "e5SXnKn0GrzztbU1Sdl5DVn0o25qPkOiPheZoyNqI3Y7bMAleSq5lczok1vO" +
            "CLVFgVRXAOe1fHi+lZq5tjPos4GLH9MyHpiEh38UnwX1wbk+xRemGB2P4Xe3" +
            "MeQHAyKkIGEnnacDt3eTKVn37o0/uXYP5FnUfzYxHnq8KX4IepQaf3j5UMeH" +
            "hwfAiyIZssHo1/sgYcmJBXbqAeAxbOXCcGvH93D9BbcGo4WndlntV+h6nmyv" +
            "wJCh31f2WoxVyaiZ+TJtGx2XDqA/4vGAo1H0ypHHE2WznDTq2GFHN4t+TQii" +
            "Sgu4VHvXEV877uB6jHLi3A7XZPNJOqcWlTHF8yF3QyJgQrxKb4U/hInV7Y92" +
            "mcypVxPf9crxUN5Koj6xntjKYepKkpzleOi0Am5/ZK+74cpiwuysSbSF09G0" +
            "d5n60UK9Z/o7PQp1+2j4eTHp6tqKDEUCZo5MbWKuphl7yBmsjr8tlWzGN5c9" +
            "OvIozfD5nhVA2IiZGDx2IZsR27LbWR8wbsGJ3O/kgUSct0+4NJO/Yr4wtTts" +
            "BGSsNdEidjbKi+SiG0JDNiMLUWMgozyQTqPU3V33q8K/PXZkvxyjfjjfAAIG" +
            "5/fVwFFTmD+/PmjW1XRTBj+NwGgy1pdCwIKpFArRD7yeRfysIeD4B9qbAN6Q" +
            "TjBOWzes+BRbt8mqG9bWZrPN/qe0IM3genpmQ0NugWnBRFqDpgXdhR+GkDBI" +
            "5Nurwl8iDZhej+pzWII0wcXhv5JzQfsba8RWjkU3OA74VERbMYAbGDcEym4r" +
            "ptwG5pPNC3YrA4obDHzYouIFrQhuGZ8f7Z9C///OIWhVsP98tkoS+5vdaKtr" +
            "y264tve2zXJWkC+Y5Z9Dy96U/zdz3hYmJr5uTpL/sflxakus//0JPci0DgsL" +
            "AAA="

        // LintStandaloneCustomRuleTest compiled with api == 1000
        private val LINT6_JAR = "" +
            "H4sIAAAAAAAAAJVWCzjTex8fcynvlGukg9elkpMRlY4mMVY2m+uLzqlY246U" +
            "bRg2FIVczomEGUYiueXyul/bq7MjDruQxQq5pVDuhZDOPKf3Kau87/n+n+/z" +
            "e/7P8/l8v9/f7/ne7BBAMSkAYMsWQHHheQRAIKKAv2SrQJFWTuZ61iiYPhBg" +
            "twFYHio6tUsA2ClQhc+BSHOUNczK0QmMhM0jWe02CD1wpzRCT5fD4lY4GPCM" +
            "BkeHR0WFjH3uVUygGCL+C4efY0AfMTgKGu/thdsUKy+E9SASPf4HRfnrFC9P" +
            "gt8XvAtsscYFIADQKHgtnU14yEBLnB8O40f0BWO80CQS1TnUsRcmc1mpqTx2" +
            "SbYrB/9oDwoevZUnpY33RFrHGBQXPCgQr7QOxUZcOpRdztHKSJmRXcarmEAN" +
            "x23MjpvZrk352VYDkw5STTEZlRUrzyYmpzLWlleaMwFTw3n79xgkPvTaoZ6d" +
            "8kDn+n4CBhwbWPXMeoSgon531kci010j3HY2NTIhshl0rMVhh1YtrVnJ1Zm2" +
            "omIj/T0Iqtj+VNeI5aTaaej1H9ktNeiTGo0dM6fdVKTBdVpW7nx8T34E+ncE" +
            "qMCqhzwqxzyODqKjOxSkOQbO/pcZslUBlH97TlOwf/zrGia4N2Cw8GWJj91U" +
            "vQYTy2rN5BdXt8TmyiB/PNf2NpwK3md/prIXz/V3ylesCnQPClD2/YWrmHsv" +
            "eIGBt3z09nrhLvgcx5XQVYNTkOHydkY1cyo8VpDMhKuvjBEIhaR+MtTlYROo" +
            "ykJzd/eWVgXGc1tsgW5YblnLKMq9IeS8ZYBObLFySQ6vMz1OnXl6oAPElJ6i" +
            "BLgjXJZAtIGm5vizfo0EdHlytGhUaT18tgxj9StcRJU4ohpp7+/mNKFANVFh" +
            "8GYVdU3Ag9mRh1Td7rTZucoGurU23pvgDTNYmdvajfuu3L4PVH3Fv13vZS9B" +
            "hyTxpB/1jXL050Ev2TpJ2VSX6kte2JSBnGOtjoHzQyXRjTBW+cXp+Q85uhfy" +
            "xylXiAd5/q587XhLfC/zVSHl/YejtszkOPyleznLKCZU9cbM/Tj1GS2mlxxH" +
            "TYZpYTaV+JOTfXvZ5UzHsZmCvfezu12A3kO1dcihQAWU/omFm/EqvSMOS3Aj" +
            "lcrfYqpLJ1/fxIwfuoN584SedCYRcWhQ1b7nH6WTbN8TgedswgbCHYNtmno1" +
            "/5nmlojEehCDmJfe0JqeFFuwfbMf0aufI2W/i7f4ufisPp2+C9d+TmdP222l" +
            "M/DWn2cvq9Um8CPb3gYV30rKUzoB6YpZVe10Dv7j6dzC3MhjeNUq3/Di9rGx" +
            "lCOdCJzZhVOnq7rYiWd69JpOmd311FyorecpoJqs1SNp1yrE4djxe2LUtSSb" +
            "7ry8w3ciSJ1HKXstwO0ME9CuRc/S5SdxkLT4c0P2Zga1sIFt8u6UX9emC/tv" +
            "MfQlxm+hrvFFlSsNf4wimAKD4cYvvWNKCvgUSdp4Ifm7GbVnedQhqZIEmhSj" +
            "Xxo1yjFtaSJ++OXsEdmMCdhLTOZFbEPc9vp9vK3BV7pnU6EgDoelhtahmYQF" +
            "pCOTnTtq2MpTzSnv4kKVHPuRwzCWUdy+Pu3pg1KlZ8ssZifIWjQOzvcJFHQj" +
            "b3vWh6s3o+o6oddNmUukdPUjFlNk0LFYN/ubL07uk+nbLdoY9kN2iubg+JGC" +
            "n1xjuxzaULfT21aBG3uHs3GyZYUIAKAp6B96m/YOaxLJH+eA8/Ak+fkGfmwg" +
            "Dv0oeXOFtUhCQ+WOm1LROnP0Dvhvv0c0yIHMkWlFaa5DIfUZRdBcI/bU/qvk" +
            "YYu6B95DwCVtyLXZ1wHAnAjLxeF0o70D3FSjlRXuY33AUdd7WdfrdF/MFkWK" +
            "QxbLxkbEoTiXnaMLynB2t2w42X0lAW1iBwuBS5ruDFU0RWbA3KFZNyyLykmd" +
            "b/qMqQaVYR3baiGK4VnJkPLD2TlUn9WRU3utHd5ZwXR9Zk/Rs1SsaYvRPeer" +
            "wzA+mpSJpBci5obHXaxgtQf3vK9ZBDTUUQ3e8wuPkbsnsM95NFQk5+FpkCJX" +
            "+mlwjrNGw/usttIedL4/XiVqLrwtN1OVTjhaUXhHdKCl3SR5pq3FV/V7Vxx/" +
            "tWWX4YykBKgqIYnwsAFXZFr0vqRWEVrXNanG8OAv3xA3YPUTppsuIAE4aXyI" +
            "rn2kV/qhJw47D6fdMb2I1lyrI9s0LdyveaALWY4pLxVLnowZ1s44goPVrS7L" +
            "vZZUlM93KHrOtuLpdJ8AQLKlnGyHDmQZztHDXru5D++FD7SMIchjIeaZnAO2" +
            "+cbhsAPMAI0RdlIJt+Vg9/R5irEWhG1fESry7Ykj9/kEJeF8AzwxONIXs0ZD" +
            "aT5kfcasq/lXGYIMAqMJWF+iJxbsRyR6kcDrCSRIGE+c4EB7e4I3ZBKCw32M" +
            "KD7J0mu1fozgcm0lHX5IDdwdxKDeam6+W2BeMJ7avNuKeloQhoioAvDbW8Jf" +
            "IgOYXI/qU1jCNOGd4b+Sc3XfNzaIzRyLbXAc8LF+NmOANjDShCpuM6b8BubD" +
            "r9fqZgaUNxh4t0mxC1sRXjA+PdoBkf9/3RC2Ktx6PlnFi//NRrTZteU2XPui" +
            "5NdyVpgvnOWfQsv4Kv9v5rwdQlxi3ZyU4GMJ4tTesv73Jy+EF4AGCwAA"

        // LintStandaloneCustomRuleTest compiled with api == 1000 and minApi=1
        private val LINT7_JAR = "" +
            "H4sIAAAAAAAAAJVWCzSU+xYfr5Q7DjLldZBn6BjyiKNwmEyZMRhccfKaZibJ" +
            "jJHxmPGIKFKRZ6M8Q56hhCQm586V1xivkXFQHkdC3o8UhzvW6a4yyb1nf2uv" +
            "b31r/X6/vf/f2v+9tzWch1cAANi7F6BSfAEOYBs34C/bx3aEmZ2JurklVIMH" +
            "YL0NWB7KPSPFBkiwHfQ1EGFiaQ41s7UDI6CLCHqrBVwd3CkIV1dj0Nuf2Ggy" +
            "tYfGRsa4OcS+jsrLdjQB/03ArzHAzxgsCYX3xmF3xYpyYN0JBPf/QRHfmYLz" +
            "8PL9hnexjbd2hQcAqGX/LZVdeAjySawvFu1L8AGjcSgiMdk+1LYfKhwiVl8e" +
            "syrSnYvvUraEXd/HFFDEeyDMozVLCn8r5KswD8VcDdbNLmcopKfMiXzCSxpA" +
            "tCYsjH8xttqY8bWq4knSSTZEp1c8WXs9OT2TvvFprSETMDOS/5OyZuJL3EHZ" +
            "7JTfVG795IUGx5ArX5uPeknKPpi/tCfTTS7Cav5uZEJkA9Co0eagQjWlQczB" +
            "nrImaSF4BAg50Pq7mjbdTrpTC/dCZO9T1Gm52o45J1dJQfAzBTM3Fr634Crq" +
            "33BgoVlvwNh+2i+owFRUB0iQoWnvF0IVqfQnPfKYJWGa/3kNHdTvP1Q0XnrJ" +
            "eqZGjoahN2WySqoaY/KEEb+ea1mOSAarIp0r+vHtfnYFByrJboH+4j432g/k" +
            "FQetUPEnu5ZvFUnBFhgOXt1PsSDhdqZEVAPjifsagpZwZUoPDgclDQZAzrys" +
            "B1aayiu92tsEov5hhSlUC8973Dhm6fb88oWT/ioxJeKluczOtFhZmtObDiBN" +
            "cIbk7wY/swqkvKlviHPxrfVCld+5zh1VVgObf4w2uwnjkiaMSkci/VztJkHJ" +
            "BpJU5vwBNQPwUHakrrRrTou1gwjZtam2eJI5QqVn/tCqNxCWVccjPcXKqsEh" +
            "96SeSGIKdg2MMTQWgeNtKknZyWeqgnGYlDe5Rk225MXh0uu1UHq55+ziZq7a" +
            "xYIJUhhBh+nnwFKMO4nvp00Vkf7cPG5FuxOLDy7O/WRJg0jfnquLlZ1ToOH2" +
            "M2SEaabGM4ln7ZCtj0Mybd/NFR6uy351hsd7uPoZYpgMstQ4tRIfJ9k/arMK" +
            "05as+Fd0Vdn0+3j0hG4OeqkvNck5Ea47JI3s/UfZdJvPKfI5i/A3EbZBFvX9" +
            "8ofuuSYiMO6EQFrwEqW+r8S0zSe7K7XqD4TIj3Gm50tcNFJTpbCt51SUW7LE" +
            "nGFN5+dDZKoTWJEty4ElGUn5YqdOdEevS3faBzX/vrCyMNoDq1xnaXkKvXuX" +
            "ot8JxxpfdHSq7G5LdO5Vr3c0fuAhv1JdwwRZ1pvLRlKuPeGDYSaKeZM3kixe" +
            "5ecfy7lK7DxOOmwKbqUaAKU+eJR96os9cS/u3DDSWLMa+uYHUTfSzY3ZosEM" +
            "qsaeiQzLayxu8QqtX6O8DHmCYHrj3tGlhSwSP2WiKODHOZnX+cnDAqUJFAHq" +
            "oKDlGMOwsZ6wecNFXyR9EjqOzvTEPI8VqlFl7gsKezV/FwJkMOgyKBWKQbh/" +
            "GuKOfcfTNvGZhpSPsaFitoOIEShdO1Z1QHFWR6DM5bHp/GSAAoWB9emDAG/n" +
            "C93fvBIf9awTcsuQtkpMk9U3nQkAGsW4IuPfnlYVHlDirg3/OTtFfmhCv/Cs" +
            "Q0y3TYtlVlrLOs/23vEwulerkQsAuMDuH+q79g5zItEPa4N19yD6+pA/NxAb" +
            "ZytRE5Bht9fzKlC8wML9VS7FiospKav23HIF5olmojdrrZ4m+jX55vWl6y5k" +
            "Blu76hit8qwqrQuMDk7n6Sgp1gh9ODI+QJxNr1tdnNILo7CKcsKDdB+6Vcbq" +
            "Plyrnh3lgvhZSYytSLSUrKhGBGD4E1By1lAqIipEIvM6NQ+m/eh+R768vaML" +
            "yPADUgk4fagCAja+OXwaLuMoFd/don85+DUiwY5KiYc5Bb9GFZh1jemO6J6d" +
            "HrII3n/CD7YYdkgiK19ewUnSdjPCM6zOCZZigusJO+7pp2UvefowxB0Nht4Y" +
            "504PmbRXekaLb+mdiu25ndx8ZDkDMWpNRfYpw7UTZ86XC9oNr7EuDMnJfCQN" +
            "1IvitN6vj1YB1yNRkBLFZ80FZ261pQa9UDuGKfANqhMqk8j4s/Go+J0yMBlT" +
            "qRxWHumCMkJGYtJ0+65IVJ0dTfVEyW+UhkLrGZUB2XDSEgSbyX9nMPq9eos+" +
            "GUpdWOJn6QnACuJL3jb4MZuPvQTQkbw3xi+lWkt8NDNZeZGzJDpWpr0Uo1rj" +
            "2iHmQ5cuUs92pD86WzF/sJXiej4Qh1ieU1YYSPfOFBtI0GvFWR2Fi761yVDf" +
            "5Pv+FNr/9VQlYn38PdBY4jfzR05s8fLW3Nlykx0Z7KoCo7wwPgQPDNiXQMAR" +
            "wVtFxS4iDyz7hfL2AG+rLjijvQdecpqu3mTeA29vt+K3+fkuWSmQmpzR0PCg" +
            "0KRw4m6DklmyEzsNLm4Qz/c3h79MGDC9ldWXtDhpnHvEfy33iup3tordAvNu" +
            "C+z/+U7txgBuY9zjuIW7MUW3MV/ufH93ExDfJvBxlwbAqcK5dHz5aUe5/v8V" +
            "hFOVsx19UcXz/c3mtNux9287dij/TjXLyees8i+pPd6R/zdr3hrOt2dLToD9" +
            "0Nl56u/d+voPd/fnrhoLAAA="

        // A lint check compatible with 3.0 which references XmlScanner.ALL
        // (which moved in 3.1)
        private val LINT_XML_SCANNER_ALL_30 = "" +
            "H4sIAAAAAAAAAJ1XeTTUexsfjWjGkt3YkhhZh7FMQsLYxzD2XbbJvhMyqYTs" +
            "GqRrS2QvO2UsJXtZxpIrYZCism9FmN7Ovb3nlnvznvs+5zz//M7n8/k+5/t7" +
            "nud8PxgUkBIMABw7BrCR8UQBvsURwJ8B+pZodWMVcW09DQkgAPMTcDvapU3/" +
            "G8D8W7L8CESr6GlrqBsZw9AaG+jeHl2UOGyQDiUu0t9LrDGUfCk9/W79ea/o" +
            "IJ0OmigGEy59KS5dbajVTxxBlWn1indrj/RQvPgIdc4XdGZfOHojUjEuDmVk" +
            "/zGEEEw4UMCPlVJ+S0dvz78V+SOG9jsGG2zv6eOBPRTLeADr4eoVcCgB8k8E" +
            "Rxeso7v/33jRJQm6FkAA4Do1ACB1CM/oj09IbyesGjYA6xjg7ScAhzl62Pv7" +
            "3zaZ0ufSZCD3Q/qVEpWz2LSG8gsH2rMSjgvt428JevIMy8kaCDimGitWKklc" +
            "nMHNL5YxfkXy7qae5zdAPlDcrDP8Wlj8cXEsAXP997R3k81j6d4Xn6RP7m9U" +
            "PH4KNEfKJoDebla6zDBx04SwGJpXYyIyW6vvmg3VR5rQ5NiLRxK39iJjJNA4" +
            "rN1e9A6NLUQo7CHtQ+PmyqXqe8LIwQATyBf8QpwyAV1eX6T6oTrRh7eG3/Rj" +
            "+kMuiNHuKUtzBCY6YQfuHoQjSW98IgRBCHXmxuDMPXtezfFJ5uuYz3SLc1Fw" +
            "bvnWDk+gNp29XbXIEXL+uJcPoYNTSVtqZMCRjEhRvfo5UjsbT9HIRGmdhmNT" +
            "z1KKQ6oRKlVBerwxWsWlGGupp7gJvGKsGgTi4zeCeTQBLOdMjjnp1yhm6LUf" +
            "f5G7oUPfgnRi4NarhFQWFZRzsLJTvRKNpZd42uCyGhq+4pMS+CYePcF3vq5S" +
            "9NwTPhmgtLORiBskr5I7OAzC2Ctotms/3G7DtVDvPCslhRCEY2fBTxC+U51A" +
            "HNLJrbD7RoL3Gg/eMuASp2XtsJA8QifY5X48HfPUeHAEeLBJ/a3zufUszw1l" +
            "A4TBJTOuRVKfPD1rHTT94xcxku+xN/CweySuQsTLeXYZ/DzNOsdjTPOiP08L" +
            "v+EGQUZyy/tEya0bobI+YylZwrZFyc1dEz10U/3sFtbcCqw4gaJQV3LTcm7u" +
            "lHIgFOe2mc5mNt67sv7CVmKQjBi8FDb7qdnAZb7WUucCzHKsZWtCOHJIKUWW" +
            "nNcDTKqQILu0eEirb+JJNwk1ypaOtYYPRnqo1oF405g8WBFggo+jUAF/bOBz" +
            "eCxxYu4r9Nwx8nmfL046JBCUsONa+ebz+jYREkUzw+33GF9A/ZbpnFBMifCV" +
            "aIqMD2ruoP2KaSDGxsUkb4Ux43T2HhdEb4aOqb8CEsIqqjcspxHbYtQ7IMfD" +
            "pe/ANnotuICP7otNl+aVlhw4J7B87irSr2aVOrkCnD7IHN/cbs3nePfkFlNN" +
            "v4RWk4Z8xfwe6GHvNshjyV/gU0hC0xFZ1deqnIHmwRrbxKrwbLlED2Lbrpyp" +
            "L/DM869CaavwJUFJzSEZphW79O23bCXELpD4tFFGyuZlUqeJpYWTDCNy2vvn" +
            "Qd174fwKehQAUKUDACT/1aB+H1NTzyBmdZawtMIxQY8QbdcQA8mTL2uFbcBC" +
            "Wm6ydBThfB0eVsXF2EaOIfHMyUoFYXWcyj7SkOhqkEgGXE3KOUca83AZjpgV" +
            "PJuenj0RmqNAvDK2szt1nnzEh+Npuy/TsJRSjCNVzottscRj1fB4O1Naglw5" +
            "v8SsBjdc9/h04W/eS5oUjzs++q8F+QKCqDCcLda0QWvqtFgXqTmsWHtldKjU" +
            "clyINu2FTng8tS8usgKL45/NpSywG+/ts2iYh+urXlUZZltnGlJvKylpsPHl" +
            "qcnz66pSHJHyLDeFOFMFWZtUZLo92+AU4+eT93wtmIMfuMNplcrw/Cyr4Izd" +
            "YK1FiYGV/XLTE4dBvbz3kIa64sbCYHoEP25E4C2IF+/5vq6hI8O8U0T6RGjp" +
            "nDcocxToE85dInW3bFR8mMGULSCJeP5BHLOAoWOH7BUbM66J2pd5H3yTE10v" +
            "jvwe0gZtwt1sCdgYG1oq/SgNz4bJe5nYOOI6RtDjRlxOF2y6rR52etxNz62I" +
            "aIvqCVhGTp3KE5vTCU4Fd4x7hvecRThzKUp/QtC1SYF673jzUctRwrjQgSCj" +
            "c/w6EC9+CUm9CR73+4Lz5mwWkjyZxoKk9VGvZZnljEsjpzUtzpxLoU6DToC7" +
            "evYzRvyGjrcMo6Is+ZrZ1k4RYJtuMd0O4uejGjNuS0qiPc4YyZWfpRu1O6OL" +
            "z9RU8Pc2zDJ7xtEkMWP9WEw4I4vQcHuquLF3I/oUhyiY1ji6fVU47JKOneTR" +
            "JIzJrXZXjgdg3Yd4o1COHM/fTNXzp2IeYqEE263khp7kRxsyO91DD0xMCWnG" +
            "q5C0+IwjXInhDZC71wKo6ky63tE82sl/Bn47e9JEXJ/EZ9sdOMh4EfShilaf" +
            "QhPZ2RBe9OqWD9z4BkMXE/dCa0bE2ipld3DhtJViker488amyWLXeNFteiIr" +
            "TiJKelulMwlsO30aZNYqZ3LxLq/DCzxPZQ7Umu7+nqBS3rIskn3SqJX8qGjG" +
            "K1LVmP4yi1icbxebjWbr2MvyN+lWFvzPdO/MbUmOoNwXmykMDLawceqgm/JV" +
            "p0NFEQ7hlnHv1/uCiYWvweVqiU+aZ+rGPMYYvNmbbZ0RBXM7xs41HAuvwKXX" +
            "WW42sCiwyoXIVZihLieA97RKh6+9p55wLQdNYgNFGEASrVuVcwpVG6+asxt6" +
            "VNhvkNeuIvw2DUvll6Vj1b3Krfvn51Ah6PuYqPV7BdmgmMKqwLmMrIG3oWN+" +
            "JZgGDraSAm0tx9y3dEUwy/WlRYdLZ60anlj3u/vTvLMO29HI/a0ISM+qwHl0" +
            "TOl9Dw0eK34m6M4yAukZWzap67YifnK4g3QDFnpKKldvuogKa7zx5uRMDmyH" +
            "262oSJntcpxGvYD3wgIfqiHQiva2WVlY1HAAnRIIVPQ+yLrp2AcSd4Fs8zVX" +
            "RZS+SB9T3/7s+C3bIQfe/qOqypz3ZUTQFLhrLH0ssPiUVMwTBiFN5l3b3JXR" +
            "c68JNq8HlRWWenQHSCG6VLDegbIrvH1ro9FiCUWPB5+ODtlMDWkZiVQKmLpN" +
            "P2OBUZ1AU51QRzBOumOxd87jEVdhgyQsDrJdo7N+RSap8Ayffv7pR5KrnFUF" +
            "nu3Mu4/v6e4lqoBqX7VeF+eemBFb3jUr2CpNSvWwDJldEdrZxScpxYrW6wwB" +
            "t7fRJ8Fy0yg0lB5Kf5opEyLPMtmOH4XY6sR7vHH5miP36OXjmNnXTDzLEY8S" +
            "gmxnJqdOPt28Ue3fu+G0nbFiTLUymHXPhLKPKCyGTOokA39euSRZJ9k0CgBg" +
            "4dvDCv4/V662v38g1hDr7Oof4Bfyfecaor2Z4QxXvJTSIvJdgdfD2AXoXQ2q" +
            "btVDwTcN83UemDq4rvR3vq6Xu1S3UDZ9oc5amXubYk+oPZh5sDRSZ6wteO9T" +
            "c/pazdV98hpzC5fig/vhmrKKGkWJp4M57aAtIsi6ing/YJRh0HxqbpO3ZnIu" +
            "qwh/0vpzTCFvB4Z5iN5TbLNE6/YUsaWa60G8vUT8UkQP0cGvHDr6Qu5EEnZP" +
            "eNaAgueiBex8txoWtj/kWAwdYoW4jk9mr+paH7urUBCixaIfbewQ0WP0ce0T" +
            "fe6FWuaLqZayb/ots9GZifkpVTXC7POx7HSXgaWmSR53O9eb1/kr9hzzzZ6E" +
            "AU0oKX7nNO/SuNZH9aiyb9Uv8T589asumzvUdUdhtM7hM/kLWZ7Ul9iBORuf" +
            "lwLkdKOnhsiPflqyKou595BvITNdKiatn3NFpC1u+aWLuG5r0k1ntcVJArei" +
            "Zrpt8le2liZnFt6BxOEXLmWLYcBGkbBRIVMaH57A2ARhiuPCwHdoMaaeuAvE" +
            "9mdmYjjND+tpuIUoxd+eWx11jql8d8r9KwUGRXGEBfhrl/FnMACWVAA/WomD" +
            "tIOe47+Rf134Fw7ksIMpfzq49Hu3Hcag/YnRf6A/D2My/sT88k+dfRgd8hNd" +
            "lOLXg3FQ5aDZ+OvKHA9R+bX1OKh/8I30lz7t0X//YjqofnAd/KV+Ffx/LAcM" +
            "6ijVny0HAtz8Jun0xw/9Dyg43kUBDwAA"

        @ClassRule
        @JvmField
        var temp = TemporaryFolder()

        init {
            temp.create()
        }

        private val lintJar: File =
            base64gzip("lint1.jar", LINT_JAR_BASE64_GZIP).createFile(temp.root)
        private val oldLintJar: File =
            base64gzip("lint2.jar", LOMBOK_LINT_JAR_BASE64_GZIP).createFile(temp.root)
        private val psiLintJar: File =
            base64gzip("lint3.jar", PSI_LINT_JAR_BASE64_GZIP).createFile(temp.root)
        private val lintJarWithServiceRegistry: File = base64gzip(
            "lint4.jar",
            LINT_JAR_SERVICE_REGISTRY_BASE64_GZIP
        ).createFile(temp.root)
        private val lintApiLevel0: File = base64gzip("lint5.jar", LINT5_JAR).createFile(temp.root)
        private val lintApiLevel1000: File =
            base64gzip("lint6.jar", LINT6_JAR).createFile(temp.root)
        private val lintApiLevel1000min1: File =
            base64gzip("lint7.jar", LINT7_JAR).createFile(temp.root)
        private val lintXmlScannerAll30: File =
            base64gzip("lint8.jar", LINT_XML_SCANNER_ALL_30).createFile(temp.root)
    }
}
