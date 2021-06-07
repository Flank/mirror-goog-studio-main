load("//:coverage.bzl", "coverage_report")
load("//:coverage.bzl", "combine_report_definitions")

# This should be a py_binary, but due to a Bazel bug
# using a py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "jacoco_xml_to_lcov",
    srcs = ["jacoco_xml_to_lcov.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using a py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "ignore_files_filter",
    srcs = ["ignore_files_filter.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "merge_lcov",
    srcs = ["merge_lcov.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "merge_tests",
    srcs = ["merge_tests.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "drop_exempt",
    srcs = ["drop_exempt.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "filter_lcov",
    srcs = ["filter_lcov.py"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
# See https://github.com/bazelbuild/bazel/issues/1192.
py_library(
    name = "generate_list",
    srcs = ["generate_list.py"],
)

# This is just an example report for testing during development
# run tests: bazel test --define agent_coverage=true --config=remote -- @cov//:example.suite
# make lcov: bazel build --config=remote -- @cov//:example.lcov
coverage_report(
    name = "example",
    srcpath_exclude = [
        "tools/base/lint/libs",
        "tools/base/usb-devices/src/com/android/tools/usb/parser",
    ],
    srcpath_include = [
        "tools/adt/idea/adt-branding",
        "tools/base/lint",
        "tools/base/usb-devices",
    ],
    tests = [
        "//tools/base/build-system/integration-test/application:automatic-tests",  # 8 shard 2 exec
        "//tools/base/lint/libs/lint-tests:tests.test",  # 1 shard 1 exec
    ],
)

# Coverage report for CMake vNext feature
coverage_report(
    name = "cmake-vnext",
    srcpath_include = [
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/CmakeFileApiMetadataGenerator.kt",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/cmake/CmakeFileApiCacheDataV2.kt",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/cmake/CmakeFileApiV1.kt",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/cmake/CmakeFileApiIndexData.kt",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/cmake/CmakeFileApiCmakeFilesDataV1.kt",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/cmake/CmakeFileApiTargetData.kt",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/cmake/CmakeFileApiCodeModelDataV2.kt",
    ],
    tests = [
        "//tools/base/build-system/gradle-core:tests.test",
        "//tools/base/build-system/integration-test/native:CmakeBasicProjectTest",
        "//tools/base/build-system/integration-test/native:CmakeGradleTargetsTest",
        "//tools/base/build-system/integration-test/native:CmakeJniLibTest",
        "//tools/base/build-system/integration-test/native:CmakeMultiModuleTest",
        "//tools/base/build-system/integration-test/native:CmakeStlMatrixTest",
        "//tools/base/build-system/integration-test/native:CmakeSysrootTest",
        "//tools/base/build-system/integration-test/native:CmakeTargetsTest",
        "//tools/base/build-system/integration-test/native:CombinedAbiDensitySplits",
        "//tools/base/build-system/integration-test/native:HeaderInCmakeListsTest",
        "//tools/base/build-system/integration-test/native:InjectedAbiSplitTest",
        "//tools/base/build-system/integration-test/native:InjectedAbiTest",
        "//tools/base/build-system/integration-test/native:NativeBuildOutputTest",
        "//tools/base/build-system/integration-test/native:NdkBuildAndroidMkLibraryTest",
        "//tools/base/build-system/integration-test/native:NdkBuildJniLibTest",
        "//tools/base/build-system/integration-test/native:NdkBuildTargetsTest",
        "//tools/base/build-system/integration-test/native:NdkBuildTest",
        "//tools/base/build-system/integration-test/native:NdkJniLibTest",
        "//tools/base/build-system/integration-test/native:NdkLibPrebuiltsTest",
        "//tools/base/build-system/integration-test/native:NdkPrebuiltsTest",
        "//tools/base/build-system/integration-test/native:NdkSanAngelesTest",
        "//tools/base/build-system/integration-test/native:NoSplitNdkVariantsTest",
        "//tools/base/build-system/integration-test/native:Pre21SplitTest",
        "//tools/base/build-system/integration-test/native:RenderscriptNdkTest",
        "//tools/base/build-system/integration-test/native:RsEnabledAnnotationTest",
        "//tools/base/build-system/integration-test/native:RsSupportModeTest",
        "//tools/base/build-system/integration-test/native:V2NativeModelTest",
        "//tools/base/build-system/integration-test/native:VulkanTest",
    ],
)

coverage_report(
    name = "android-apk",
    srcpath_include = [
        "tools/vendor/google/android-apk",
    ],
    tests = [
        "//tools/vendor/google/android-apk:android-apk.tests_tests",
    ],
)

coverage_report(
    name = "build-attribution",
    srcpath_include = [
        "tools/adt/idea/build-attribution/src",
    ],
    tests = [
        "//tools/adt/idea/build-attribution:intellij.android.build-attribution.tests_tests",
    ],
)

coverage_report(
    name = "custom-views-preview",
    srcpath_include = [
        "tools/adt/idea/designer/customview/src/",
    ],
    tests = [
        "//tools/adt/idea/designer/customview:intellij.android.designer.customview.tests_tests",
    ],
)

coverage_report(
    name = "app-inspection",
    srcpath_include = ["tools/adt/idea/app-inspection"],
    tests = [
        "//tools/adt/idea/app-inspection/api:intellij.android.app-inspection.api_tests",
        "//tools/adt/idea/app-inspection/ide:intellij.android.app-inspection.ide.tests_tests",
    ],
)

coverage_report(
    name = "database-inspector",
    srcpath_include = [
        "tools/adt/idea/app-inspection/inspectors/database",
    ],
    tests = [
        "//tools/adt/idea/app-inspection/inspectors/database:intellij.android.app-inspection.inspectors.database.tests_tests",
    ],
)

coverage_report(
    name = "workmanager-inspector",
    srcpath_include = [
        "tools/adt/idea/app-inspection/inspectors/workmanager",
    ],
    tests = [
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:intellij.android.app-inspection.inspectors.workmanager.model_tests",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:intellij.android.app-inspection.inspectors.workmanager.view.tests_tests",
    ],
)

coverage_report(
    name = "network-inspector",
    srcpath_include = [
        "tools/adt/idea/app-inspection/inspectors/network/ide/src",
        "tools/adt/idea/app-inspection/inspectors/network/model/src",
        "tools/adt/idea/app-inspection/inspectors/network/view/src",
        "tools/base/app-inspection/inspectors/network/src",
    ],
    tests = [
        "//tools/adt/idea/app-inspection/inspectors/network/ide:intellij.android.app-inspection.inspectors.network.ide.tests_tests",
        "//tools/adt/idea/app-inspection/inspectors/network/model:intellij.android.app-inspection.inspectors.network.model_tests",
        "//tools/adt/idea/app-inspection/inspectors/network/view:intellij.android.app-inspection.inspectors.network.view.tests_tests",
        "//tools/base/app-inspection/inspectors/network:agent_tests.test",
    ],
)

coverage_report(
    name = "databinding",
    srcpath_include = [
        "tools/adt/idea/android-lang-databinding",
        "tools/adt/idea/android/src/com/android/tools/idea/databinding",
        "tools/adt/idea/android/src/com/android/tools/idea/lang/databinding",
        "tools/adt/idea/databinding/",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_tests",
        "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
        "//tools/adt/idea/databinding:intellij.android.databinding.tests_tests",
    ],
)

coverage_report(
    name = "designtools",
    srcpath_exclude = [
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/flexbox",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/layout3d",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/mockup",
    ],
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/rendering",
        "tools/adt/idea/android/src/com/android/tools/idea/sampledata",
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcemanager",
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcechooser",
        "tools/adt/idea/compose-designer/src",
        "tools/adt/idea/designer/src/",
        "tools/adt/idea/layout-inspector/src/",
        "tools/adt/idea/nav/editor/src/",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
        "//tools/adt/idea/android-layout-inspector:android-layout-inspector-test_tests",
        "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests",
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui_tests",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib.tests_tests",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor.tests_tests",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api_tests",
        "//tools/base/layoutlib-api:tools.tests",
    ],
)

coverage_report(
    name = "dsl",
    srcpath_include = [
        "tools/adt/idea/gradle-dsl/src/com/android/tools/idea/gradle/dsl",
    ],
    tests = [
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.tests_tests",
    ],
)

coverage_report(
    name = "dynamic-layout-inspector",
    srcpath_include = [
        "tools/adt/idea/layout-inspector/src",
        "tools/base/dynamic-layout-inspector/agent/appinspection/src/main",
    ],
    tests = [
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests",
        "//tools/base/dynamic-layout-inspector/agent/appinspection:agent_tests.test",
    ],
)

coverage_report(
    name = "layouteditor",
    srcpath_exclude = [
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/flexbox",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/layout3d",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/mockup",
    ],
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/rendering",
        "tools/adt/idea/android/src/com/android/tools/idea/sampledata",
        "tools/adt/idea/compose-designer/src",
        "tools/adt/idea/designer/src/",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
        "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests",
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui_tests",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib.tests_tests",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api_tests",
        "//tools/base/layoutlib-api:tools.tests",
    ],
)

coverage_report(
    name = "ml-model-binding",
    srcpath_exclude = [
        "tools/adt/idea/mlkit/testSrc",
        "tools/base/mlkit-common/src/test",
    ],
    srcpath_include = [
        "tools/adt/idea/mlkit",
        "tools/base/mlkit-common",
    ],
    tests = [
        "//tools/adt/idea/mlkit:intellij.android.mlkit.tests_tests",
        "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common_tests",
    ],
)

coverage_report(
    name = "motioneditor",
    srcpath_include = [
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/motion",
    ],
    tests = [
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
    ],
)

coverage_report(
    name = "navigationeditor",
    srcpath_include = [
        "tools/adt/idea/nav/editor/src/com/android/tools/idea/naveditor",
    ],
    tests = [
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor.tests_tests",
    ],
)

coverage_report(
    name = "navigationpropertyinspector",
    srcpath_include = [
        "tools/adt/idea/nav/editor/src/com/android/tools/idea/naveditor/property2",
    ],
    tests = [
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor.tests_tests",
    ],
)

coverage_report(
    name = "navigationsafeargs",
    srcpath_include = [
        "tools/adt/idea/nav/safeargs",
    ],
    tests = [
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs.tests_tests",
    ],
)

coverage_report(
    name = "profilers",
    srcpath_include = [
        "tools/adt/idea/android-transport/src/com/android/tools/idea/transport",
        "tools/adt/idea/profilers-android/src/com/android/tools/idea/profilers",
        "tools/adt/idea/profilers/src/com/android/tools/profilers",
        "tools/adt/idea/profilers-ui/src/com/android/tools/profilers",
        "tools/adt/idea/transport-database/src/main/java/com/android/tools/datastore",
    ],
    tests = [
        "//tools/adt/idea/android-transport:intellij.android.transport_tests",
        "//tools/adt/idea/profilers:intellij.android.profilers_tests",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid_tests",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase_tests",
    ],
)

coverage_report(
    name = "projectsystem",
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/gradle",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
    ],
)

# Coverage report for R8 rule files editor.
coverage_report(
    name = "r8-editing",
    srcpath_include = [
        "tools/adt/idea/android-lang/src/com/android/tools/idea/lang/proguardR8",
    ],
    tests = [
        "//tools/adt/idea/android-lang:intellij.android.lang.tests_tests",
    ],
)

# Coverage report for the Resource Manager and Resource Picker classes
coverage_report(
    name = "resources-ui",
    srcpath_exclude = [
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcemanager/model/DesignAssetListModel.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcemanager/plugin/SketchImporter.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcemanager/README.md",
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcemanager/sketchImporter",
    ],
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcemanager",
        "tools/adt/idea/android/src/com/android/tools/idea/ui/resourcechooser",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
        "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
    ],
)

# Coverage report for the split editor feature in design tools.
# The editor covers most CUJs in both layout and navigation editors,
# so we run both targets and exclude unrelated directories.
coverage_report(
    name = "spliteditor",
    srcpath_exclude = [
        "tools/adt/idea/designer/src/com/android/tools/idea/common/lint",
        "tools/adt/idea/designer/src/com/android/tools/idea/common/scene/draw",
        "tools/adt/idea/designer/src/com/android/tools/idea/editors/layeredimage",
        "tools/adt/idea/designer/src/com/android/tools/idea/sampledata",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/constraint",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/flexbox",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/leanback",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/handlers/motion",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/layout3d",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/mockup",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/property",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/scene/draw",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/scout",
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/visual",
    ],
    srcpath_include = [
        "tools/adt/idea/designer/src/com/android/tools/idea",
        "tools/adt/idea/nav/editor/src/com/android/tools/idea/naveditor",
    ],
    tests = [
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor.tests_tests",
    ],
)

# Coverage report for suggested import.
# The explicitly excluded source file is for legacy mode.
coverage_report(
    name = "suggested-import",
    srcpath_exclude = [
        "tools/adt/idea/android/src/com/android/tools/idea/imports/MavenClassRegistryFromHardcodedMap.kt",
    ],
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/imports",
        "tools/adt/idea/android/src/org/jetbrains/android/dom/inspections/AndroidUnresolvableTagInspection.kt",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests__imports",
    ],
)

coverage_report(
    name = "systracev2",
    srcpath_include = [
        "tools/adt/idea/adt-ui-model/src/main/java/com/android/tools/adtui/model/trackgroup",
        "tools/adt/idea/adt-ui/src/main/java/com/android/tools/adtui/trackgroup",
        "tools/adt/idea/profilers/src/com/android/tools/profilers/cpu",
        "tools/adt/idea/profilers-ui/src/com/android/tools/profilers/cpu",
    ],
    tests = [
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui_tests",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model_tests",
        "//tools/adt/idea/profilers:intellij.android.profilers_tests",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests",
    ],
)

coverage_report(
    name = "fullscreen-heapdump-ui",
    srcpath_include = [
        "tools/adt/idea/profilers/src/com/android/tools/profilers/memory",
        "tools/adt/idea/profilers-ui/src/com/android/tools/profilers/memory",
    ],
    tests = [
        "//tools/adt/idea/profilers:intellij.android.profilers_tests",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests",
    ],
)

coverage_report(
    name = "templates",
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/npw/module/recipes",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/KeystoreUtils.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/ModuleTemplateDataBuilder.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/Parameter.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/recipe/DefaultRecipeExecutor2.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/recipe/FindReferencesExecutor2.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/RecipeMergeUtils.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/recipe/RenderingContext2.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/RepositoryUrlManager.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/SourceProviderUtil.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/StringParameterValidator.kt",
        "tools/adt/idea/android/src/com/android/tools/idea/templates/TemplateUtils.kt",
        "tools/base/wizard/template-impl/src/com/android/tools/idea/wizard/template",
        "tools/base/wizard/template-plugin/src/com/android/tools/idea/wizard/template",
    ],
    tests = [
        "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
    ],
)

