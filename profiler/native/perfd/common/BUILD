load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "select_android")

cc_library(
    name = "trace_utils",
    srcs = [
        "utils/tracing_utils.cc",
    ],
    hdrs = [
        "utils/tracing_utils.h",
    ],
    copts = ANDROID_COPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/native/utils",
    ],
)

cc_library(
    name = "perfetto",
    srcs = [
        "perfetto.cc",
        "perfetto_manager.cc",
    ] + select_android(
        [
            "perfetto.android.cc",
        ],
        [
            "perfetto.host.cc",
        ],
    ),
    hdrs = [
        "fake_perfetto.h",
        "perfetto.h",
        "perfetto_manager.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/external/perfetto_repo",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":trace_utils",
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
        "@perfetto_repo//:cc_proto",
    ],
)

cc_test(
    name = "common_cpu_tests",
    srcs = [
        "fake_perfetto.h",
        "perfetto_manager_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/tests",
        "-I$(GENDIR)/external/perfetto_repo",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/cpu",
    ],
    linkopts = [
        "-ldl",  # Needed for dladdr referenced in TermincationService
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":perfetto",
        ":trace_utils",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
        "@googletest//:gtest_main",
        "@perfetto_repo//:cc_proto",
    ],
)
