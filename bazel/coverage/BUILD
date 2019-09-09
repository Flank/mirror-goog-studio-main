load("//:coverage.bzl", "coverage_report")

# This should be a py_binary, but due to a Bazel bug
# using a py_binary as a tool in a genrule doesn't work
py_library(
    name = "jacoco_xml_to_lcov",
    srcs = ["jacoco_xml_to_lcov.py"],
    visibility = ["//visibility:public"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
py_library(
    name = "merge_lcov",
    srcs = ["merge_lcov.py"],
)

# This should be a py_binary, but due to a Bazel bug
# using py_binary as a tool in a genrule doesn't work
py_library(
    name = "filter_lcov",
    srcs = ["filter_lcov.py"],
)

# This is just an example report for testing during development
# run tests: bazel test --define agent_coverage=true --config=remote -- @cov//:example:suite
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
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.tests_tests",  # 5 shard 1 exec
        "//tools/base/build-system/integration-test/application:automatic-tests",  # 8 shard 2 exec
        "//tools/base/build-system/integration-test/native:AbiPureSplits",  # 1 shard 2 exec
        "//tools/base/lint/libs/lint-tests:tests.test",  # 1 shard 1 exec
    ],
)

coverage_report(
    name = "android-apk",
    srcpath_include = [
        "tools/vendor/google/android-apk",
    ],
    tests = [
        "//tools/vendor/google/android-apk:android-apk_tests",
    ],
)

coverage_report(
    name = "databinding",
    srcpath_include = [
        "tools/adt/idea/android-lang-databinding",
        "tools/adt/idea/databinding/",
        "tools/adt/idea/android/src/com/android/tools/idea/databinding",
        "tools/adt/idea/android/src/com/android/tools/idea/lang/databinding",
    ],
    tests = [
        "//tools/adt/idea/android:intellij.android.core.tests_tests",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_tests",
        "//tools/adt/idea/databinding:intellij.android.databinding.tests_tests",
    ],
)

coverage_report(
    name = "navigationeditor",
    srcpath_include = [
        "tools/adt/idea/naveditor/src/com/android/tools/idea/naveditor",
    ],
    tests = [
        "//tools/adt/idea/naveditor:intellij.android.naveditor.tests_tests",
    ],
)

