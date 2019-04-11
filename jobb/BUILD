load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

java_library(
    name = "jobb-lib",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = ["//tools/external/fat32lib"],
)

sdk_java_binary(
    name = "jobb",
    main_class = "com.android.jobb.Main",
    visibility = ["//visibility:public"],
    runtime_deps = ["jobb-lib"],
)
