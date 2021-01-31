package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")
load("//tools/adt/idea/studio:studio.bzl", "studio_data")

# Note: The dex_library rule only handles source code, not resources, so we separate both file
# types, process them on different paths, and merge them together at the end.

kotlin_library(
    name = "agent-sources_undexed",
    srcs = glob([
        "src/main/com/android/tools/agent/appinspection/**/*.kt",
        "src/main/com/android/tools/agent/appinspection/**/*.java",
    ]),
    # do not sort: fake_android must come before latest_runtime_jar in the classpath to override small pieces of it
    deps = [
        "//tools/base/dynamic-layout-inspector/agent/appinspection/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
        "//tools/base/bazel:studio-proto",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
    ],
)

dex_library(
    name = "agent-sources_dexed",
    dexer = "D8",
    flags = [
        "--min-api 29",  # Live layout inspection supported on Q+
    ],
    jars = [
        ":agent-sources_undexed",
        "//tools/base/bazel:studio-proto",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto",
    ],
)

java_library(
    name = "agent-resources",
    resource_strip_prefix = "tools/base/dynamic-layout-inspector/agent/appinspection/resources",
    resources = glob(["resources/META-INF/**"]),
)

merge_jars(
    name = "agent",
    out = "layoutinspector-view-inspection.jar",
    jars = [
        ":agent-sources_dexed",
        ":agent-resources",
    ],
)

studio_data(
    name = "bundle",
    files = ["layoutinspector-view-inspection.jar"],
    mappings = {"tools/base/dynamic-layout-inspector/agent/appinspection/": "app-inspection/"},
)
