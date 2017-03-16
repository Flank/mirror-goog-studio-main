cc_binary(
    name = "libperfa.so",
    srcs = [
        "agent_component.h",
        "perfa.cc",
        "perfa.h",
        "support/event_manager.cc",
        "support/event_manager.h",
        "support/event_passthrough.cc",
        "support/http_tracker.cc",
        "support/jni_wrappers.h",
        "support/vm_stats_sampler.cc",
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
    ],
)
