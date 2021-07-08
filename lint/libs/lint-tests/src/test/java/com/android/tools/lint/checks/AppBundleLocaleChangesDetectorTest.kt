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

import com.android.tools.lint.detector.api.Detector

class AppBundleLocaleChangesDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return AppBundleLocaleChangesDetector()
    }

    fun testJava1() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.res.Configuration;
                import java.util.Locale;

                public class Example {
                    protected void example(Configuration configuration, Locale locale) {
                        configuration.locale = locale;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/Example.java:8: Warning: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the bundle configuration [AppBundleLocaleChanges]
                        configuration.locale = locale;
                                      ~~~~~~
                0 errors, 1 warnings
              """
            )
    }

    fun testJava2() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.res.Configuration;
                import java.util.Locale;

                public class Example {
                    protected void example(Configuration configuration, Locale locale) {
                        configuration.setLocale(locale);
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/Example.java:8: Warning: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the bundle configuration [AppBundleLocaleChanges]
                        configuration.setLocale(locale);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun testJava3() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.res.Configuration;
                import android.os.LocaleList;

                public class Example {
                    protected void example(Configuration configuration, LocaleList locales) {
                        configuration.setLocales(locales);
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/Example.java:8: Warning: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the bundle configuration [AppBundleLocaleChanges]
                        configuration.setLocales(locales);
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
               """
            )
    }

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                import android.content.res.Configuration
                import java.util.Locale

                fun example(configuration: Configuration, locale: Locale) {
                    configuration.locale = locale
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test.kt:5: Warning: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the bundle configuration [AppBundleLocaleChanges]
                    configuration.locale = locale
                                  ~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun testKotlin2() {
        lint().files(
            kotlin(
                """
                import android.content.res.Configuration
                import java.util.Locale

                fun example(configuration: Configuration, locale: Locale) {
                    configuration.setLocale(locale)
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test.kt:5: Warning: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the bundle configuration [AppBundleLocaleChanges]
                    configuration.setLocale(locale)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun testKotlin3() {
        lint().files(
            kotlin(
                """
                import android.content.res.Configuration
                import android.os.LocaleList

                fun example(configuration: Configuration, locales: LocaleList) {
                    configuration.setLocales(locales)
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test.kt:5: Warning: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the bundle configuration [AppBundleLocaleChanges]
                    configuration.setLocales(locales)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun testJavaPlayCoreUsage() {
        lint().files(
            PLAYCORE_FILE2,
            java(
                """
                package test.pkg;

                import android.content.res.Configuration;
                import java.util.Locale;
                import com.google.android.play.core.splitinstall.SplitInstallRequest;

                public class Example {
                    protected void example(Configuration configuration, Locale locale) {
                        configuration.locale = locale;
                        new SplitInstallRequest.Builder().addLanguage(locale).build();
                    }
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testKotlinPlayCoreUsage() {
        lint().files(
            PLAYCORE_FILE2,
            kotlin(
                """
                import android.content.res.Configuration
                import java.util.Locale
                import com.google.android.play.core.splitinstall.SplitInstallRequest

                fun example(configuration: Configuration, locale: Locale) {
                    configuration.setLocale(locale)
                    SplitInstallRequest.Builder().addLanguage(locale).build()
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testKotlinPlayCoreUsage2() {
        lint().files(
            PLAYCORE_FILE,
            PLAYCORE_KTX_FILE,
            kotlin(
                """
                import android.content.res.Configuration
                import java.util.Locale
                import com.google.android.play.core.splitinstall.SplitInstallManager
                import com.google.android.play.core.ktx.requestInstall

                suspend fun example(
                    mgr: SplitInstallManager,
                    configuration: Configuration,
                    locale: Locale
                ) {
                    configuration.setLocale(locale)
                    mgr.requestInstall(listOf(), listOf("en"))
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testKotlinPlayCoreUsage3() {
        lint().files(
            PLAYCORE_FILE,
            PLAYCORE_KTX_FILE,
            kotlin(
                """
                import android.content.res.Configuration
                import java.util.Locale
                import com.google.android.play.core.splitinstall.SplitInstallManager
                import com.google.android.play.core.ktx.requestInstall

                suspend fun example(
                    mgr: SplitInstallManager,
                    configuration: Configuration,
                    locale: Locale
                ) {
                    configuration.setLocale(locale)
                    mgr.requestInstall(languages = listOf("en"))
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testLanguageSplitsDisabled() {
        lint().files(
            GRADLE_LANGUAGES_SPLIT_DISABLED,
            java(
                """
                package test.pkg;

                import android.content.res.Configuration;
                import java.util.Locale;

                public class Example {
                    protected void example(Configuration configuration, Locale locale) {
                        configuration.locale = locale;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    companion object {
        private val GRADLE_LANGUAGES_SPLIT_DISABLED = gradle(
            """
                android {
                    bundle {
                        language {
                            enableSplit = false
                        }
                    }
                }
            """
        ).indented()

        private val PLAYCORE_FILE = java(
            """
                package com.google.android.play.core.splitinstall;

                public class SplitInstallManager { }

                public class SplitInstallRequest {
                    public static class Builder {
                        Builder addLanguage(Locale locale) {
                            return this;
                        }
                    }
                    public void build() { }
                }
            """
        ).indented()

        private val PLAYCORE_FILE2 = java(
            """
                package com.google.android.play.core.splitinstall;

                public class SplitInstallRequest {
                    public static class Builder {
                        Builder addLanguage(Locale locale) {
                            return this;
                        }
                        public void build() { }
                    }
                }
            """
        ).indented()

        private val PLAYCORE_KTX_FILE = kotlin(
            """
                package com.google.android.play.core.ktx

                import com.google.android.play.core.splitinstall.SplitInstallManager

                suspend fun SplitInstallManager.requestInstall(
                  modules: List<String> = listOf(),
                  languages: List<String> = listOf()
                ): Int = 0
            """
        ).indented()
    }
}