coverage_report(
    name = "navigationpropertyinspector",
    srcpath_include = [
        "tools/adt/idea/naveditor/src/com/android/tools/idea/naveditor/property2",
    ],
    tests = [
        "//tools/adt/idea/naveditor:intellij.android.naveditor.tests_tests",
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
        "tools/adt/idea/naveditor/src/com/android/tools/idea/naveditor",
    ],
    tests = [
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
        "//tools/adt/idea/naveditor:intellij.android.naveditor.tests_tests",
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
        "//tools/adt/idea/android-debuggers:intellij.android.debuggers.tests_tests",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.tests_tests",
        "//tools/adt/idea/android-lang:intellij.android.lang.tests_tests",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding.tests_tests",
        "//tools/adt/idea/android-layout-inspector:android-layout-inspector-test_tests",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework_tests",
        "//tools/adt/idea/android-transport:intellij.android.transport_tests",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer_tests",
        "//tools/adt/idea/assistant:assistant-test_tests",
        "//tools/adt/idea/build-common:intellij.android.buildCommon_tests",
        "//tools/adt/idea/connection-assistant:connection-assistant-test_tests",
        "//tools/adt/idea/databinding:intellij.android.databinding.tests_tests",
        "//tools/adt/idea/deploy:intellij.android.deploy_tests",
        "//tools/adt/idea/designer:intellij.android.designer.tests_tests",
        "//tools/adt/idea/kotlin-integration:kotlin-integration-tests_tests",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector.tests_tests",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui_tests",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader.tests_tests",
        "//tools/adt/idea/native-symbolizer:native-symbolizer_tests",
        "//tools/adt/idea/naveditor:intellij.android.naveditor.tests_tests",
        "//tools/adt/idea/observable:intellij.android.observable_tests",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui_tests",
        "//tools/adt/idea/profilers:intellij.android.profilers_tests",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid_tests",
        "//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests",
        "//tools/adt/idea/project-system:intellij.android.projectSystem.tests_tests",
        # TODO(b/136028658): too flaky for use in coverage runs
        #"//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle.tests_tests",
        "//tools/adt/idea/resources-base:intellij.android.resources-base.tests_tests",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates.tests_tests",
        "//tools/adt/idea/smali:intellij.android.smali_tests",
        "//tools/adt/idea/swingp:intellij.android.adt.swingp_tests",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase_tests",
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
        "//tools/base/apkparser:studio.android.sdktools.binary-resources_tests",
        "//tools/base/apkparser:tools.tests",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer_tests",
        "//tools/base/apkparser/analyzer:tools.tests",
        "//tools/base/apkparser/cli:tools.tests",
        "//tools/base/aspects_agent:agent_tests",
        "//tools/base/aspects_agent:studio.android.sdktools.aspects-agent_tests",
        "//tools/base/bazel:bin2c_tests",
        "//tools/base/bazel:iml_to_build_tests",
        "//tools/base/bazel:third_party_up_to_date",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger_tests",
        "//tools/base/build-system:tools.manifest-merger_tests.test",
        "//tools/base/build-system/builder:tests.test",
        "//tools/base/build-system/builder-model:tests",
        "//tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api_tests",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api_tests",
        "//tools/base/build-system/gradle-api:tests.test",
        "//tools/base/build-system/gradle-core:tests.test",
        "//tools/base/build-system/integration-test/application:automatic-tests",
        "//tools/base/build-system/integration-test/application:tests",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/gradlecompat:GradleVersionCheckTest",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/sanity:tests",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/sdk:sdk_tests",
        "//tools/base/build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/testing/unit:tests",
        "//tools/base/build-system/integration-test/databinding:tests",
        "//tools/base/build-system/integration-test/framework:framework_tests.test",
        "//tools/base/build-system/integration-test/native:AbiPureSplits",
        "//tools/base/build-system/integration-test/native:CmakeBasicProjectTest",
        "//tools/base/build-system/integration-test/native:CmakeExtensionsTest",
        "//tools/base/build-system/integration-test/native:CmakeGradleTargetsTest",
        "//tools/base/build-system/integration-test/native:CmakeInjectedAbiSplitTest",
        "//tools/base/build-system/integration-test/native:CmakeJniLibTest",
        "//tools/base/build-system/integration-test/native:CmakeJniPureSplitLibTest",
        "//tools/base/build-system/integration-test/native:CmakeMultiModuleTest",
        "//tools/base/build-system/integration-test/native:CmakeStlMatrixTest",
        "//tools/base/build-system/integration-test/native:CmakeSysrootTest",
        "//tools/base/build-system/integration-test/native:CmakeTargetsTest",
        "//tools/base/build-system/integration-test/native:CmakeVanillaBasicProjectTest",
        "//tools/base/build-system/integration-test/native:CombinedAbiDensityPureSplits",
        "//tools/base/build-system/integration-test/native:FeatureAndAbiPureSplitsTest",
        "//tools/base/build-system/integration-test/native:HeaderInCmakeListsTest",
        "//tools/base/build-system/integration-test/native:InjectedAbiNativeLibraryTest",
        "//tools/base/build-system/integration-test/native:NativeBuildOutputTest",
        "//tools/base/build-system/integration-test/native:NativeModelTest",
        "//tools/base/build-system/integration-test/native:NdkBuildAndroidMkLibraryTest",
        "//tools/base/build-system/integration-test/native:NdkBuildJniLibTest",
        "//tools/base/build-system/integration-test/native:NdkBuildJniPureSplitLibTest",
        "//tools/base/build-system/integration-test/native:NdkBuildSplitTest",
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
        "//tools/base/build-system/integration-test/native:VulkanTest",
        "//tools/base/common:studio.android.sdktools.common.tests_tests",
        "//tools/base/common:tools.tests.test",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib_tests",
        "//tools/base/ddmlib:tools.tests",
        "//tools/base/deploy/deployer:deployer.tests",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer_tests",
        "//tools/base/devicelib:studio.devicelib_tests",
        "//tools/base/devicelib:tools.tests.test",
        "//tools/base/draw9patch:studio.android.sdktools.draw9patch_tests",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver_tests",
        "//tools/base/fakeadbserver:tools.tests",
        "//tools/base/flags:studio.android.sdktools.flags_tests",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api_tests",
        "//tools/base/layoutlib-api:tools.tests",
        "//tools/base/lint:studio-checks-tests.test",
        "//tools/base/lint:tests.test",
        "//tools/base/lint/libs/lint-tests:tests.test",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch_tests",
        "//tools/base/perflib:studio.android.sdktools.perflib_tests",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe_tests",
        "//tools/base/profiler/tests/perf-test:NativeBinarySizeTest",
        "//tools/base/repository:studio.android.sdktools.repository_tests",
        "//tools/base/repository:tools.tests",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common_tests",
        "//tools/base/sdk-common:tools.tests.test",
        "//tools/base/sdklib:studio.android.sdktools.sdklib_tests",
        "//tools/base/sdklib:tools.tests.test",
        "//tools/base/testutils:studio.testutils-tests_tests",
        "//tools/base/testutils:tools.tests.test",
        "//tools/base/usb-devices:studio.usb-devices_tests",
        "//tools/data-binding:studio.compilerCommon_tests",
        "//tools/data-binding:studio.compiler_tests",
        "//tools/data-binding:tools.compilerCommon_tests.test",
        "//tools/idea/updater:integration_test",
        "//tools/vendor/google/android-apk:android-apk_tests",
        # TODO(b/140178289): too flaky for use in the coverage suite
        #"//tools/vendor/google/android-ndk:android-ndk_tests",
    ],
)
