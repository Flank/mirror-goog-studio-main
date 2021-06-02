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

class MediaCapabilitiesDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return MediaCapabilitiesDetector()
    }

    @Suppress("LintDocExample")
    fun testCorrect() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            CORRECT_MANIFEST,
            java(
                """
                package test.pkg;

                import android.provider.MediaStore;

                public class MediaStoreVideoUsage {
                    protected void example() {
                        Object obj = MediaStore.Video.Media.INTERNAL_CONTENT_URI;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testCorrect2() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;

                public class MediaStoreVideoUsage {
                    protected void example() {
                        Object obj = android.provider.MediaStore.getMediaScannerUri();
                    }
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testCorrectOldGradle() {
        lint().files(
            GRADLE_VERSION_4_2_0,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;

                import android.provider.MediaStore;

                public class MediaStoreVideoUsage {
                    protected void example() {
                        Object obj = MediaStore.Video.Media.INTERNAL_CONTENT_URI;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testMissingResourceAttr() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST2,
            java(
                """
                package test.pkg;

                import android.provider.MediaStore;

                public class MediaStoreVideoUsage {
                    protected void example() {
                        Object obj = MediaStore.Video.Media.INTERNAL_CONTENT_URI;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/main/AndroidManifest.xml:14: Warning: The android.content.MEDIA_CAPABILITIES <property> tag is missing the android:resource attribute pointing to a valid XML file [MediaCapabilities]
                        <property android:name="android.content.MEDIA_CAPABILITIES"/>
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun testMissingJava() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;

                import android.provider.MediaStore;

                public class MediaStoreVideoUsage {
                    protected void example() {
                        Object obj = MediaStore.Video.Media.INTERNAL_CONTENT_URI;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingJava2() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;

                public class MediaStoreVideoUsage {
                    protected void example() {
                        Object obj = android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI;
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingJava3() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;

                public class MediaStoreVideoUsage {
                    protected void example() {
                         contentResolver.query(
                 android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            );    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingJava4() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;
                import android.provider.MediaStore;

                public class MediaStoreVideoUsage {
                    protected void example() {
                         contentResolver.query(
                            MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            );    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingJava5() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;
                import android.provider.MediaStore.Video;

                public class MediaStoreVideoUsage {
                    protected void example() {
                         contentResolver.query(
                            Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            );    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingJava6() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            java(
                """
                package test.pkg;
                import static android.provider.MediaStore.*;

                public class MediaStoreVideoUsage {
                    protected void example() {
                         contentResolver.query(
                            Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            );    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingKotlin() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            kotlin(
                """
                package test.pkg
                import android.provider.MediaStore

                class MediaStoreVideoUsage {
                    fun example(): Unit {
                        val obj = MediaStore.Video.Media.INTERNAL_CONTENT_URI
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingKotlin2() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            kotlin(
                """
                package test.pkg
                class MediaStoreVideoUsage {
                    fun example(): Unit {
                        val obj = android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingKotlin3() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            kotlin(
                """
                package test.pkg
                class MediaStoreVideoUsage {
                    fun example(): Unit {
                         contentResolver.query(
                 android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            )    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingKotlin4() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            kotlin(
                """
                package test.pkg
                import android.provider.MediaStore

                class MediaStoreVideoUsage {
                    fun example(): Unit {
                         contentResolver.query(
                          MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            )    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingKotlin5() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            kotlin(
                """
                package test.pkg
                import android.provider.MediaStore.Video

                class MediaStoreVideoUsage {
                    fun example(): Unit {
                         contentResolver.query(
                          Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            )    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingKotlin6() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            kotlin(
                """
                package test.pkg
                import android.provider.MediaStore.*

                class MediaStoreVideoUsage {
                    fun example(): Unit {
                         contentResolver.query(
                          Video.Media.INTERNAL_CONTENT_URI,
                            null,
                            null,
                            null,
                            null
                            )    }
                }
                """
            ).indented()
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    fun testMissingClassFile() {
        lint().files(
            GRADLE_VERSION_7_0_0_ALPHA_08,
            WRONG_MANIFEST,
            base64gzip("libs/mylib.jar", BASE64_JAR_WITH_VIDEO_USAGE)
        )
            .run()
            .expect(EXPECTED_LINT_WARNINGS)
    }

    companion object {
        private val CORRECT_MANIFEST = xml(
            "AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg"
                android:versionCode="1"
                android:versionName="1.0" >

                <uses-sdk android:minSdkVersion="14" />

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                    <service
                        android:name=".ExampleHostnameVerifier" >
                    </service>
                <property android:name="android.content.MEDIA_CAPABILITIES"
                    android:resource="@xml/my_resource"/>
                </application>
            </manifest>
            """
        ).indented()

        private val WRONG_MANIFEST = xml(
            "AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg"
                android:versionCode="1"
                android:versionName="1.0" >

                <uses-sdk android:minSdkVersion="14" />

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                    <service
                        android:name=".ExampleHostnameVerifier" >
                    </service>
                </application>

            </manifest>
            """
        ).indented()

        private val WRONG_MANIFEST2 = xml(
            "AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg"
                android:versionCode="1"
                android:versionName="1.0" >

                <uses-sdk android:minSdkVersion="14" />

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                    <service
                        android:name=".ExampleHostnameVerifier" >
                    </service>
                    <property android:name="android.content.MEDIA_CAPABILITIES"/>
                </application>

            </manifest>
            """
        ).indented()

        private val GRADLE_VERSION_4_2_0 = gradle(
            """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:4.2.0'
                }
            }
            """
        ).indented()

        private val GRADLE_VERSION_7_0_0_ALPHA_08 = gradle(
            """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:7.0.0-alpha08'
                }
            }
            """
        ).indented()

        private const val EXPECTED_LINT_WARNINGS =
            "" +
                "src/main/AndroidManifest.xml:8: Warning: The app accesses MediaStore.Video, but is missing a <property> tag with a android.content.MEDIA_CAPABILITIES declaration [MediaCapabilities]\n" +
                "    <application\n" +
                "     ~~~~~~~~~~~\n" +
                "0 errors, 1 warnings"

        private const val BASE64_JAR_WITH_VIDEO_USAGE = "" +
            "H4sIAAAAAAAAAAvwZmbhYgCBnb1dgQxIgIVBhiE5P1c/NISTgXlfwKX480Bc" +
            "WsHNwMji3MHCwBIcz8gQANe9G003D1R3akVibkFOKsSUQ4RMOYhmigiaKYl5" +
            "KUX5mSkQ004RMu0wmmnqOExLzMvLL0ksyczPK05JLShJLS6BWHAWtwUiQOM4" +
            "GBgTugI996RMMWBiYNBhYWAwJd4Cr8SyxBAgQy85J7G4GGzdxv8X429hWjc1" +
            "5L6/sKPAv2nbZKYwc4VsaGJqCnKwDLyf5eOzmHOZSJBXfI3C7d2cMjvvXdub" +
            "G/xfp/bAi+b6hvophe8WXOnoeHf4zJvt98/UWH9//Lt//X0Gm+ObdXTOpBm2" +
            "316s8cBP7HOExOHYO5s3ZCR6Jvxolf/1bKnDqh33BDymT3VnmixqvyRr+VWV" +
            "2OlnGya+7CpKvTDhVv938YRbc35MPFB2NFS3vWrTVb4TExcun/p+I//UicfC" +
            "E58E3g5ImbN9k/ZrmSk75hq6JSyMeHk5VlSKqze4r/b8lCRTzetfWLiuF7EU" +
            "7U7xm7Jdf2vORMWzWZvYVy2OrXp8nOv4b93tfpYbDJsCuU+bvUs+eohZ+vWN" +
            "DRsyHwvKRd/Y9DAh5MDP86839OzYJXziw42b2fMmnb5wx3RR5ZTjKaeeJ/Qq" +
            "iZZ4cq8Ruf6/5izjypTE6IS157Oyr361vXROr3KfjYHX5axmxq2zGJhmpqgn" +
            "vG9RfFmksLijwm5m9+M/flv/7QqrDbXUel8YslZe9loZyzK91JaUOc5vP08R" +
            "zw845nq/3KNe4IXY+iX7V/O0FSdskZebvk7pv4uUz4nJbmapLn47Fysv9d7f" +
            "83u78rRnbFpK3P6s0/LnzD3wJ94i4phBfcBHqaaOeLVtkfcs37D+PnMsvaP/" +
            "5EeJ2w9ZZBrPXrNTT5AR9mz0fDJDVP29cX5Hbd1nHuf5E4T2nJvwr6nnwIJr" +
            "LEE5m98UzzgYElT8zeb9prR5HzPfGQuXP3KfqfJcuJo7+81yy6ijwjZP5js2" +
            "LlbismcM8GZkkmPGlacloCwBhheOIBqSw1khORwjoSNMwszfyCbZQU1C5HZW" +
            "SG7HYyJmXkc2sQXNRETOZ4XkfDwmY+Z7ZJMv4TAZaynACikFsFqGqwyQgFu8" +
            "oNGYkWjL0EsEVkiJgGE1KxvIaFYgvAc0/BIziAcA9BuWBcYFAAA="
    }
}
