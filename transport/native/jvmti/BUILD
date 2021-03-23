load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

# Common helpers used by perfa_lib, JVMTI agent and App Inspection.
cc_library(
    name = "jvmti",
    srcs = [
        "hidden_api_silencer.cc",
        "hidden_api_silencer.h",
        "jvmti_helper.cc",
        "jvmti_helper.h",
        "scoped_local_ref.h",
    ],
    hdrs = [
        "hidden_api_silencer.h",
        "jvmti_helper.h",
        "scoped_local_ref.h",
    ],
    copts = ANDROID_COPTS,
    include_prefix = "jvmti",
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/utils",
    ],
)
