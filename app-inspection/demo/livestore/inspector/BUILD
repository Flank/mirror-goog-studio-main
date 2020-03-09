load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

package(default_visibility = ["//visibility:public"])

kotlin_library(
    name = "livestore-inspector",
    srcs = glob([
        "src/**/*.kt",
    ]),
    resource_strip_prefix = "tools/base/app-inspection/demo/livestore/inspector/resources/",
    resources = glob(["resources/META-INF/**"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
        "//tools/base/app-inspection/demo/livestore/library:livestore",
        "//tools/base/app-inspection/demo/livestore/protocol:livestore-protocol",
        "//tools/idea/.idea/libraries:gson",
    ],
)

dex_library(
    name = "livestore-inspector_dex",
    jars = [
        ":livestore-inspector",
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.8.5:jar",
    ],
)
