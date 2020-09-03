load("//tools/base/app-inspection/tests/app-inspection-test:app_inspection_test.bzl", "app_inspection_app")
load("//tools/base/transport/test-framework:transport_test.bzl", "transport_app")

package(default_visibility = ["//visibility:public"])

app_inspection_app(
    name = "test-app",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*.version"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//tools/base/transport/test-framework/test-app:libtest-app",
    ],
)
