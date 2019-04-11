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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.ANDROIDX_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.BuildFileBuilder
import com.android.build.gradle.integration.common.fixture.app.JavaSourceFileBuilder
import com.android.build.gradle.integration.common.fixture.app.LayoutFileBuilder
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace
import com.android.build.gradle.options.BooleanOption.ENABLE_INCREMENTAL_DATA_BINDING
import com.android.build.gradle.options.BooleanOption.USE_ANDROID_X
import com.android.testutils.TestUtils.waitForFileSystemTick
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Expect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Integration test to ensure incrementality of incremental builds when data binding is used.
 *
 * The test project has the following structure (including generated source files and compiled
 * classes):
 *
 * project
 *   - app
 *       - src/main/java
 *           - com.example.app
 *               - MainActivity.java
 *       - src/main/res/layout (empty)
 *       - build/generated/ap_generated_sources/debug/out
 *           - com.example.app
 *               - BR.java
 *               - DataBinderMapperImpl.java
 *           - androidx.databinding
 *               - DataBinderMapperImpl.java
 *           - com.example.lib
 *               - BR.java
 *           - androidx.databinding.library.baseAdapters.BR.java
 *           - androidx.databinding.DataBindingComponent.java
 *       - build/intermediates/javac/debug/classes (7 files)
 *   - lib
 *       - src/main/java
 *           - com.example.lib
 *               - Data1.java [*** changed ***]
 *               - Data2.java
 *               - Loner1.java [*** changed ***]
 *               - Loner2.java
 *       - src/main/res/layout
 *           - layout1.xml [*** changed ***]
 *           - layout2.xml
 *       - build/generated/ap_generated_sources/debug/out
 *           - com.example.lib
 *               - DataBinderMapperImpl.java
 *           - com.example.lib.databinding
 *               - Layout1BindingImpl.java
 *               - Layout2BindingImpl.java
 *            - com.example.lib.generated.callback
 *               - OnClickListener.java
 *           - com.example.lib
 *               - BR.java
 *           - androidx.databinding.library.baseAdapters.BR.java
 *           - androidx.databinding.DataBindingComponent.java
 *       - build/intermediates/javac/debug/classes (11 files)
 *
 * The layout1.xml and layout2.xml layout files use expressions defined in Data1.java and
 * Data2.java, whereas Loner1.java and Loner2.java are source files that do not have any
 * relationships with other source files and layout files.
 *
 * The following tests will change each of the files marked with [*** changed ***] above to trigger
 * incremental builds and verify the set of re-generated source files and recompiled classes.
 */
@RunWith(FilterableParameterized::class)
class DataBindingIncrementalityTest(private val withIncrementalDB: Boolean) {

