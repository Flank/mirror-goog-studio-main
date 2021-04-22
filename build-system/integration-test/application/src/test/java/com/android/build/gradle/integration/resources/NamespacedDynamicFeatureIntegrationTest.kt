
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
package com.android.build.gradle.integration.resources
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.apk.Dex
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.Opcodes

/**
 * Tests the new namespaced resource pipeline for a project with multi-level dynamic features.
 *
 * Project roughly structured as follows (see implementation below for exact structure) :
 *
 * <pre>
 *                  ---------->  library3  -------->
 *   otherFeature2                                    library2
 *                  ---------->                 --->            ------>
 *                               otherFeature1                           library1
 *                                              --->  application  --->
 *
 *
 * More explicitly,
 *        otherFeature2  depends on  library3, otherFeature1, application
 *        otherFeature1  depends on  library2, application
 *          application  depends on  library1
 *             library3  depends on  library2
 *             library2  depends on  library1
 * </pre>
 */
class NamespacedDynamicFeatureIntegrationTest {
    private val lib1 = MinimalSubProject.lib("com.example.lib1")
        .appendToBuild("android.aaptOptions.namespaced = true\n")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources><string name="lib1String">Lib1 string</string></resources>""")
        .withFile(
            "src/main/java/com/example/lib1/Example.java",
            """package com.example.lib1;
                    public class Example {
                        public static int lib1() { return R.string.lib1String; }
                    }""")
        .withFile(
            "src/main/res/drawable/dot.xml",
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="24dp"
                        android:height="24dp"
                        android:viewportWidth="24.0"
                        android:viewportHeight="24.0">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="M12,12m-10,0a10,10 0,1 1,20 0a10,10 0,1 1,-20 0"/>
                    </vector>""")
        .withFile(
            "src/main/res/values/public.xml",
            """<resources>
                        <public type="string" name="lib1String" />
                    </resources>""")
    private val lib2 = MinimalSubProject.lib("com.example.lib2")
        .appendToBuild("android.aaptOptions.namespaced = true\n")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="lib2String">Lib2 String</string>
                        <string name="lib1String">@com.example.lib1:string/lib1String</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/lib2/Example.java",
            """package com.example.lib2;
                    public class Example {
                        public static int lib2() { return R.string.lib2String; }
                        public static int lib1() { return com.example.lib1.R.string.lib1String; }
                    }""")
        .withFile(
            "src/main/res/drawable/dot.xml",
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="24dp"
                        android:height="24dp"
                        android:viewportWidth="24.0"
                        android:viewportHeight="24.0">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="M12,12m-10,0a10,10 0,1 1,20 0a10,10 0,1 1,-20 0"/>
                    </vector>""")
        .withFile(
            "src/main/res/values/public.xml",
            """<resources>
                        <public type="string" name="lib2String" />
                        <public type="string" name="lib1String" />
                    </resources>""")
    private val lib3 = MinimalSubProject.lib("com.example.lib3")
        .appendToBuild("android.aaptOptions.namespaced = true\n")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="lib3String">Lib3 String</string>
                        <string name="lib2String">@com.example.lib2:string/lib2String</string>
                        <string name="lib1String">@com.example.lib2:string/lib1String</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/lib3/Example.java",
            """package com.example.lib3;
                    public class Example {
                        public static int lib3() { return R.string.lib3String; }
                        public static int lib2() { return com.example.lib2.R.string.lib2String; }
                        public static int lib1() { return com.example.lib2.R.string.lib1String; }
                    }""")
        .withFile(
            "src/main/res/drawable/dot.xml",
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="24dp"
                        android:height="24dp"
                        android:viewportWidth="24.0"
                        android:viewportHeight="24.0">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="M12,12m-10,0a10,10 0,1 1,20 0a10,10 0,1 1,-20 0"/>
                    </vector>""")
        .withFile(
            "src/main/res/values/colors.xml",
            """<resources>
                        <color name="lib3Red">#F00</color>
                    </resources>""")
        .withFile(
            "src/main/res/values/attrs.xml",
            """<resources>
                        <attr name="lib3Red" format="reference" />
                    </resources>""")
        .withFile(
            "src/main/res/values/public.xml",
            """<resources>
                        <public type="string" name="lib3String" />
                        <public type="string" name="lib1String" />
                        <public type="attr" name="lib3Red" />
                    </resources>""")
    private val application = MinimalSubProject.app("com.example.application")
        .appendToBuild("android.aaptOptions.namespaced = true\n" +
                "android.dynamicFeatures = [\":otherFeature1\", \":otherFeature2\"]")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="applicationString">application String</string>
                        <string name="lib1String">@com.example.lib1:string/lib1String</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/application/Example.java",
            """package com.example.application;
                    public class Example {
                        public static int application() { return R.string.applicationString; }
                        public static int lib1() { return com.example.lib1.R.string.lib1String; }
                    }
                    """)
        .withFile(
            "src/main/res/values/public.xml",
            """<resources>
                        <public type="string" name="applicationString" />
                    </resources>""")
    private val otherFeature1 = MinimalSubProject.dynamicFeature("com.example.otherFeature1")
        .appendToBuild("android.aaptOptions.namespaced = true\n")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="otherFeature1String">Other Feature 1 String</string>
                        <string name="applicationString">@com.example.application:string/applicationString</string>
                        <string name="lib2String">@com.example.lib2:string/lib2String</string>
                        <string name="lib1String">@com.example.lib2:string/lib1String</string>
                    </resources>""")
        .withFile(
            "src/main/res/values/styles.xml",
            """<resources>
                        <style name="otherFeature1Text" parent="android:TextAppearance">
                            <item name="android:textSize">20sp</item>
                        </style>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/otherFeature1/Example.java",
            """package com.example.otherFeature1;
                    public class Example {
                        public static int otherFeature1() { return R.string.otherFeature1String; }
                        public static int application() {
                            return com.example.application.R.string.applicationString; }
                        public static int lib2() { return com.example.lib2.R.string.lib2String; }
                        public static int lib1() { return R.string.lib1String; }
                    }
                    """)
        .withFile(
            "src/main/res/values/public.xml",
            """<resources>
                        <public type="string" name="otherFeature1String" />
                        <public type="string" name="applicationString" />
                        <public type="string" name="lib2String" />
                        <public type="style" name="otherFeature1Text" />
                    </resources>""")
    private val otherFeature2 = MinimalSubProject.dynamicFeature("com.example.otherFeature2")
        .appendToBuild("android.aaptOptions.namespaced = true\n")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources xmlns:lib3="http://schemas.android.com/apk/res/com.example.lib3">
                        <string name="otherFeature2String">Other Feature 2 String</string>
                        <string name="otherFeature1String">@com.example.otherFeature1:string/otherFeature1String</string>
                        <string name="applicationString">@com.example.otherFeature1:string/applicationString</string>
                        <string name="lib3String">@lib3:string/lib3String</string>
                        <string name="lib2String">@com.example.otherFeature1:string/lib2String</string>
                        <string name="lib1String">@lib3:string/lib1String</string>
                    </resources>""")
        .withFile(
            "src/main/res/values/styles.xml",
            """<resources>
                            <style name="otherFeature2Text" parent="@com.example.otherFeature1:style/otherFeature1Text">
                                <item name="android:textColor">?com.example.lib3:attr/lib3Red</item>
                            </style>
                        </resources>""")
        .withFile(
            "src/main/java/com/example/otherFeature2/Example.java",
            """package com.example.otherFeature2;
                    public class Example {
                        public static int otherFeature2() { return R.string.otherFeature2String; }
                        public static int otherFeature1() { return com.example.otherFeature1.R.string.otherFeature1String; }
                        public static int application() { return R.string.applicationString; }
                        public static int lib3() { return com.example.lib3.R.string.lib3String; }
                        public static int lib2() { return R.string.lib2String; }
                        public static int lib1() { return R.string.lib1String; }
                        public static int lib3Red() { return com.example.lib3.R.attr.lib3Red; }
                    }
                    """)
        .withFile(
            "src/main/res/values/public.xml",
            """<resources>
                        <public type="string" name="otherFeature2String" />
                        <public type="string" name="lib1String" />
                    </resources>""")
    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib1", lib1)
            .subproject(":lib2", lib2)
            .subproject(":lib3", lib3)
            .subproject(":application", application)
            .subproject(":otherFeature1", otherFeature1)
            .subproject(":otherFeature2", otherFeature2)
            .dependency(otherFeature2, otherFeature1)
            .dependency(otherFeature2, application)
            .dependency(otherFeature2, lib3)
            .dependency(lib3, lib2)
            .dependency(otherFeature1, lib2)
            .dependency(otherFeature1, application)
            .dependency(lib2, lib1)
            .dependency(application, lib1)
            .build()
    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp)
        .create()

    @Test
    fun testApkContentsAndPackageIds() {
        project.executor()
            .run(
                ":otherFeature1:assembleDebug",
                ":otherFeature1:assembleDebugAndroidTest",
                ":otherFeature2:assembleDebug",
                ":otherFeature2:assembleDebugAndroidTest",
                ":app:assembleDebug",
                ":app:assembleDebugAndroidTest")
        val lib1DotDrawablePath = "res/drawable/com.example.lib1\$dot.xml"
        val lib2DotDrawablePath = "res/drawable/com.example.lib2\$dot.xml"
        val lib3DotDrawablePath = "res/drawable/com.example.lib3\$dot.xml"
        project.getSubproject(":otherFeature1")
            .getApk(GradleTestProject.ApkType.DEBUG)
            .use { apk ->
                assertThat(apk).exists()
                assertThat(apk).doesNotContain(lib1DotDrawablePath)
                assertThat(apk).contains(lib2DotDrawablePath)
                assertThat(apk).containsClass("Lcom/example/otherFeature1/R;")
                // TODO(b/72695265): Reinstate these assertions
                //  assertThat(apk).containsClass("Lcom/example/otherFeature1/R\$string;")
                //  assertThat(apk.mainDexFile.get().getFields("Lcom/example/otherFeature1/R\$string;"))
                //      .containsExactly(
                //          "public static final I otherFeature1String",
                //          "public static final I applicationString",
                //          "public static final I lib2String",
                //          "public static final I lib1String"
                //      )
                assertThat(apk).doesNotContainClass("Lcom/example/lib1/R;")
                assertThat(apk).doesNotContainClass("Lcom/example/lib1/R\$string;")
                assertThat(apk).doesNotContainClass("Lcom/example/application/R;")
                assertThat(apk).doesNotContainClass("Lcom/example/application/R\$string;")
                assertThat(apk).containsClass("Lcom/example/lib2/R;")
                assertThat(apk).containsClass("Lcom/example/lib2/R\$string;")
            }
        project.getSubproject(":otherFeature2")
            .getApk(GradleTestProject.ApkType.DEBUG)
            .use { apk ->
                assertThat(apk).exists()
                assertThat(apk).doesNotContain(lib1DotDrawablePath)
                assertThat(apk).doesNotContain(lib2DotDrawablePath)
                assertThat(apk).contains(lib3DotDrawablePath)
                assertThat(apk).containsClass("Lcom/example/otherFeature2/R;")
                // TODO(b/72695265): Reinstate these assertions
                //  assertThat(apk).containsClass("Lcom/example/otherFeature2/R\$string;")
                //  assertThat(apk.mainDexFile.get().getFields("Lcom/example/otherFeature2/R\$string;"))
                //      .containsExactly(
                //          "public static final I otherFeature2String",
                //          "public static final I otherFeature1String",
                //          "public static final I applicationString",
                //          "public static final I lib3String",
                //          "public static final I lib2String",
                //          "public static final I lib1String"
                //      )
                assertThat(apk).doesNotContainClass("Lcom/example/lib1/R;")
                assertThat(apk).doesNotContainClass("Lcom/example/lib1/R\$string;")
                assertThat(apk).doesNotContainClass("Lcom/example/lib2/R;")
                assertThat(apk).doesNotContainClass("Lcom/example/lib2/R\$string;")
                assertThat(apk).doesNotContainClass("Lcom/example/application/R;")
                assertThat(apk).doesNotContainClass("Lcom/example/application/R\$string;")
                assertThat(apk).doesNotContainClass("Lcom/example/otherFeature1/R;")
                assertThat(apk).doesNotContainClass("Lcom/example/otherFeature1/R\$string;")
                assertThat(apk).containsClass("Lcom/example/lib3/R;")
                assertThat(apk).containsClass("Lcom/example/lib3/R\$string;")
            }
        // check the base feature declared the list of features and their associated IDs.
        val idsList =
            project.getSubproject(":application")
                .getIntermediateFile(
                    "feature_set_metadata",
                    "debug",
                    "feature-metadata.json")
        assertThat(idsList).exists()
        val featureSetMetadata = FeatureSetMetadata.load(idsList)
        assertThat(featureSetMetadata).isNotNull()
        val otherFeature1PackageId = featureSetMetadata.getResOffsetFor(":otherFeature1")
        val otherFeature2PackageId = featureSetMetadata.getResOffsetFor(":otherFeature2")
        assertThat(otherFeature1PackageId).isAtMost(FeatureSetMetadata.BASE_ID)
        assertThat(otherFeature2PackageId).isAtMost(FeatureSetMetadata.BASE_ID)
        assertThat(otherFeature1PackageId).isNotEqualTo(otherFeature2PackageId)
        // TODO: check that resourceIds use correct packageIds - manually tested this.
    }
    private val modifierToString =
        mapOf(
            Opcodes.ACC_PUBLIC to "public",
            Opcodes.ACC_STATIC to "static",
            Opcodes.ACC_FINAL to "final")
    private fun modifiers(accessFlags: Int): String {
        val modifiers = ArrayList<String>()
        var runningFlags = accessFlags
        modifierToString.forEach {
                value, string ->
            if (runningFlags and value != 0) {
                modifiers += string
                runningFlags = runningFlags and value.inv()
            }
        }
        if (runningFlags != 0) {
            throw IllegalArgumentException("Unexpected flags, %2x".format(runningFlags))
        }
        return modifiers.joinToString(" ")
    }
    private fun Dex.getFields(className: String): List<String> {
        return classes[className]!!
            .fields
            .map { modifiers(it.accessFlags) + " " + it.type + " " + it.name }
    }
}
