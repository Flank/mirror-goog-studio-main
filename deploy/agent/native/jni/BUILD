package(default_visibility = ["//tools/base/deploy/agent/native:all"])

cc_library(
    name = "agent_jni",
    srcs = [
        "jni_util.cc",
    ],
    hdrs = [
        "jni_class.h",
        "jni_object.h",
        "jni_signature.h",
        "jni_util.h",
    ],
    tags = ["no_windows"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/deploy/common:common_lib",
    ],
)
