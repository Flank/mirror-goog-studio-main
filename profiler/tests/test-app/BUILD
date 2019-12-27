load("//tools/base/transport/test-framework:transport_test.bzl", "transport_app")

package(default_visibility = ["//visibility:public"])

transport_app(
    name = "test-app",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp/2.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp3/okhttp/3.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okio/okio/1.6.0:jar",
        "//tools/base/transport/test-framework/test-app:libtest-app",
    ],
)