coverage_report(
    name = "upgrade-assistant",
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/gradle/project/upgrade",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
    ],
)

coverage_report(
    name = "validation",
    srcpath_exclude = [
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/visual/analytics",
    ],
    srcpath_include = [
        "tools/adt/idea/designer/src/com/android/tools/idea/uibuilder/visual",
    ],
    tests = [
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
    ],
)

# Coverage report for Dagger editor support.
coverage_report(
    name = "dagger-editing",
    srcpath_include = [
        "tools/adt/idea/dagger/src/com/android/tools/idea/dagger",
    ],
    tests = [
        "//tools/adt/idea/dagger:intellij.android.dagger.tests_tests",
    ],
)

# Coverage report for compose-ide-plugin editor support.
coverage_report(
    name = "compose-ide-plugin",
    srcpath_include = [
        "tools/adt/idea/compose-ide-plugin/src/com/android/tools/compose",
    ],
    tests = [
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin.tests_tests",
    ],
)

# Coverage report for parallel device test report
coverage_report(
    name = "parallel-device-test-report",
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/testartifacts/instrumented/testsuite",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
    ],
)

# Coverage report for UTP test execution.
coverage_report(
    name = "utp",
    srcpath_include = [
        "tools/adt/idea/utp/src",
        "tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/testing/utp",
        "tools/base/utp",
    ],
    tests = [
        "//tools/adt/idea/utp:utp_tests",
        "//tools/base/build-system/gradle-core:tests.test",
        "//tools/base/build-system/integration-test/connected:UtpConnectedTest",
        "//tools/base/utp/android-device-provider-ddmlib:tests.test",
        "//tools/base/utp/android-device-provider-gradle:tests.test",
        "//tools/base/utp/android-test-plugin-host-coverage:tests.test",
        "//tools/base/utp/android-test-plugin-host-device-info:tests.test",
        "//tools/base/utp/android-test-plugin-host-logcat:tests.test",
        "//tools/base/utp/android-test-plugin-host-retention:tests.test",
        "//tools/base/utp/android-test-plugin-result-listener-gradle:tests.test",
    ],
)

