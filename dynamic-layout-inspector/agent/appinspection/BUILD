package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_test", "kotlin_library")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/adt/idea/studio:studio.bzl", "studio_data")

kotlin_library(
    name = "nojarjar-shared",
    srcs = glob([
        "src/main/com/android/tools/agent/shared/*.kt",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/bazel:studio-proto",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_library(
    name = "nojarjar",
    srcs = glob(["src/main/com/android/tools/agent/nojarjar/*.kt"]),
    deps = [
        ":nojarjar-shared",
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

app_inspection_jar(
    name = "agent",
    srcs = glob([
        "src/main/com/android/tools/agent/appinspection/**/*.kt",
        "src/main/com/android/tools/agent/appinspection/**/*.java",
    ]),
    out = "layoutinspector-view-inspection.jar",
    bundle_srcs = [
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common.jar",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-jvm",
        ":nojarjar-shared",
    ],
    d8_flags = [
        "--min-api 29",  # Live layout inspection supported on Q+
    ],
    inspection_resource_strip_prefix = "tools/base/dynamic-layout-inspector/agent/appinspection/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    nojarjar_deps = [
        ":nojarjar",
    ],
    proto = "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
    # do not sort: fake_android must come before latest_runtime_jar in the classpath to override small pieces of it
    deps = [
        "//tools/base/dynamic-layout-inspector/agent/appinspection/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/bazel:studio-proto",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "@maven//:androidx.annotation.annotation",
        ":nojarjar-shared",
    ],
)

kotlin_test(
    name = "agent_tests_without_androidx.window",
    srcs = ["src/test/com/android/tools/agent/appinspection/ViewLayoutInspectorWithoutAndroidxWindowTest.kt"] + glob([
        "src/main/com/android/tools/agent/appinspection/**/*.kt",
        "src/main/com/android/tools/agent/appinspection/**/*.java",
        "src/test/com/android/tools/agent/appinspection/testutils/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests_without_androidx.window.jar",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
    ],
    # do not sort: fake_android must come before latest_runtime_jar in the classpath to override small pieces of it
    deps = [
        "//tools/base/dynamic-layout-inspector/agent/appinspection/fake-android:fake_android_without_androidx.window",
        ":nojarjar",
        ":nojarjar-shared",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/bazel:studio-proto",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:androidx.annotation.annotation",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_test(
    name = "agent_tests",
    srcs = glob(
        [
            "src/test/com/android/tools/agent/appinspection/**/*.kt",
            "src/test/com/android/tools/agent/appinspection/**/*.java",
        ],
        exclude = ["src/test/com/android/tools/agent/appinspection/ViewLayoutInspectorWithoutAndroidxWindowTest.kt"],
    ),
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests.jar",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
    ],
    deps = [
        ":agent-sources_undexed",
        ":nojarjar",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

studio_data(
    name = "bundle",
    files = ["layoutinspector-view-inspection.jar"],
    mappings = {"tools/base/dynamic-layout-inspector/agent/appinspection/": "app-inspection/"},
)
