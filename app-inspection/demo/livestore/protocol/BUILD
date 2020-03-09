load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

package(default_visibility = ["//visibility:public"])

kotlin_library(
    name = "livestore-protocol",
    srcs = glob([
        "src/**/*.kt",
    ]),
    deps = [
        "//tools/idea/.idea/libraries:gson",
    ],
)
