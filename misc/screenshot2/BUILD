load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

java_library(
    name = "screenshot2lib",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

sdk_java_binary(
    name = "screenshot2",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "win": "-Dcom.android.sdklib.toolsdir=%~dp0\\..",
    },
    main_class = "com.android.screenshot.Screenshot",
    visibility = ["//visibility:public"],
    runtime_deps = ["screenshot2lib"],
)
