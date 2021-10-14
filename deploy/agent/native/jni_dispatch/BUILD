package(default_visibility = ["//tools/base/deploy/agent/native:all"])

cc_library(
    name = "jni_dispatch",
    srcs = [
        "jni_dispatch.cc",
    ],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
    ],
    alwayslink = True,
)

cc_binary(
    name = "jni_dispatch_dso",
    linkshared = True,
    deps = [
        ":jni_dispatch",
        "//prebuilts/studio/jdk:jni_headers",
    ],
)