# Coverage report for Embedded Emulator.
coverage_report(
    name = "emulator",
    srcpath_include = [
        "tools/adt/idea/emulator/src/com/android/tools/idea/emulator",
    ],
    tests = [
        "//tools/adt/idea/emulator:intellij.android.emulator.tests_tests",
    ],
)

# Coverage report for "Pair Devices over Wi-Fi" (aka Wireless ADB)
coverage_report(
    name = "adb-wireless",
    srcpath_include = [
        "tools/adt/idea/android-adb/src/com/android/tools/idea/adb/wireless",
    ],
    tests = [
        "//tools/adt/idea/android-adb:intellij.android.adb.tests_tests",
    ],
)

coverage_report(
    name = "memory-recording-ui",
    srcpath_include = [
        "tools/adt/idea/profilers/src/com/android/tools/profilers/memory/AllocationStage.kt",
        "tools/adt/idea/profilers/src/com/android/tools/profilers/memory/MainMemoryProfilerStage.kt",
        "tools/adt/idea/profilers-ui/src/com/android/tools/profilers/memory/AllocationStageView.kt",
        "tools/adt/idea/profilers-ui/src/com/android/tools/profilers/memory/MainMemoryProfilerStageView.kt",
    ],
    tests = [
        "//tools/adt/idea/profilers:intellij.android.profilers_tests",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests",
    ],
)

