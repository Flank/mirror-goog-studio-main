load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

package(default_visibility = ["//visibility:public"])

kotlin_library(
    name = "live-store-inspector",
    srcs = glob([
        "src/**/*.kt",
    ]),
    resource_strip_prefix = "tools/base/app-inspection/demo/live-store/inspector/resources/",
    resources = glob(["resources/META-INF/**"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
        "//tools/base/app-inspection/demo/live-store/library:live-store",
        "//tools/base/app-inspection/demo/live-store/protocol:live-store-protocol",
        "//tools/idea/.idea/libraries:gson",
    ],
)

dex_library(
    name = "live_sql_viewer_dex",
    jars = [
        ":live-store-inspector",
    ],
)
