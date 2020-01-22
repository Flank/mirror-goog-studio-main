load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")

transport_test(
    name = "AppInspectionTest",
    size = "medium",
    srcs = glob(["testSrcs/**/*.java"]),
    app_dexes = ["//tools/base/transport/test-framework/test-app:test-app"],
    # Test inspector injected at runtime
    app_runtime_deps = ["//tools/base/app-inspection/tests/test-inspector:test-inspector"],
    jvm_flags = [
        "-Dtest.inspector.dex.location=$(location //tools/base/app-inspection/tests/test-inspector:test-inspector)",
    ],
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//tools/idea/.idea/libraries:truth",
    ],
)