# Coverage report for "Wear OS emulator pairing assistant
coverage_report(
    name = "wear-pairing-assistant",
    srcpath_include = [
        "tools/adt/idea/android/src/com/android/tools/idea/wearparing",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests__wear.pairing.assistant",
    ],
)

combine_report_definitions(
    prefix = "comps",
    # keep sorted
    reports = [
        "adb-wireless",
        "all",
        "android-apk",
        "app-inspection",
        "build-attribution",
        "cmake-vnext",
        "compose-ide-plugin",
        "custom-views-preview",
        "dagger-editing",
        "database-inspector",
        "databinding",
        "designtools",
        "dsl",
        "dynamic-layout-inspector",
        "emulator",
        "fullscreen-heapdump-ui",
        "layouteditor",
        "memory-recording-ui",
        "ml-model-binding",
        "motioneditor",
        "navigationeditor",
        "navigationpropertyinspector",
        "navigationsafeargs",
        "network-inspector",
        "parallel-device-test-report",
        "profilers",
        "projectsystem",
        "r8-editing",
        "resources-ui",
        "spliteditor",
        "suggested-import",
        "systracev2",
        "templates",
        "upgrade-assistant",
        "utp",
        "validation",
        "wear-pairing-assistant",
        "workmanager-inspector",
    ],
)

