package com.android.tools.lint.checks

import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.GradleModelMocker
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector
import java.io.File
import java.io.IOException

class PrivateResourceDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return PrivateResourceDetector()
    }

    // Sample code
    private val cls = java(
        "src/main/java/test/pkg/Private.java",
        """
        package test.pkg;
        public class Private {
            void test() {
                int x = R.string.my_private_string; // ERROR
                int y = R.string.my_public_string; // OK
            }
        }
        """
    ).indented()

    private val strings = xml(
        "src/main/res/values/strings.xml",
        """
        <resources xmlns:tools="http://schemas.android.com/tools">

            <string tools:override="true" name="my_private_string">String 1</string>
            <string name="my_public_string">String 2</string>

        </resources>
        """
    ).indented()

    private val rFile = createRFile()
    private val publicTxtFile = createPublicResourcesFile()
    private val gradle: TestFile = gradle(
        """
        apply plugin: 'com.android.application'

        dependencies {
            compile 'com.android.tools:test-library:1.0.0'
        }
        """
    )
        .withMockerConfigurator { mocker: GradleModelMocker ->
            mocker.withLibraryPublicResourcesFile(
                "com.android.tools:test-library:1.0.0",
                publicTxtFile.path
            )
            mocker.withLibrarySymbolFile(
                "com.android.tools:test-library:1.0.0", rFile.path
            )
        }

    private fun createPublicResourcesFile(): File {
        return try {
            val tempDir = TestUtils.createTempDirDeletedOnExit().toFile()
            val publicResources = """
                string my_public_string
                style Theme.AppCompat.DayNight

            """.trimIndent()
            val publicTxtFile = File(tempDir, FN_PUBLIC_TXT)
            publicTxtFile.writeText(publicResources)
            publicTxtFile
        } catch (ioe: IOException) {
            fail(ioe.message)
            File("")
        }
    }

    private fun createRFile(): File {
        return try {
            val tempDir = TestUtils.createTempDirDeletedOnExit().toFile()
            val allResources = """
                int string my_private_string 0x7f040000
                int string my_public_string 0x7f040001
                int layout my_private_layout 0x7f040002
                int id title 0x7f040003
                int style Theme_AppCompat_DayNight 0x7f070004
            """.trimIndent()
            val rFile = File(tempDir, FN_RESOURCE_TEXT)
            rFile.writeText(allResources)
            rFile
        } catch (ioe: IOException) {
            fail(ioe.message)
            File("")
        }
    }

    // Sample code
    private val rClass = java(
        "src/main/java/test/pkg/R.java",
        """
        package test.pkg;
        public final class R {
            public static final class string {
                public static final int my_private_string = 0x7f0a0000;
                public static final int my_public_string = 0x7f0a0001;
            }
        }
        """
    ).indented()

    fun testPrivateInXml() {
        val expected =
            """
            src/main/res/layout/private.xml:10: Warning: The resource @string/my_private_string is marked as private in com.android.tools:test-library:1.0.0 [PrivateResource]
                        android:text="@string/my_private_string" />
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            xml(
                "src/main/res/layout/private.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:id="@+id/newlinear"
                              android:orientation="vertical"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/my_private_string" />

                    <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/my_public_string" />
                </LinearLayout>
                """
            ).indented(),
            gradle
        ).run().expect(expected)
    }

    fun testPrivateInJava() {
        val expected =
            """
            src/main/java/test/pkg/Private.java:4: Warning: The resource @string/my_private_string is marked as private in com.android.tools:test-library:1.0.0 [PrivateResource]
                    int x = R.string.my_private_string; // ERROR
                                     ~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            java(
                "src/main/java/test/pkg/Private.java",
                """
                package test.pkg;
                public class Private {
                    void test() {
                        int x = R.string.my_private_string; // ERROR
                        int y = R.string.my_public_string; // OK
                        int z = android.R.string.my_private_string; // OK (not in project namespace)
                    }
                }
                """
            ).indented(),
            rClass,
            gradle
        ).allowCompilationErrors().run().expect(expected)
    }

    fun testStyle() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=221560
        lint().files(
            xml(
                "src/main/res/layout/private2.xml",
                """
                <merge xmlns:android="http://schemas.android.com/apk/res/android">

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:theme="@style/Theme.AppCompat.DayNight" />

                    <View
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:theme="@style/Theme_AppCompat_DayNight" />

                </merge>
                """
            ).indented(),
            gradle
        ).run().expectClean()
    }

    fun testOverride() {
        val expected =
            """
            src/main/res/layout/my_private_layout.xml: Warning: Overriding @layout/my_private_layout which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override="true", otherwise pick a different name. [PrivateResource]
            src/main/res/values/strings.xml:3: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override="true", otherwise pick a different name. [PrivateResource]
                <string name="my_private_string">String 1</string>
                              ~~~~~~~~~~~~~~~~~
            src/main/res/values/strings.xml:7: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override="true", otherwise pick a different name. [PrivateResource]
                <item type="string" name="my_private_string">String 1</item>
                                          ~~~~~~~~~~~~~~~~~
            src/main/res/values/strings.xml:10: Warning: Overriding @string/my_private_string which is marked as private in com.android.tools:test-library:1.0.0. If deliberate, use tools:override="true", otherwise pick a different name. [PrivateResource]
                <string tools:override="false" name="my_private_string">String 2</string>
                                                     ~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        lint().files(
            xml(
                "src/main/res/values/strings.xml",
                """
                <resources xmlns:tools="http://schemas.android.com/tools">
                    <string name="app_name">LibraryProject</string>
                    <string name="my_private_string">String 1</string>
                    <string name="my_public_string">String 2</string>
                    <string name="string3"> @my_private_string </string>
                    <string name="string4"> @my_public_string </string>
                    <item type="string" name="my_private_string">String 1</item>
                    <dimen name="my_private_string">String 1</dimen>
                    <string tools:ignore="PrivateResource" name="my_private_string">String 2</string>
                    <string tools:override="false" name="my_private_string">String 2</string>
                    <string tools:override="true" name="my_private_string">String 2</string>
                </resources>
                """
            ).indented(),
            xml("src/main/res/layout/my_private_layout.xml", "<LinearLayout/>"),
            xml("src/main/res/layout/my_public_layout.xml", "<LinearLayout/>"),
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'com.android.tools:test-library:1.0.0'
                }
                """
            )
                .withMockerConfigurator { mocker: GradleModelMocker ->
                    mocker.withLibraryPublicResourcesFile(
                        "com.android.tools:test-library:1.0.0",
                        publicTxtFile.path
                    )
                    mocker.withLibrarySymbolFile(
                        "com.android.tools:test-library:1.0.0",
                        rFile.path
                    )
                }
        )
            .run()
            .expect(expected)
    }

    fun testIds() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=183851
        lint().files(
            xml(
                "src/main/res/layout/private.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:id="@+id/title"
                              android:orientation="vertical"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"/>
                """
            ),
            java(
                "src/main/java/test/pkg/Private.java",
                """
                package test.pkg;
                public class Private {
                    void test() {
                        int x = R.id.title; // ERROR
                    }
                    public static final class R {
                        public static final class id {
                            public static final int title = 0x7f0a0000;
                        }
                    }
                }
                """
            ).indented(),
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'com.android.tools:test-library:1.0.0'
                }
                """
            )
                .withMockerConfigurator { mocker: GradleModelMocker ->
                    mocker.withLibraryPublicResourcesFile(
                        "com.android.tools:test-library:1.0.0",
                        publicTxtFile.path
                    )
                    mocker.withLibrarySymbolFile(
                        "com.android.tools:test-library:1.0.0",
                        rFile.path
                    )
                }
        )
            .run()
            .expectClean()
    }

    fun testAllowLocalOverrides() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=207152
        // Allow referencing private resources from Java, if
        //   (1) you are not directly referencing the foreign R class, and
        //   (2) you have a local definition of the same resource. (In that case
        //       you also need to mark that local resource as a deliberate override,
        //       but if not you'll get a warning in the XML file where the override is
        //       defined.)
        lint().files(manifest().pkg("test.pkg"), rClass, cls, strings, gradle).run().expectClean()
    }

    fun testAllowLocalOverridesWithResourceRepository() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=207152
        lint().files(manifest().pkg("test.pkg"), rClass, cls, strings, gradle)
            .supportResourceRepository(true)
            .run()
            .expectClean()
    }
}
