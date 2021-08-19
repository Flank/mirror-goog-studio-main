load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")

transport_test(
    name = "AppInspectionTest",
    size = "medium",
    srcs = glob(["testSrcs/**/*.java"]),
    # NOTE: use direct file reference instead of target reference (ex: .../test-app:test-app)
    # Otherwise, the APK will not be included in the test.
    app_dexes = ["//tools/base/app-inspection/tests/test-app:test-app.apk"],
    # Test inspector injected at runtime
    app_runtime_deps = ["//tools/base/app-inspection/tests/test-inspector:test-inspector"],
    jvm_flags = [
        "-Dtest.inspector.dex.location=$(location //tools/base/app-inspection/tests/test-inspector:test-inspector)",
    ],
    shard_count = 3,
    deps = [
        "//tools/base/app-inspection/tests/test-framework",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
        "@maven//:androidx.annotation.annotation",
        "@maven//:com.google.truth.truth",
    ],
)