# All coverage enabled tests should go here
coverage_report(
    name = "all",
    tests = [
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui_tests",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model_tests",
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
        "//tools/adt/idea/android-adb:intellij.android.adb.tests_tests",
        "//tools/adt/idea/android-common:intellij.android.common_tests",
        #TODO(b/155257347) too flaky for coverage runs
        #"//tools/adt/idea/android-kotlin:intellij.android.kotlin.tests_tests",
        "//tools/adt/idea/android-lang:intellij.android.lang.tests_tests",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_tests",
        "//tools/adt/idea/android-layout-inspector:android-layout-inspector-test_tests",
        "//tools/adt/idea/android-templates:intellij.android.templates.tests_tests",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework_tests",
        "//tools/adt/idea/android-transport:intellij.android.transport_tests",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer_tests",
        "//tools/adt/idea/app-inspection/api:intellij.android.app-inspection.api_tests",
        "//tools/adt/idea/app-inspection/ide:intellij.android.app-inspection.ide.tests_tests",
        "//tools/adt/idea/app-inspection/inspectors/database:intellij.android.app-inspection.inspectors.database.tests_tests",
        "//tools/adt/idea/app-inspection/inspectors/network/ide:intellij.android.app-inspection.inspectors.network.ide.tests_tests",
        "//tools/adt/idea/app-inspection/inspectors/network/model:intellij.android.app-inspection.inspectors.network.model_tests",
        "//tools/adt/idea/app-inspection/inspectors/network/view:intellij.android.app-inspection.inspectors.network.view.tests_tests",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:intellij.android.app-inspection.inspectors.workmanager.model_tests",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:intellij.android.app-inspection.inspectors.workmanager.view.tests_tests",
        "//tools/adt/idea/assistant:assistant-test_tests",
        "//tools/adt/idea/build-attribution:intellij.android.build-attribution.tests_tests",
        "//tools/adt/idea/build-common:intellij.android.buildCommon_tests",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer.tests_tests",
        "//tools/adt/idea/connection-assistant:connection-assistant-test_tests",
        "//tools/adt/idea/dagger:intellij.android.dagger.tests_tests",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin.tests_tests",
        "//tools/adt/idea/databinding:intellij.android.databinding.tests_tests",
        "//tools/adt/idea/debuggers:intellij.android.debuggers.tests_tests",
        "//tools/adt/idea/deploy:intellij.android.deploy_tests",
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
        "//tools/adt/idea/designer/customview:intellij.android.designer.customview.tests_tests",
        "//tools/adt/idea/emulator:intellij.android.emulator.tests_tests",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.tests_tests",
        "//tools/adt/idea/kotlin-integration:kotlin-integration-tests_tests",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui_tests",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib.tests_tests",
        "//tools/adt/idea/mlkit:intellij.android.mlkit.tests_tests",
        "//tools/adt/idea/native-symbolizer:native-symbolizer_tests",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor.tests_tests",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs.tests_tests",
        "//tools/adt/idea/observable:intellij.android.observable_tests",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui_tests",
        "//tools/adt/idea/profilers:intellij.android.profilers_tests",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid_tests",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle.tests_tests",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates.tests_tests",
        "//tools/adt/idea/smali:intellij.android.smali_tests",
        "//tools/adt/idea/studio-updater:integration_test",
        "//tools/adt/idea/swingp:intellij.android.adt.swingp_tests",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase_tests",
        "//tools/adt/idea/utp:utp_tests",
        "//tools/adt/idea/whats-new-assistant:whats-new-assistant-test_tests",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model_tests",
        "//tools/analytics-library/crash:analytics-crash_tests",
        "//tools/analytics-library/crash:tools.analytics-crash_tests.test",
        "//tools/analytics-library/publisher:analytics-publisher_tests",
        "//tools/analytics-library/shared:analytics-shared_tests",
        "//tools/analytics-library/shared:tools.analytics-shared_tests.test",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing_tests",
        "//tools/analytics-library/testing:tools.analytics-testing_tests.test",
        "//tools/analytics-library/tracker:analytics-tracker_tests",
        "//tools/analytics-library/tracker:tools.analytics-tracker_tests.test",
        "//tools/apksig:all",
        "//tools/apkzlib:apkzlib_tests",
        "//tools/base/adblib:tools.adblib.tests.test",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources_tests",
        "//tools/base/apkparser:tools.tests",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer_tests",
        "//tools/base/apkparser/analyzer:tools.tests",
        "//tools/base/apkparser/cli:tools.tests",
        "//tools/base/app-inspection/inspectors/network:agent_tests.test",
        "//tools/base/aspects_agent:agent_tests",
        "//tools/base/bazel:bin2c_tests",
        "//tools/base/bazel:iml_to_build_tests",
        "//tools/base/bazel:third_party_up_to_date",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger_tests",
        "//tools/base/build-system:tools.manifest-merger_tests.test",
        "//tools/base/build-system/builder:tests.test",
        "//tools/base/build-system/builder-model:tests.test",
        "//tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api_tests",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api_tests",
        "//tools/base/build-system/gradle-api:tests.test",
        "//tools/base/build-system/gradle-core:tests.test",
        "//tools/base/build-system/profile:tests.test",
        "//tools/base/build-system/integration-test/application:automatic-tests",
        "//tools/base/build-system/integration-test/application:coupled_tests",
        "//tools/base/build-system/integration-test/application:dependencies-tests",
        "//tools/base/build-system/integration-test/application:library-tests",
        "//tools/base/build-system/integration-test/application:mlkit-tests",
        "//tools/base/build-system/integration-test/application:packaging-tests",
        "//tools/base/build-system/integration-test/application:resources-tests",
        "//tools/base/build-system/integration-test/application:testing-tests",
        "//tools/base/build-system/integration-test/application:tests",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/gradlecompat:GradleVersionCheckTest",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/sanity:tests",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/sdk:sdk_tests",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/testing/unit:tests",
        "//tools/base/build-system/integration-test/connected:UtpConnectedTest",
        "//tools/base/build-system/integration-test/databinding:tests",
        "//tools/base/build-system/integration-test/framework:framework_tests.test",
        "//tools/base/build-system/integration-test/lint:tests",
        "//tools/base/build-system/integration-test/native:CmakeBasicProjectTest",
        "//tools/base/build-system/integration-test/native:CmakeGradleTargetsTest",
        "//tools/base/build-system/integration-test/native:InjectedAbiSplitTest",
        "//tools/base/build-system/integration-test/native:CmakeJniLibTest",
        "//tools/base/build-system/integration-test/native:CmakeMultiModuleTest",
        "//tools/base/build-system/integration-test/native:CmakeStlMatrixTest",
        "//tools/base/build-system/integration-test/native:CmakeSysrootTest",
        "//tools/base/build-system/integration-test/native:CmakeTargetsTest",
        "//tools/base/build-system/integration-test/native:CombinedAbiDensitySplits",
        "//tools/base/build-system/integration-test/native:HeaderInCmakeListsTest",
        "//tools/base/build-system/integration-test/native:InjectedAbiTest",
        "//tools/base/build-system/integration-test/native:NativeBuildOutputTest",
        "//tools/base/build-system/integration-test/native:NdkBuildAndroidMkLibraryTest",
        "//tools/base/build-system/integration-test/native:NdkBuildJniLibTest",
        "//tools/base/build-system/integration-test/native:NdkBuildTargetsTest",
        "//tools/base/build-system/integration-test/native:NdkBuildTest",
        "//tools/base/build-system/integration-test/native:NdkJniLibTest",
        "//tools/base/build-system/integration-test/native:NdkLibPrebuiltsTest",
        "//tools/base/build-system/integration-test/native:NdkPrebuiltsTest",
        "//tools/base/build-system/integration-test/native:NdkSanAngelesTest",
        "//tools/base/build-system/integration-test/native:NoSplitNdkVariantsTest",
        "//tools/base/build-system/integration-test/native:Pre21SplitTest",
        "//tools/base/build-system/integration-test/native:RenderscriptNdkTest",
        "//tools/base/build-system/integration-test/native:RsEnabledAnnotationTest",
        "//tools/base/build-system/integration-test/native:RsSupportModeTest",
        "//tools/base/build-system/integration-test/native:V2NativeModelTest",
        "//tools/base/build-system/integration-test/native:VulkanTest",
        "//tools/base/common:studio.android.sdktools.common.tests_tests",
        "//tools/base/common:tools.tests.test",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib_tests",
        "//tools/base/ddmlib:tools.tests.test",
        "//tools/base/deploy/deployer:deployer.tests",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer_tests",
        "//tools/base/draw9patch:studio.android.sdktools.draw9patch_tests",
        "//tools/base/dynamic-layout-inspector/agent/appinspection:agent_tests.test",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver_tests",
        "//tools/base/fakeadbserver:tools.tests",
        "//tools/base/flags:studio.android.sdktools.flags_tests",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api_tests",
        "//tools/base/layoutlib-api:tools.tests",
        "//tools/base/lint:studio-checks-tests.test",
        "//tools/base/lint:tests.test",
        "//tools/base/lint/libs/lint-tests:tests.test",
        "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common_tests",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch_tests",
        "//tools/base/perflib:studio.android.sdktools.perflib_tests",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe_tests",
        "//tools/base/profiler/tests/perf-test:NativeBinarySizeTest",
        "//tools/base/repository:studio.android.sdktools.repository_tests",
        "//tools/base/repository:tools.tests",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository_tests",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common_tests",
        "//tools/base/sdk-common:tools.tests.test",
        "//tools/base/sdklib:studio.android.sdktools.sdklib_tests",
        "//tools/base/sdklib:tools.tests.test",
        "//tools/base/testutils:studio.testutils-tests_tests",
        "//tools/base/testutils:tools.tests.test",
        "//tools/base/usb-devices:studio.usb-devices_tests",
        "//tools/base/utp/android-device-provider-ddmlib:tests.test",
        "//tools/base/utp/android-device-provider-gradle:tests.test",
        "//tools/base/utp/android-test-plugin-host-coverage:tests.test",
        "//tools/base/utp/android-test-plugin-host-device-info:tests.test",
        "//tools/base/utp/android-test-plugin-host-logcat:tests.test",
        "//tools/base/utp/android-test-plugin-host-retention:tests.test",
        "//tools/base/utp/android-test-plugin-result-listener-gradle:tests.test",
        "//tools/data-binding:studio.compilerCommon_tests",
        "//tools/data-binding:studio.compiler_tests",
        "//tools/data-binding:tools.compilerCommon_tests.test",
        "//tools/vendor/google/android-apk:android-apk.tests_tests",
        "//tools/vendor/google/android-ndk:android-ndk.tests_tests",
    ],
)
