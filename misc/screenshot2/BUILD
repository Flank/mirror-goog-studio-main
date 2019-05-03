load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

java_library(
    name = "screenshot2lib",
    srcs = glob(["src/main/java/**"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

sdk_java_binary(
    name = "screenshot2",
    main_class = "com.android.screenshot.Screenshot",
    visibility = ["//visibility:public"],
    runtime_deps = ["screenshot2lib"],
)
