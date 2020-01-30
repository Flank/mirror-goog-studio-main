load("//tools/base/transport/test-framework:transport_test.bzl", "transport_app")

package(default_visibility = ["//visibility:public"])

transport_app(
    name = "test-app",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//tools/base/transport/test-framework/test-app:libtest-app",
    ],
)
