/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.scope.CodeShrinker
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests using Proguard/R8 to shrink and obfuscate code in a project with features.
 *
 * Project roughly structured as follows (see implementation below for exact structure) :
 *
 * <pre>
 *   instantApp  --->                --->  library2  ------>
 *                     otherFeature                           library1
 *   app  ---------->                --->  baseFeature  --->
 *
 * More explicitly,
 *   instantApp  depends on  otherFeature, baseFeature
 *          app  depends on  otherFeature, baseFeature
 * otherFeature  depends on  library2, baseFeature
 *  baseFeature  depends on  library1
 *     library2  depends on  library1
 * </pre>
 */
@RunWith(FilterableParameterized::class)
class MinifyFeaturesTest(val codeShrinker: CodeShrinker, useDexArchive: Boolean) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "codeShrinker {0}, useDexArchive={1}")
        fun getConfigurations(): Collection<Array<Any>> =
            listOf(
                arrayOf(CodeShrinker.PROGUARD, true),
                arrayOf(CodeShrinker.PROGUARD, false),
                arrayOf(CodeShrinker.R8, true),
                arrayOf(CodeShrinker.R8, false),
                arrayOf(CodeShrinker.ANDROID_GRADLE, true),
                arrayOf(CodeShrinker.ANDROID_GRADLE, false)
            )
    }


    private val lib1 =
            MinimalSubProject.lib("com.example.lib1")
                    .appendToBuild(
                            "android { buildTypes { minified { initWith(buildTypes.debug) }}}")

    private val lib2 =
            MinimalSubProject.lib("com.example.lib2")
                    .appendToBuild(
                            "android { buildTypes { minified { initWith(buildTypes.debug) }}}")

    private val baseFeature =
            MinimalSubProject.feature("com.example.baseFeature")
                    .appendToBuild("""
                            android {
                                baseFeature true
                                buildTypes {
                                    minified.initWith(buildTypes.debug)
                                    minified {
                                        minifyEnabled true
                                        useProguard ${codeShrinker == CodeShrinker.PROGUARD}
                                        proguardFiles getDefaultProguardFile('proguard-android.txt'),
                                                "proguard-rules.pro"
                                    }
                                }
                            }
                            """)
                    .withFile(
                            "src/main/AndroidManifest.xml",
                            """<?xml version="1.0" encoding="utf-8"?>
                            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                  package="com.example.baseFeature">
                                <application android:label="app_name">
                                    <activity android:name=".Main"
                                              android:label="app_name">
                                        <intent-filter>
                                            <action android:name="android.intent.action.MAIN" />
                                            <category android:name="android.intent.category.LAUNCHER" />
                                        </intent-filter>
                                    </activity>
                                </application>
                            </manifest>""")
                    .withFile(
                            "src/main/res/layout/base_main.xml",
                            """<?xml version="1.0" encoding="utf-8"?>
                            <LinearLayout
                                    xmlns:android="http://schemas.android.com/apk/res/android"
                                    android:orientation="vertical"
                                    android:layout_width="fill_parent"
                                    android:layout_height="fill_parent" >
                                <TextView
                                        android:layout_width="fill_parent"
                                        android:layout_height="wrap_content"
                                        android:text="Test App - Basic"
                                        android:id="@+id/text" />
                                <TextView
                                        android:layout_width="fill_parent"
                                        android:layout_height="wrap_content"
                                        android:text=""
                                        android:id="@+id/dateText" />
                            </LinearLayout>""")
                    .withFile("src/main/resources/base_java_res.txt", "base")
                    .withFile(
                            "src/main/java/com/example/baseFeature/Main.java",
                            """package com.example.baseFeature;

                            import android.app.Activity;
                            import android.os.Bundle;
                            import android.widget.TextView;

                            import java.lang.Exception;
                            import java.lang.RuntimeException;

                            public class Main extends Activity {

                                private int foo = 1234;

                                private final StringProvider stringProvider = new StringProvider();

                                /** Called when the activity is first created. */
                                @Override
                                public void onCreate(Bundle savedInstanceState) {
                                    super.onCreate(savedInstanceState);
                                    setContentView(R.layout.base_main);

                                    TextView tv = (TextView) findViewById(R.id.dateText);
                                    tv.setText(getStringProvider().getString(foo));
                                }

                                public StringProvider getStringProvider() {
                                    return stringProvider;
                                }

                                public void handleOnClick(android.view.View view) {
                                    // This method should be kept by the default ProGuard rules.
                                }
                            }""")
                    .withFile(
                            "src/main/java/com/example/baseFeature/StringProvider.java",
                            """package com.example.baseFeature;

                            public class StringProvider {

                                public String getString(int foo) {
                                    return Integer.toString(foo);
                                }
                            }""")
                    .withFile(
                            "src/main/java/com/example/baseFeature/EmptyClassToKeep.java",
                            """package com.example.baseFeature;
                            public class EmptyClassToKeep {
                            }""")
                    .withFile(
                            "src/main/java/com/example/baseFeature/EmptyClassToRemove.java",
                            """package com.example.baseFeature;
                            public class EmptyClassToRemove {
                            }""")
                    .withFile(
                            "proguard-rules.pro",
                            """-keep public class com.example.baseFeature.EmptyClassToKeep""")

    private val otherFeature =
            MinimalSubProject.feature("com.example.otherFeature")
                    .appendToBuild(
                            "android { buildTypes { minified { initWith(buildTypes.debug) }}}")
                    .withFile(
                            "src/main/AndroidManifest.xml",
                            """<?xml version="1.0" encoding="utf-8"?>
                            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                  package="com.example.otherFeature">
                                <application android:label="app_name">
                                    <activity android:name=".Main"
                                              android:label="app_name">
                                        <intent-filter>
                                            <action android:name="android.intent.action.MAIN" />
                                            <category android:name="android.intent.category.LAUNCHER" />
                                        </intent-filter>
                                    </activity>
                                </application>
                            </manifest>""")
                    .withFile(
                            "src/main/res/layout/other_main.xml",
                            """<?xml version="1.0" encoding="utf-8"?>
                            <LinearLayout
                                    xmlns:android="http://schemas.android.com/apk/res/android"
                                    android:orientation="vertical"
                                    android:layout_width="fill_parent"
                                    android:layout_height="fill_parent" >
                                <TextView
                                        android:layout_width="fill_parent"
                                        android:layout_height="wrap_content"
                                        android:text="Test App - Basic"
                                        android:id="@+id/text" />
                                <TextView
                                        android:layout_width="fill_parent"
                                        android:layout_height="wrap_content"
                                        android:text=""
                                        android:id="@+id/dateText" />
                            </LinearLayout>""")
                    .withFile("src/main/resources/other_java_res.txt", "other")
                    .withFile(
                            "src/main/java/com/example/otherFeature/Main.java",
                            """package com.example.otherFeature;

                            import android.app.Activity;
                            import android.os.Bundle;
                            import android.widget.TextView;

                            import java.lang.Exception;
                            import java.lang.RuntimeException;

                            import com.example.baseFeature.StringProvider;

                            public class Main extends Activity {

                                private int foo = 1234;

                                private final StringProvider stringProvider = new StringProvider();

                                /** Called when the activity is first created. */
                                @Override
                                public void onCreate(Bundle savedInstanceState) {
                                    super.onCreate(savedInstanceState);
                                    setContentView(R.layout.other_main);

                                    TextView tv = (TextView) findViewById(R.id.dateText);
                                    tv.setText(getStringProvider().getString(foo));
                                }

                                public StringProvider getStringProvider() {
                                    return stringProvider;
                                }

                                public void handleOnClick(android.view.View view) {
                                    // This method should be kept by the default ProGuard rules.
                                }
                            }""")
                    .withFile(
                            "src/main/java/com/example/otherFeature/EmptyClassToRemove.java",
                            """package com.example.otherFeature;
                            public class EmptyClassToRemove {
                            }""")

    private val app =
            MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                            "android { buildTypes { minified { initWith(buildTypes.debug) }}}")

    private val instantApp = MinimalSubProject.instantApp()

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .subproject(":baseFeature", baseFeature)
                    .subproject(":foo:otherFeature", otherFeature)
                    .subproject(":app", app)
                    .subproject(":instantApp", instantApp)
                    .dependency(app, otherFeature)
                    .dependency(otherFeature, lib2)
                    .dependency(otherFeature, baseFeature)
                    .dependency(lib2, lib1)
                    .dependency(baseFeature, lib1)
                    .dependency(instantApp, baseFeature)
                    .dependency(instantApp, otherFeature)
                    // Reverse dependencies for the instant app.
                    .dependency("application", baseFeature, app)
                    .dependency("feature", baseFeature, otherFeature)
                    .build()

    @get:Rule
    val project =
            GradleTestProject.builder()
                    .fromTestApp(testApp)
                    .addGradleProperties(
                            "${BooleanOption.ENABLE_DEX_ARCHIVE.propertyName}=$useDexArchive")
                    .create()

    @Test
    fun testApksAreMinified() {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleMinified")

        val baseFeatureApk =
                project.getSubproject(":baseFeature")
                        .getFeatureApk(object: GradleTestProject.ApkType {
                            override fun getBuildType() = "minified"
                            override fun getTestName(): String? = null
                            override fun isSigned() = true
                        })
        assertThat(baseFeatureApk).containsClass("Lcom/example/baseFeature/Main;")
        if (codeShrinker == CodeShrinker.ANDROID_GRADLE) {
            assertThat(baseFeatureApk).containsClass("Lcom/example/baseFeature/StringProvider;")
        } else {
            assertThat(baseFeatureApk).containsClass("Lcom/example/baseFeature/a;")
        }
        assertThat(baseFeatureApk).containsClass("Lcom/example/baseFeature/EmptyClassToKeep;")
        assertThat(baseFeatureApk).containsJavaResource("base_java_res.txt")
        assertThat(baseFeatureApk).containsJavaResource("other_java_res.txt")
        assertThat(baseFeatureApk).doesNotContainClass(
            "Lcom/example/baseFeature/EmptyClassToRemove;")
        assertThat(baseFeatureApk).doesNotContainClass("Lcom/example/otherFeature/Main;")
    }
}

