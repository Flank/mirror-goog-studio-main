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
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds
import com.android.testutils.apk.Dex
import com.android.testutils.truth.FileSubject
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.Opcodes

/**
 * Tests the new namespaced resource pipeline for a project with many features.
 *
 * Project roughly structured as follows (see coded dependencies below for exact structure) :
 *
 * <pre>
 *   instantApp  --->                 --->  library3  -------->
 *                     otherFeature2                             library2
 *        ---------->                 --->                 --->            ------>
 *   app                                    otherFeature1                           library1
 *        ------------------------------->                 --->  baseFeature  --->
 * </pre>
 */
class NamespacedFeaturesTest {

    // TODO: add a non-namespaced feature to project?

    private val buildScriptContent = """
        android.aaptOptions.namespaced = true
    """

    private val lib1 = MinimalSubProject.lib("com.example.lib1")
            .appendToBuild(buildScriptContent)
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
            .appendToBuild(buildScriptContent)
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
            .appendToBuild(buildScriptContent)
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

    private val baseFeature = MinimalSubProject.feature("com.example.baseFeature")
            .appendToBuild(buildScriptContent + "\nandroid.baseFeature true")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="baseFeatureString">baseFeature String</string>
                        <string name="lib1String">@com.example.lib1:string/lib1String</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/baseFeature/Example.java",
                    """package com.example.baseFeature;
                    public class Example {
                        public static int baseFeature() { return R.string.baseFeatureString; }
                        public static int lib1() { return com.example.lib1.R.string.lib1String; }
                    }
                    """)
            .withFile(
                    "src/main/res/values/public.xml",
                    """<resources>
                        <public type="string" name="baseFeatureString" />
                    </resources>""")

    private val otherFeature1 = MinimalSubProject.feature("com.example.otherFeature1")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="otherFeature1String">Other Feature 1 String</string>
                        <string name="baseFeatureString">@com.example.baseFeature:string/baseFeatureString</string>
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
                        public static int baseFeature() {
                            return com.example.baseFeature.R.string.baseFeatureString; }
                        public static int lib2() { return com.example.lib2.R.string.lib2String; }
                        public static int lib1() { return R.string.lib1String; }
                    }
                    """)
            .withFile(
                    "src/main/res/values/public.xml",
                    """<resources>
                        <public type="string" name="otherFeature1String" />
                        <public type="string" name="baseFeatureString" />
                        <public type="string" name="lib2String" />
                        <public type="style" name="otherFeature1Text" />
                    </resources>""")


    private val otherFeature2 = MinimalSubProject.feature("com.example.otherFeature2")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources xmlns:lib3="http://schemas.android.com/apk/res/com.example.lib3">
                        <string name="otherFeature2String">Other Feature 2 String</string>
                        <string name="otherFeature1String">@com.example.otherFeature1:string/otherFeature1String</string>
                        <string name="baseFeatureString">@com.example.otherFeature1:string/baseFeatureString</string>
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
                        public static int baseFeature() { return R.string.baseFeatureString; }
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

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="appString">App string</string>
                        <string name="otherFeature2String">@com.example.otherFeature2:string/otherFeature2String</string>
                        <string name="lib1String">@com.example.otherFeature2:string/lib1String</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/app/Example.java",
                    """package com.example.app;
                    public class Example {
                        public static final int app() { return R.string.appString; }
                        public static final int otherFeature2() {
                            return com.example.otherFeature2.R.string.otherFeature2String; }
                        public static final int lib1() { return R.string.lib1String; }
                    }""")

    private val instantApp = MinimalSubProject.instantApp()

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .subproject(":lib3", lib3)
                    .subproject(":baseFeature", baseFeature)
                    .subproject(":otherFeature1", otherFeature1)
                    .subproject(":otherFeature2", otherFeature2)
                    .subproject(":app", app)
                    .subproject(":instantApp", instantApp)
                    .dependency(app, otherFeature1)
                    .dependency(app, otherFeature2)
                    .dependency(otherFeature2, otherFeature1)
                    // TODO: (b/73948401) get rid of direct dependency of otherFeature2 on baseFeature.
                    .dependency(otherFeature2, baseFeature)
                    .dependency(otherFeature2, lib3)
                    .dependency(lib3, lib2)
                    .dependency(otherFeature1, lib2)
                    .dependency(otherFeature1, baseFeature)
                    .dependency(lib2, lib1)
                    .dependency(baseFeature, lib1)
                    .dependency(instantApp, baseFeature)
                    .dependency(instantApp, otherFeature1)
                    .dependency(instantApp, otherFeature2)
                    // Reverse dependencies for the instant app.
                    .dependency("application", baseFeature, app)
                    .dependency("feature", baseFeature, otherFeature1)
                    .dependency("feature", baseFeature, otherFeature2)
                    .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testApkContentsAndPackageIds() {
        AssumeUtil.assumeNotWindowsBot() // https://issuetracker.google.com/70931936
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

        val otherFeature1Apk =
                project.getSubproject(":otherFeature1")
                        .getFeatureApk(GradleTestProject.ApkType.DEBUG)
        assertThat(otherFeature1Apk).exists()
        assertThat(otherFeature1Apk).doesNotContain(lib1DotDrawablePath)
        assertThat(otherFeature1Apk).contains(lib2DotDrawablePath)
        assertThat(otherFeature1Apk).containsClass("Lcom/example/otherFeature1/R;")
        assertThat(otherFeature1Apk).containsClass("Lcom/example/otherFeature1/R\$string;")
        assertThat(otherFeature1Apk.mainDexFile.get().getFields("Lcom/example/otherFeature1/R\$string;"))
            .containsExactly(
                "public static final I otherFeature1String",
                "public static final I baseFeatureString",
                "public static final I lib2String",
                "public static final I lib1String")
        assertThat(otherFeature1Apk).doesNotContainClass("Lcom/example/lib1/R;")
        assertThat(otherFeature1Apk).doesNotContainClass("Lcom/example/lib1/R\$string;")
        assertThat(otherFeature1Apk).doesNotContainClass("Lcom/example/baseFeature/R;")
        assertThat(otherFeature1Apk).doesNotContainClass("Lcom/example/baseFeature/R\$string;")
        assertThat(otherFeature1Apk).containsClass("Lcom/example/lib2/R;")
        assertThat(otherFeature1Apk).containsClass("Lcom/example/lib2/R\$string;")

        val otherFeature2Apk =
                project.getSubproject(":otherFeature2")
                        .getFeatureApk(GradleTestProject.ApkType.DEBUG)
        assertThat(otherFeature2Apk).exists()
        assertThat(otherFeature2Apk).doesNotContain(lib1DotDrawablePath)
        assertThat(otherFeature2Apk).doesNotContain(lib2DotDrawablePath)
        assertThat(otherFeature2Apk).contains(lib3DotDrawablePath)
        assertThat(otherFeature2Apk).containsClass("Lcom/example/otherFeature2/R;")
        assertThat(otherFeature2Apk).containsClass("Lcom/example/otherFeature2/R\$string;")
        assertThat(otherFeature2Apk.mainDexFile.get().getFields("Lcom/example/otherFeature2/R\$string;"))
            .containsExactly(
                "public static final I otherFeature2String",
                "public static final I otherFeature1String",
                "public static final I baseFeatureString",
                "public static final I lib3String",
                "public static final I lib2String",
                "public static final I lib1String")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/lib1/R;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/lib1/R\$string;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/lib2/R;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/lib2/R\$string;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/baseFeature/R;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/baseFeature/R\$string;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/otherFeature1/R;")
        assertThat(otherFeature2Apk).doesNotContainClass("Lcom/example/otherFeature1/R\$string;")
        assertThat(otherFeature2Apk).containsClass("Lcom/example/lib3/R;")
        assertThat(otherFeature2Apk).containsClass("Lcom/example/lib3/R\$string;")


        // check the base feature declared the list of features and their associated IDs.
        val idsList =
                project.getSubproject(":baseFeature")
                        .getIntermediateFile(
                                "feature-split", "ids", "feature", "debug", "package_ids.json")
        FileSubject.assertThat(idsList).exists()
        val packageIds = FeatureSplitPackageIds.load(idsList)
        assertThat(packageIds).isNotNull()
        val otherFeature1PackageId = packageIds.getIdFor(":otherFeature1")
        val otherFeature2PackageId = packageIds.getIdFor(":otherFeature2")
        assertThat(otherFeature1PackageId).isAtLeast(FeatureSplitPackageIds.BASE_ID)
        assertThat(otherFeature2PackageId).isAtLeast(FeatureSplitPackageIds.BASE_ID)
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