    companion object {

        @Parameterized.Parameters(name = "incrementalDB_{0}")
        @JvmStatic
        fun parameters() = listOf(true, false)

        // Modules
        private const val APP_MODULE = ":app"
        private const val LIB_MODULE = ":lib"

        // Paths to different kinds of files
        private const val SOURCE_DIR = "src/main/java"
        private const val LAYOUT_DIR = "src/main/res/layout"
        private const val GENERATED_SOURCE_DIR = "build/generated/ap_generated_sources/debug/out"
        private const val COMPILED_CLASSES_DIR =
            "build/intermediates/javac/debug/classes"

        // Original source files in app/src/main/java
        private const val APP_PACKAGE = "com.example.app"
        private const val APP_MAIN_ACTIVITY_CLASS = "$APP_PACKAGE.MainActivity"

        // Original source files in lib/src/main/java
        private const val LIB_PACKAGE = "com.example.lib"
        private const val LIB_DATA_1_CLASS = "$LIB_PACKAGE.Data1"
        private const val LIB_DATA_2_CLASS = "$LIB_PACKAGE.Data2"
        private const val LIB_LONER_1_CLASS = "$LIB_PACKAGE.Loner1"
        private const val LIB_LONER_2_CLASS = "$LIB_PACKAGE.Loner2"

        // Original layout files in lib/src/main/res/layout
        private const val LIB_LAYOUT_1 = "layout1"
        private const val LIB_LAYOUT_2 = "layout2"

        // Generated source files in app/build/generated/ap_generated_sources
        private const val APP_GENERATED_BR_CLASS = "com.example.app.BR"
        private const val APP_GENERATED_DATA_BINDER_MAPPER_CLASS =
            "com.example.app.DataBinderMapperImpl"
        private const val APP_GENERATED_ANDROIDX_DATA_BINDER_MAPPER_CLASS =
            "androidx.databinding.DataBinderMapperImpl"

        // Generated source files in lib/build/generated/ap_generated_sources
        private const val LIB_GENERATED_DATA_BINDER_MAPPER_CLASS =
            "com.example.lib.DataBinderMapperImpl"
        private const val LIB_GENERATED_LAYOUT_1_BINDING_CLASS =
            "com.example.lib.databinding.Layout1BindingImpl"
        private const val LIB_GENERATED_LAYOUT_2_BINDING_CLASS =
            "com.example.lib.databinding.Layout2BindingImpl"
        private const val LIB_GENERATED_ON_CLICK_LISTENER_CLASS =
            "com.example.lib.generated.callback.OnClickListener"

        // Generated source files in (app and lib)/build/generated/ap_generated_sources
        private const val GENERATED_BR_FROM_LIB_CLASS = "com.example.lib.BR"
        private const val GENERATED_BASE_ADAPTERS_BR_CLASS =
            "androidx.databinding.library.baseAdapters.BR"
        private const val GENERATED_DATA_BINDING_COMPONENT_CLASS =
            "androidx.databinding.DataBindingComponent"

        // Tasks
        private const val CLEAN_TASK = "clean"
        private const val APP_COMPILE_TASK = "$APP_MODULE:compileDebugJavaWithJavac"
        private const val LIB_COMPILE_TASK = "$LIB_MODULE:compileDebugJavaWithJavac"
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(setUpTestProject())
        .addGradleProperties("${USE_ANDROID_X.propertyName}=true")
        .addGradleProperties("${ENABLE_INCREMENTAL_DATA_BINDING.propertyName}=$withIncrementalDB")
        .create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, setUpApp())
            .subproject(LIB_MODULE, setUpLib())
            .build()
    }

    private fun setUpApp(): MinimalSubProject {
        val app = MinimalSubProject.app(APP_PACKAGE)

        // 1. Create build.gradle file
        app.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = app.plugin
                compileSdkVersion = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                minSdkVersion = "23"
                dataBindingEnabled = true
                addDependency(
                    dependency = "'androidx.appcompat:appcompat:$ANDROIDX_VERSION'"
                )
                addDependency("implementation", "project('$LIB_MODULE')")
                build()
            })

        // 2. Create AndroidManifest.xml file
        app.withFile(
            "src/main/AndroidManifest.xml",
            with(ManifestFileBuilder(APP_PACKAGE)) {
                addApplicationTag(APP_MAIN_ACTIVITY_CLASS)
                build()
            })

        // 3. Create source files
        app.withFile(
            "$SOURCE_DIR/${getPath(APP_MAIN_ACTIVITY_CLASS)}.java",
            with(JavaSourceFileBuilder(APP_PACKAGE)) {
                addImports(
                    "android.os.Bundle",
                    "androidx.appcompat.app.AppCompatActivity",
                    "androidx.databinding.DataBindingUtil",
                    "com.example.lib.Data1",
                    "com.example.lib.Data2"
                )
                addClass(
                    """
                    public class MainActivity extends AppCompatActivity {

                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);

                            final Data1 data1 = new Data1();
                            final Data2 data2 = new Data2();

                            DataBindingUtil.setDefaultComponent(new androidx.databinding.DataBindingComponent(){
                                @Override
                                public Data1 getData1() {
                                    return data1;
                                }
                                @Override
                                public Data2 getData2() {
                                    return data2;
                                }
                            });

                            com.example.lib.databinding.Layout1Binding binding = DataBindingUtil.setContentView(this, R.layout.layout1);
                            binding.setData1(data1);
                        }
                    }
                    """.trimIndent()
                )
                build()
            })

        return app
    }

    private fun setUpLib(): MinimalSubProject {
        val lib = MinimalSubProject.lib(LIB_PACKAGE)

        // 1. Create build.gradle file
        lib.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = lib.plugin
                compileSdkVersion = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                minSdkVersion = "23"
                dataBindingEnabled = true
                addDependency(
                    dependency = "'androidx.constraintlayout:constraintlayout:" +
                            "$ANDROIDX_CONSTRAINT_LAYOUT_VERSION'"
                )
                build()
            })

        // 2. Create AndroidManifest.xml file
        lib.withFile(
            "src/main/AndroidManifest.xml",
            with(ManifestFileBuilder(LIB_PACKAGE)) {
                build()
            })

        // 3. Create source files
        lib.withFile(
            "$SOURCE_DIR/${getPath(LIB_DATA_1_CLASS)}.java",
            with(JavaSourceFileBuilder(LIB_PACKAGE)) {
                addImports(
                    "android.widget.TextView",
                    "android.widget.Toast",
                    "android.view.View",
                    "androidx.databinding.BaseObservable",
                    "androidx.databinding.Bindable",
                    "androidx.databinding.BindingAdapter"
                )
                addClass(
                    """
                    public class Data1 extends BaseObservable {
                        private String data1Field1 = "Data 1 Field 1";
                        private String data1Field2 = "Data 1 Field 2";

                        private int numOfClicks = 0;

                        @Bindable
                        public String getData1Field1() {
                            return data1Field1;
                        }

                        public void setData1Field1(String data1Field1) {
                            this.data1Field1 = data1Field1;
                            notifyPropertyChanged(BR.data1Field1);
                        }

                        @Bindable
                        public String getData1Field2() {
                            return data1Field2;
                        }

                        public void setData1Field2(String data1Field2) {
                            this.data1Field2 = data1Field2;
                            notifyPropertyChanged(BR.data1Field2);
                        }

                        public void clicked(View view) {
                            Toast.makeText(view.getContext(), "Number of clicks: " + (++numOfClicks), Toast.LENGTH_LONG).show();
                            setData1Field1("I'm clicked!");
                        }

                        @BindingAdapter({"android:customProperty1"})
                        public void setCustomProperty1(TextView textView, String customProperty1) {
                            Toast.makeText(textView.getContext(), "setCustomProperty1() called with argument: " + customProperty1, Toast.LENGTH_LONG).show();
                        }
                    }
                    """.trimIndent()
                )
                build()
            })
        lib.withFile(
            "$SOURCE_DIR/${getPath(LIB_DATA_2_CLASS)}.java",
            with(JavaSourceFileBuilder(LIB_PACKAGE)) {
                addImports(
                    "android.widget.TextView",
                    "android.widget.Toast",
                    "android.view.View",
                    "androidx.databinding.BaseObservable",
                    "androidx.databinding.Bindable",
                    "androidx.databinding.BindingAdapter"
                )
                addClass(
                    """
                    public class Data2 extends BaseObservable {
                        private String data2Field1 = "Data 2 Field 1";
                        private String data2Field2 = "Data 2 Field 2";;

                        private int numOfClicks = 0;

                        @Bindable
                        public String getData2Field1() {
                            return data2Field1;
                        }

                        public void setData2Field1(String data2Field1) {
                            this.data2Field1 = data2Field1;
                            notifyPropertyChanged(BR.data2Field1);
                        }

                        @Bindable
                        public String getData2Field2() {
                            return data2Field2;
                        }

                        public void setData2Field2(String data2Field2) {
                            this.data2Field2 = data2Field2;
                            notifyPropertyChanged(BR.data2Field2);
                        }

                        public void clicked(View view) {
                            Toast.makeText(view.getContext(), "I'm clicked!", Toast.LENGTH_LONG).show();
                            setData2Field1("Number of clicks: " + (++numOfClicks));
                        }

                        @BindingAdapter({"android:customProperty2"})
                        public void setCustomProperty2(TextView textView, String customProperty2) {
                            Toast.makeText(textView.getContext(), "setCustomProperty2() called with argument: " + customProperty2, Toast.LENGTH_LONG).show();
                        }
                    }
                    """.trimIndent()
                )
                build()
            })
        lib.withFile(
            "$SOURCE_DIR/${getPath(LIB_LONER_1_CLASS)}.java",
            with(JavaSourceFileBuilder(LIB_PACKAGE)) {
                addClass(
                    """
                    public class Loner1 {
                    }
                    """.trimIndent()
                )
                build()
            })
        lib.withFile(
            "$SOURCE_DIR/${getPath(LIB_LONER_2_CLASS)}.java",
            with(JavaSourceFileBuilder(LIB_PACKAGE)) {
                addClass(
                    """
                    public class Loner2 {
                    }
                    """.trimIndent()
                )
                build()
            })

        // 4. Create layout files
        lib.withFile(
            "$LAYOUT_DIR/$LIB_LAYOUT_1.xml",
            with(LayoutFileBuilder()) {
                useAndroidX = true
                withDataBinding = true
                addVariable("data1", "com.example.lib.Data1")
                addTextView(
                    "text1",
                    "@{data1.data1Field1}",
                    listOf(
                        "android:onClick=\"@{(view) -> data1.clicked(view)}\"",
                        "android:customProperty1=\"@{data1.data1Field2}\""
                    )
                )
                build()
            })
        lib.withFile(
            "$LAYOUT_DIR/$LIB_LAYOUT_2.xml",
            with(LayoutFileBuilder()) {
                useAndroidX = true
                withDataBinding = true
                addVariable("data2", "com.example.lib.Data2")
                addTextView(
                    "text1",
                    "@{data2.data2Field1}",
                    listOf(
                        "android:onClick=\"@{(view) -> data2.clicked(view)}\"",
                        "android:customProperty2=\"@{data2.data2Field2}\""
                    )
                )
                build()
            })

        return lib
    }

    // Original source files and layout files in lib/src/main/java. These files will be changed
    // to trigger incremental builds.
    private lateinit var libData1JavaFile: File
    private lateinit var libLoner1JavaFile: File
    private lateinit var libLayout1LayoutFile: File

    // Generated source files in app/build/generated/ap_generated_sources
    private lateinit var appGeneratedBrJavaFile: File
    private lateinit var appGeneratedDataBinderMapperJavaFile: File
    private lateinit var appGeneratedAndroidXDataBinderMapperJavaFile: File
    private lateinit var appGeneratedBrFromLibJavaFile: File
    private lateinit var appGeneratedBaseAdaptersBrJavaFile: File
    private lateinit var appGeneratedDataBindingComponentJavaFile: File

    // Generated source files in lib/build/generated/ap_generated_sources
    private lateinit var libGeneratedDataBinderMapperJavaFile: File
    private lateinit var libGeneratedLayout1BindingJavaFile: File
    private lateinit var libGeneratedLayout2BindingJavaFile: File
    private lateinit var libGeneratedOnClickListenerJavaFile: File
    private lateinit var libGeneratedBrJavaFile: File
    private lateinit var libGeneratedBaseAdaptersBrJavaFile: File
    private lateinit var libGeneratedDataBindingComponentJavaFile: File

    // Compiled classes in app/build/intermediates/javac
    private lateinit var appMainActivityClass: File
    private lateinit var appGeneratedBrClass: File
    private lateinit var appGeneratedDataBinderMapperClass: File
    private lateinit var appGeneratedAndroidXDataBinderMapperClass: File
    private lateinit var appGeneratedBrFromLibClass: File
    private lateinit var appGeneratedBaseAdaptersBrClass: File
    private lateinit var appGeneratedDataBindingComponentClass: File

    // Compiled classes in lib/build/intermediates/javac
    private lateinit var libData1Class: File
    private lateinit var libData2Class: File
    private lateinit var libLoner1Class: File
    private lateinit var libLoner2Class: File
    private lateinit var libGeneratedDataBinderMapperClass: File
    private lateinit var libGeneratedLayout1BindingClass: File
    private lateinit var libGeneratedLayout2BindingClass: File
    private lateinit var libGeneratedOnClickListenerClass: File
    private lateinit var libGeneratedBrClass: File
    private lateinit var libGeneratedBaseAdaptersBrClass: File
    private lateinit var libGeneratedDataBindingComponentClass: File

    // Timestamps of generated source files and compiled classes
    private lateinit var fileToTimestampMap: Map<File, Long>

    // Set of generated source files and compiled classes that have changed
    private lateinit var changedFiles: Set<File>

    @Before
    fun setUp() {
        val appDir = project.getSubproject(APP_MODULE).testDir
        val libDir = project.getSubproject(LIB_MODULE).testDir

        libData1JavaFile = File("$libDir/$SOURCE_DIR/${getPath(LIB_DATA_1_CLASS)}.java")
        libLoner1JavaFile = File("$libDir/$SOURCE_DIR/${getPath(LIB_LONER_1_CLASS)}.java")
        libLayout1LayoutFile = File("$libDir/$LAYOUT_DIR/$LIB_LAYOUT_1.xml")

        appGeneratedBrJavaFile = File("$appDir/$GENERATED_SOURCE_DIR/${getPath(APP_GENERATED_BR_CLASS)}.java")
        appGeneratedDataBinderMapperJavaFile = File("$appDir/$GENERATED_SOURCE_DIR/${getPath(APP_GENERATED_DATA_BINDER_MAPPER_CLASS)}.java")
        appGeneratedAndroidXDataBinderMapperJavaFile = File("$appDir/$GENERATED_SOURCE_DIR/${getPath(APP_GENERATED_ANDROIDX_DATA_BINDER_MAPPER_CLASS)}.java")
        appGeneratedBrFromLibJavaFile = File("$appDir/$GENERATED_SOURCE_DIR/${getPath(GENERATED_BR_FROM_LIB_CLASS)}.java")
        appGeneratedBaseAdaptersBrJavaFile = File("$appDir/$GENERATED_SOURCE_DIR/${getPath(GENERATED_BASE_ADAPTERS_BR_CLASS)}.java")
        appGeneratedDataBindingComponentJavaFile = File("$appDir/$GENERATED_SOURCE_DIR/${getPath(GENERATED_DATA_BINDING_COMPONENT_CLASS)}.java")

        libGeneratedDataBinderMapperJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(LIB_GENERATED_DATA_BINDER_MAPPER_CLASS)}.java")
        libGeneratedLayout1BindingJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(LIB_GENERATED_LAYOUT_1_BINDING_CLASS)}.java")
        libGeneratedLayout2BindingJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(LIB_GENERATED_LAYOUT_2_BINDING_CLASS)}.java")
        libGeneratedOnClickListenerJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(LIB_GENERATED_ON_CLICK_LISTENER_CLASS)}.java")
        libGeneratedBrJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(GENERATED_BR_FROM_LIB_CLASS)}.java")
        libGeneratedBaseAdaptersBrJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(GENERATED_BASE_ADAPTERS_BR_CLASS)}.java")
        libGeneratedDataBindingComponentJavaFile = File("$libDir/$GENERATED_SOURCE_DIR/${getPath(GENERATED_DATA_BINDING_COMPONENT_CLASS)}.java")

        appMainActivityClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(APP_MAIN_ACTIVITY_CLASS)}.class")
        appGeneratedBrClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(APP_GENERATED_BR_CLASS)}.class")
        appGeneratedDataBinderMapperClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(APP_GENERATED_DATA_BINDER_MAPPER_CLASS)}.class")
        appGeneratedAndroidXDataBinderMapperClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(APP_GENERATED_ANDROIDX_DATA_BINDER_MAPPER_CLASS)}.class")
        appGeneratedBrFromLibClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(GENERATED_BR_FROM_LIB_CLASS)}.class")
        appGeneratedBaseAdaptersBrClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(GENERATED_BASE_ADAPTERS_BR_CLASS)}.class")
        appGeneratedDataBindingComponentClass = File("$appDir/$COMPILED_CLASSES_DIR/${getPath(GENERATED_DATA_BINDING_COMPONENT_CLASS)}.class")

        libData1Class = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_DATA_1_CLASS)}.class")
        libData2Class = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_DATA_2_CLASS)}.class")
        libLoner1Class = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_LONER_1_CLASS)}.class")
        libLoner2Class = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_LONER_2_CLASS)}.class")
        libGeneratedDataBinderMapperClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_GENERATED_DATA_BINDER_MAPPER_CLASS)}.class")
        libGeneratedLayout1BindingClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_GENERATED_LAYOUT_1_BINDING_CLASS)}.class")
        libGeneratedLayout2BindingClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_GENERATED_LAYOUT_2_BINDING_CLASS)}.class")
        libGeneratedOnClickListenerClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(LIB_GENERATED_ON_CLICK_LISTENER_CLASS)}.class")
        libGeneratedBrClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(GENERATED_BR_FROM_LIB_CLASS)}.class")
        libGeneratedBaseAdaptersBrClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(GENERATED_BASE_ADAPTERS_BR_CLASS)}.class")
        libGeneratedDataBindingComponentClass = File("$libDir/$COMPILED_CLASSES_DIR/${getPath(GENERATED_DATA_BINDING_COMPONENT_CLASS)}.class")
    }

    private fun recordTimestamps() {
        val files = listOf(
            appGeneratedBrJavaFile,
            appGeneratedDataBinderMapperJavaFile,
            appGeneratedAndroidXDataBinderMapperJavaFile,
            appGeneratedBrFromLibJavaFile,
            appGeneratedBaseAdaptersBrJavaFile,
            appGeneratedDataBindingComponentJavaFile,
            libGeneratedDataBinderMapperJavaFile,
            libGeneratedLayout1BindingJavaFile,
            libGeneratedLayout2BindingJavaFile,
            libGeneratedOnClickListenerJavaFile,
            libGeneratedBrJavaFile,
            libGeneratedBaseAdaptersBrJavaFile,
            libGeneratedDataBindingComponentJavaFile,
            appMainActivityClass,
            appGeneratedBrClass,
            appGeneratedDataBinderMapperClass,
            appGeneratedAndroidXDataBinderMapperClass,
            appGeneratedBrFromLibClass,
            appGeneratedBaseAdaptersBrClass,
            appGeneratedDataBindingComponentClass,
            libData1Class,
            libData2Class,
            libLoner1Class,
            libLoner2Class,
            libGeneratedDataBinderMapperClass,
            libGeneratedLayout1BindingClass,
            libGeneratedLayout2BindingClass,
            libGeneratedOnClickListenerClass,
            libGeneratedBrClass,
            libGeneratedBaseAdaptersBrClass,
            libGeneratedDataBindingComponentClass
        )

        val map = mutableMapOf<File, Long>()
        for (file in files) {
            map[file] = file.lastModified()
        }
        fileToTimestampMap = map.toMap()

        // This is to avoid the flakiness of timestamp checks
        waitForFileSystemTick()
    }

    private fun recordChangedFiles() {
        changedFiles = fileToTimestampMap.filter { (file, previousTimestamp) ->
            file.lastModified() != previousTimestamp
        }.keys
    }

    /**
     * Runs a full (non-incremental) build.
     */
    private fun runFullBuild(): GradleBuildResult {
        val result = project.executor().run(CLEAN_TASK, APP_COMPILE_TASK)
        recordTimestamps()
        return result
    }

    /** Runs an incremental build. */
    private fun runIncrementalBuild(): GradleBuildResult {
        val result = project.executor().run(APP_COMPILE_TASK)
        recordChangedFiles()
        return result
    }

    private fun getPath(packageName: String) = packageName.replace('.', '/')

    @get:Rule
    val expect: Expect = Expect.create()

    @Test
    fun `verify first full build`() {
        // This test verifies the results of the first full (non-incremental) build. The other tests
        // verify the results of the second incremental build based on different change scenarios.
        val result = runFullBuild()

        // Source files should be generated
        assertThat(appGeneratedBrJavaFile).exists()
        assertThat(appGeneratedDataBinderMapperJavaFile).exists()
        assertThat(appGeneratedAndroidXDataBinderMapperJavaFile).exists()
        assertThat(appGeneratedBrFromLibJavaFile).exists()
        assertThat(appGeneratedBaseAdaptersBrJavaFile).exists()
        assertThat(appGeneratedDataBindingComponentJavaFile).exists()
        assertThat(libGeneratedDataBinderMapperJavaFile).exists()
        assertThat(libGeneratedLayout1BindingJavaFile).exists()
        assertThat(libGeneratedLayout2BindingJavaFile).exists()
        assertThat(libGeneratedOnClickListenerJavaFile).exists()
        assertThat(libGeneratedBrJavaFile).exists()
        assertThat(libGeneratedBaseAdaptersBrJavaFile).exists()
        assertThat(libGeneratedDataBindingComponentJavaFile).exists()

        // Both original and generated source files should be compiled
        assertThat(appMainActivityClass).exists()
        assertThat(appGeneratedBrClass).exists()
        assertThat(appGeneratedDataBinderMapperClass).exists()
        assertThat(appGeneratedAndroidXDataBinderMapperClass).exists()
        assertThat(appGeneratedBrFromLibClass).exists()
        assertThat(appGeneratedBaseAdaptersBrClass).exists()
        assertThat(appGeneratedDataBindingComponentClass).exists()
        assertThat(libData1Class).exists()
        assertThat(libData2Class).exists()
        assertThat(libLoner1Class).exists()
        assertThat(libLoner2Class).exists()
        assertThat(libGeneratedDataBinderMapperClass).exists()
        assertThat(libGeneratedLayout1BindingClass).exists()
        assertThat(libGeneratedLayout2BindingClass).exists()
        assertThat(libGeneratedOnClickListenerClass).exists()
        assertThat(libGeneratedBrClass).exists()
        assertThat(libGeneratedBaseAdaptersBrClass).exists()
        assertThat(libGeneratedDataBindingComponentClass).exists()

        // Check the tasks' states
        assertThat(result.getTask(LIB_COMPILE_TASK)).didWork()
        assertThat(result.getTask(APP_COMPILE_TASK)).didWork()
    }

    @Test
    fun `change original source file with a change related to data binding`() {
        runFullBuild()

        // Change an original source file with a change that is related to data binding
        searchAndReplace(
            libData1JavaFile,
            "public class Data1 extends BaseObservable {",
            """
            public class Data1 extends BaseObservable {

            @Bindable
            public String getData1Field3() {
                return "Data 1 Field 3";
            }
            """.trimIndent()
        )

        val result = runIncrementalBuild()

        /*
         * ANNOTATION PROCESSING
         */
        // All of the generated source files are relevant, so all of them should be re-generated
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsAllOf(
                libGeneratedDataBinderMapperJavaFile,
                libGeneratedLayout1BindingJavaFile,
                libGeneratedLayout2BindingJavaFile,
                libGeneratedOnClickListenerJavaFile,
                libGeneratedBrJavaFile,
                libGeneratedBaseAdaptersBrJavaFile,
                libGeneratedDataBindingComponentJavaFile,
                appGeneratedBrJavaFile,
                appGeneratedDataBinderMapperJavaFile,
                appGeneratedAndroidXDataBinderMapperJavaFile,
                appGeneratedBrFromLibJavaFile,
                appGeneratedBaseAdaptersBrJavaFile,
                appGeneratedDataBindingComponentJavaFile
            )

        /*
         * COMPILATION
         */
        // The relevant (original or generated) source files should be recompiled
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsAllOf(
                libData1Class,
                libData2Class,
                libGeneratedDataBinderMapperClass,
                libGeneratedLayout1BindingClass,
                libGeneratedLayout2BindingClass,
                libGeneratedOnClickListenerClass,
                libGeneratedBrClass,
                libGeneratedBaseAdaptersBrClass,
                libGeneratedDataBindingComponentClass,
                appMainActivityClass,
                appGeneratedBrClass,
                appGeneratedDataBinderMapperClass,
                appGeneratedAndroidXDataBinderMapperClass,
                appGeneratedBrFromLibClass,
                appGeneratedBaseAdaptersBrClass,
                appGeneratedDataBindingComponentClass
            )

        // The irrelevant (original or generated) source files should not be recompiled
        if (withIncrementalDB) {
            expect.that(changedFiles)
                .named("Recompiled classes")
                .containsNoneOf(
                    libLoner1Class,
                    libLoner2Class
                )
        } else {
            // These classes are still recompiled in non-incremental mode
            expect.that(changedFiles)
                .named("Recompiled classes")
                .containsAllOf(
                    libLoner1Class,
                    libLoner2Class
                )
        }

        // Check the tasks' states
        assertThat(result.getTask(LIB_COMPILE_TASK)).didWork()
        assertThat(result.getTask(APP_COMPILE_TASK)).didWork()
    }

    @Test
    fun `change original source file with a change not related to data binding`() {
        runFullBuild()

        // Change an original source file with a change that is not related to data binding
        searchAndReplace(
            libData1JavaFile,
            "public class Data1 extends BaseObservable",
            "public class Data1 extends BaseObservable /* dummy comment to trigger change */"
        )

        val result = runIncrementalBuild()

        /*
         * ANNOTATION PROCESSING
         */
        // The relevant generated source files should be re-generated
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsAllOf(
                libGeneratedDataBinderMapperJavaFile,
                libGeneratedLayout1BindingJavaFile,
                libGeneratedLayout2BindingJavaFile,
                libGeneratedOnClickListenerJavaFile,
                libGeneratedBrJavaFile,
                libGeneratedBaseAdaptersBrJavaFile,
                libGeneratedDataBindingComponentJavaFile
            )

        // The irrelevant generated source files should not be re-generated
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsNoneOf(
                appGeneratedBrJavaFile,
                appGeneratedDataBinderMapperJavaFile,
                appGeneratedAndroidXDataBinderMapperJavaFile,
                appGeneratedBrFromLibJavaFile,
                appGeneratedBaseAdaptersBrJavaFile,
                appGeneratedDataBindingComponentJavaFile
            )

        /*
         * COMPILATION
         */
        // The relevant (original or generated) source files should be recompiled
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsAllOf(
                libData1Class,
                libData2Class,
                libGeneratedDataBinderMapperClass,
                libGeneratedLayout1BindingClass,
                libGeneratedLayout2BindingClass,
                libGeneratedOnClickListenerClass,
                libGeneratedBrClass,
                libGeneratedBaseAdaptersBrClass,
                libGeneratedDataBindingComponentClass
            )

        // The irrelevant (original or generated) source files should not be recompiled
        if (withIncrementalDB) {
            expect.that(changedFiles)
                .named("Recompiled classes")
                .containsNoneOf(
                    libLoner1Class,
                    libLoner2Class
                )
        } else {
            // These classes are still recompiled in non-incremental mode
            expect.that(changedFiles)
                .named("Recompiled classes")
                .containsAllOf(
                    libLoner1Class,
                    libLoner2Class
                )
        }
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsNoneOf(
                appMainActivityClass,
                appGeneratedBrClass,
                appGeneratedDataBinderMapperClass,
                appGeneratedAndroidXDataBinderMapperClass,
                appGeneratedBrFromLibClass,
                appGeneratedBaseAdaptersBrClass,
                appGeneratedDataBindingComponentClass
            )

        // Check the tasks' states
        assertThat(result.getTask(LIB_COMPILE_TASK)).didWork()
        assertThat(result.getTask(APP_COMPILE_TASK)).wasUpToDate()
    }

    @Test
    fun `change original source file that is not related to data binding`() {
        runFullBuild()

        // Change an original source file that is not related to data binding
        searchAndReplace(
            libLoner1JavaFile,
            "public class Loner1",
            "public class Loner1 /* dummy comment to trigger change */"
        )

        val result = runIncrementalBuild()

        /*
         * ANNOTATION PROCESSING
         */
        // The relevant generated source files should be re-generated. (Note that Gradle always
        // re-runs aggregating annotation processors on any source file change as it doesn't know
        // if the change is affecting those annotation processors or not, therefore the set of
        // relevant files below could be more than necessary.)
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsAllOf(
                libGeneratedDataBinderMapperJavaFile,
                libGeneratedLayout1BindingJavaFile,
                libGeneratedLayout2BindingJavaFile,
                libGeneratedOnClickListenerJavaFile,
                libGeneratedBrJavaFile,
                libGeneratedBaseAdaptersBrJavaFile,
                libGeneratedDataBindingComponentJavaFile
            )

        // The irrelevant generated source files should not be re-generated
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsNoneOf(
                appGeneratedBrJavaFile,
                appGeneratedDataBinderMapperJavaFile,
                appGeneratedAndroidXDataBinderMapperJavaFile,
                appGeneratedBrFromLibJavaFile,
                appGeneratedBaseAdaptersBrJavaFile,
                appGeneratedDataBindingComponentJavaFile
            )

        /*
         * COMPILATION
         */
        // The relevant (original or generated) source files should be recompiled
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsAllOf(
                libLoner1Class,
                libData1Class,
                libData2Class,
                libGeneratedDataBinderMapperClass,
                libGeneratedLayout1BindingClass,
                libGeneratedLayout2BindingClass,
                libGeneratedOnClickListenerClass,
                libGeneratedBrClass,
                libGeneratedBaseAdaptersBrClass,
                libGeneratedDataBindingComponentClass
            )

        // The irrelevant (original or generated) source files should not be recompiled
        if (withIncrementalDB) {
            expect.that(changedFiles)
                .named("Recompiled classes")
                .doesNotContain(libLoner2Class)
        } else {
            // These classes are still recompiled in non-incremental mode
            expect.that(changedFiles)
                .named("Recompiled classes")
                .contains(libLoner2Class)
        }
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsNoneOf(
                appMainActivityClass,
                appGeneratedBrClass,
                appGeneratedDataBinderMapperClass,
                appGeneratedAndroidXDataBinderMapperClass,
                appGeneratedBrFromLibClass,
                appGeneratedBaseAdaptersBrClass,
                appGeneratedDataBindingComponentClass
            )

        // Check the tasks' states
        assertThat(result.getTask(LIB_COMPILE_TASK)).didWork()
        assertThat(result.getTask(APP_COMPILE_TASK)).wasUpToDate()
    }

    @Test
    fun `change original layout file with a change related to data binding`() {
        runFullBuild()

        // Change an original layout file with a change that is related to data binding
        searchAndReplace(
            libLayout1LayoutFile,
            "android:text=\"@{data1.data1Field1}\"",
            "android:text=\"@{data1.data1Field2}\""
        )

        val result = runIncrementalBuild()

        /*
         * ANNOTATION PROCESSING
         */
        // Gradle decides to do full recompile because a resource (the layout info file) has changed
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsAllOf(
                libGeneratedDataBinderMapperJavaFile,
                libGeneratedLayout1BindingJavaFile,
                libGeneratedLayout2BindingJavaFile,
                libGeneratedOnClickListenerJavaFile,
                libGeneratedBrJavaFile,
                libGeneratedBaseAdaptersBrJavaFile,
                libGeneratedDataBindingComponentJavaFile,
                appGeneratedBrJavaFile,
                appGeneratedDataBinderMapperJavaFile,
                appGeneratedAndroidXDataBinderMapperJavaFile,
                appGeneratedBrFromLibJavaFile,
                appGeneratedBaseAdaptersBrJavaFile,
                appGeneratedDataBindingComponentJavaFile
            )

        /*
         * COMPILATION
         */
        // Gradle decides to do full recompile because a resource (the layout info file) has changed
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsAllOf(
                libData1Class,
                libData2Class,
                libLoner1Class,
                libLoner2Class,
                libGeneratedDataBinderMapperClass,
                libGeneratedLayout1BindingClass,
                libGeneratedLayout2BindingClass,
                libGeneratedOnClickListenerClass,
                libGeneratedBrClass,
                libGeneratedBaseAdaptersBrClass,
                libGeneratedDataBindingComponentClass,
                appMainActivityClass,
                appGeneratedBrClass,
                appGeneratedDataBinderMapperClass,
                appGeneratedAndroidXDataBinderMapperClass,
                appGeneratedBrFromLibClass,
                appGeneratedBaseAdaptersBrClass,
                appGeneratedDataBindingComponentClass
            )

        // Check the tasks' states
        assertThat(result.getTask(LIB_COMPILE_TASK)).didWork()
        assertThat(result.getTask(APP_COMPILE_TASK)).didWork()
    }

    @Test
    fun `change original layout file with a change not related to data binding`() {
        runFullBuild()

        // Change an original layout file with a change that is not related to data binding
        searchAndReplace(
            libLayout1LayoutFile,
            "<data>",
            "<data> <!-- dummy comment to trigger change -->"
        )

        val result = runIncrementalBuild()

        /*
         * ANNOTATION PROCESSING
         */
        // None of the generated source files are relevant, so none of them should be re-generated
        expect.that(changedFiles)
            .named("Re-generated source files")
            .containsNoneOf(
                libGeneratedDataBinderMapperJavaFile,
                libGeneratedLayout1BindingJavaFile,
                libGeneratedLayout2BindingJavaFile,
                libGeneratedOnClickListenerJavaFile,
                libGeneratedBrJavaFile,
                libGeneratedBaseAdaptersBrJavaFile,
                libGeneratedDataBindingComponentJavaFile,
                appGeneratedBrJavaFile,
                appGeneratedDataBinderMapperJavaFile,
                appGeneratedAndroidXDataBinderMapperJavaFile,
                appGeneratedBrFromLibJavaFile,
                appGeneratedBaseAdaptersBrJavaFile,
                appGeneratedDataBindingComponentJavaFile
            )

        /*
         * COMPILATION
         */
        // None of the (original or generated) source files are relevant, so none of them should be
        // recompiled
        expect.that(changedFiles)
            .named("Recompiled classes")
            .containsNoneOf(
                libData1Class,
                libData2Class,
                libLoner1Class,
                libLoner2Class,
                libGeneratedDataBinderMapperClass,
                libGeneratedLayout1BindingClass,
                libGeneratedLayout2BindingClass,
                libGeneratedOnClickListenerClass,
                libGeneratedBrClass,
                libGeneratedBaseAdaptersBrClass,
                libGeneratedDataBindingComponentClass,
                appMainActivityClass,
                appGeneratedBrClass,
                appGeneratedDataBinderMapperClass,
                appGeneratedAndroidXDataBinderMapperClass,
                appGeneratedBrFromLibClass,
                appGeneratedBaseAdaptersBrClass,
                appGeneratedDataBindingComponentClass
            )

        // Check the tasks' states
        assertThat(result.getTask(LIB_COMPILE_TASK)).wasUpToDate()
        assertThat(result.getTask(APP_COMPILE_TASK)).wasUpToDate()
    }
}
