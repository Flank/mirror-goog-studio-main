cc_binary(
    name = "libperfa.so",
    srcs = [
        "jvmti_helper.cc",
        "jvmti_helper.h",
        "memory/memory_agent.cc",
        "memory/memory_agent.h",
        "perfa.cc",
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
        "//tools/base/profiler/native/agent",
        "//tools/base/profiler/native/dex/slicer",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)
