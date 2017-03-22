cc_binary(
    name = "libagent.so",
    srcs = [
        "agent.cc",
        "jvmti_helper.h",
        "jvmti_helper.cc",
        "scoped_local_ref.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkshared = 1,
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
        "//tools/base/profiler/native/perfa:perfa_static",
    ],
)
