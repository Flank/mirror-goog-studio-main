package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/adt/idea/studio:studio.bzl", "studio_data")

app_inspection_jar(
    name = "agent",
    srcs = glob([
        "src/main/com/android/tools/agent/appinspection/**/*.kt",
        "src/main/com/android/tools/agent/appinspection/**/*.java",
    ]),
    out = "layoutinspector-view-inspection.jar",
    d8_flags = [
        "--min-api 29",  # Live layout inspection supported on Q+
    ],
    inspection_resource_strip_prefix = "tools/base/dynamic-layout-inspector/agent/appinspection/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    jarjar_srcs = [
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common.jar",
    ],
    proto = "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
    # do not sort: fake_android must come before latest_runtime_jar in the classpath to override small pieces of it
    deps = [
        "//tools/base/dynamic-layout-inspector/agent/appinspection/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
        "//tools/base/bazel:studio-proto",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
    ],
)

kotlin_test(
    name = "agent_tests",
    srcs = glob([
        "src/test/com/android/tools/agent/appinspection/**/*.kt",
        "src/test/com/android/tools/agent/appinspection/**/*.java",
    ]),
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests.jar",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
    ],
    deps = [
        ":agent-sources_undexed",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

studio_data(
    name = "bundle",
    files = ["layoutinspector-view-inspection.jar"],
    mappings = {"tools/base/dynamic-layout-inspector/agent/appinspection/": "app-inspection/"},
)
