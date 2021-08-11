package com.android.tools.lint.checks

import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.resources.ResourceUrl
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.GradleModelMocker
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.function.Consumer

class PrivateResourceDetectorTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    fun lint(): TestLintTask {
        return TestLintTask.lint().issues(PrivateResourceDetector.ISSUE).sdkHome(TestUtils.getSdk().toFile())
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

    private val defaultLibraryMocks: Consumer<GradleModelMocker> = createLibraryMocker(
        createLibrary(
            artifact = "com.android.tools:test-library:1.0.0",
            all = listOf(
                "@string/my_private_string",
                "@string/my_public_string",
                "@layout/my_private_layout",
                "@id/title",
                "@style/Theme_AppCompat_DayNight"

            ),
            public = listOf(
                "@string/my_public_string",
                "@style/Theme_AppCompat_DayNight"
            )
        )
    )

    private val defaultRClass: TestFile = rClass(
        "test.pkg",
        "@string/my_private_string",
        "@string/my_public_string"
    )

    private val gradle: TestFile = gradle(
        """
        apply plugin: 'com.android.application'

        dependencies {
            compile 'com.android.tools:test-library:1.0.0'
        }
        """
    ).indented().withMockerConfigurator(defaultLibraryMocks)

    @Test
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

    @Test
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
            defaultRClass,
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
            ).indented().withMockerConfigurator(defaultLibraryMocks)
        ).run().expect(expected)
    }

    @Test
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
            ).indented().withMockerConfigurator(defaultLibraryMocks)
        ).run().expectClean()
    }

    @Test
    fun testAllowLocalOverrides() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=207152
        // Allow referencing private resources from Java, if
        //   (1) you are not directly referencing the foreign R class, and
        //   (2) you have a local definition of the same resource. (In that case
        //       you also need to mark that local resource as a deliberate override,
        //       but if not you'll get a warning in the XML file where the override is
        //       defined.)
        lint().files(manifest().pkg("test.pkg"), defaultRClass, cls, strings, gradle).run().expectClean()
    }

    @Test
    fun testAllowLocalOverridesWithResourceRepository() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=207152
        lint().files(manifest().pkg("test.pkg"), defaultRClass, cls, strings, gradle)
            .supportResourceRepository(true)
            .run()
            .expectClean()
    }

    @Test
    fun test195097935() {
        // Regression test for 195097935: Use of private resources doesn't generate any lint warnings
        lint().files(
            java(
                """
                package com.example.resourcevisibility;

                public class MainActivity extends android.app.Activity {

                  protected void test() {
                    // Expected to be private:
                    int privateResourceId = com.google.android.material.R.anim.abc_fade_in;
                    int privateResourceId2 = R.anim.abc_fade_in;
                    // Expected to be public:
                    int publicResourceId = com.google.android.material.R.style.Animation_Design_BottomSheetDialog;
                    int publicResourceId2 = R.style.Animation_Design_BottomSheetDialog;
                  }
                }
                """
            ).indented(),
            rClass(
                "com.example.resourcevisibility",
                "@anim/abc_fade_in",
                "@style/Animation_Design_BottomSheetDialog"
            ),
            rClass(
                "com.google.android.material",
                "@anim/abc_fade_in",
                "@style/Animation_Design_BottomSheetDialog"
            ),
            gradle(
                """
                apply plugin: 'com.android.application'
                dependencies {
                    implementation 'com.google.android.material:material:1.4.0'
                    implementation 'androidx.activity:activity:1.3.1'
                    implementation 'androidx.appcompat:appcompat:1.3.1'
                }
                """
            ).indented().withMockerConfigurator(
                createLibraryMocker(
                    createLibrary(
                        artifact = "com.google.android.material:material:1.4.0",
                        all = listOf(
                            "@attr/showMotionSpec",
                            "@anim/abc_fade_in",
                            "@anim/abc_tooltip_enter",
                            "@style/Animation_Design_BottomSheetDialog"
                        ),
                        public = listOf(
                            "@attr/showMotionSpec",
                            "@style/Animation_Design_BottomSheetDialog"
                        )
                    ),
                    createLibrary(
                        artifact = "androidx.appcompat:appcompat:1.3.1",
                        all = listOf(
                            "@attr/autoCompleteTextViewStyle",
                            "@anim/abc_fade_in",
                            "@drawable/abc_edit_text_material"
                        ),
                        public = listOf(
                            "@attr/autoCompleteTextViewStyle"
                        )
                    ),
                    createLibrary(
                        artifact = "androidx.activity:activity:1.3.1",
                        all = listOf(
                            "@color/ripple_material_light",
                        )
                        // no public resources
                    )
                )
            )
        ).run().expect(
            """
            src/main/java/com/example/resourcevisibility/MainActivity.java:7: Warning: The resource @anim/abc_fade_in is marked as private in com.google.android.material:material:1.4.0 [PrivateResource]
                int privateResourceId = com.google.android.material.R.anim.abc_fade_in;
                                                                           ~~~~~~~~~~~
            src/main/java/com/example/resourcevisibility/MainActivity.java:8: Warning: The resource @anim/abc_fade_in is marked as private in com.google.android.material:material:1.4.0 [PrivateResource]
                int privateResourceId2 = R.anim.abc_fade_in;
                                                ~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    private fun createAllSymbolsFile(artifact: String, vararg resources: String): File {
        val file = File(temporaryFolder.root, artifact.replace(':', '_') + "/" + FN_RESOURCE_TEXT)
        var id = 0x7f040000
        file.parentFile?.mkdirs()
        file.writeText(
            resources.map { it.toUrl() }.joinToString("\n") {
                "int ${it.type} ${it.name} 0x${Integer.toHexString(id++)}"
            }
        )
        return file
    }

    private fun createPublicSymbolsFile(artifact: String, vararg resources: String): File {
        val file = File(temporaryFolder.root, artifact.replace(':', '_') + "/" + FN_PUBLIC_TXT)
        // Note that we always return a path but don't create the
        // file if it's empty; this matches the behavior of AGP
        if (resources.isNotEmpty()) {
            file.parentFile?.mkdirs()
            file.writeText(
                resources.map { it.toUrl() }.joinToString("\n") {
                    "${it.type} ${it.name}"
                }
            )
        }
        return file
    }

    private fun String.toUrl(): ResourceUrl = ResourceUrl.parse(this) ?: error("Invalid resource reference $this")

    private fun createLibrary(artifact: String, all: List<String>, public: List<String> = emptyList()):
        Triple<String, List<String>, List<String>> =
            Triple(artifact, all, public)

    private fun createLibraryMocker(
        vararg libraries: Triple<String, List<String>, List<String>>
    ): Consumer<GradleModelMocker> {
        return Consumer { mocker: GradleModelMocker ->
            for (library in libraries) {
                val (artifact, all, public) = library
                mocker.withLibraryPublicResourcesFile(
                    artifact,
                    createPublicSymbolsFile(artifact, *public.toTypedArray()).path
                )
                mocker.withLibrarySymbolFile(
                    artifact,
                    createAllSymbolsFile(artifact, *all.toTypedArray()).path
                )
            }
        }
    }
}
