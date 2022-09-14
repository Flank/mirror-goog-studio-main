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
    name = "trace_manager",
    srcs = [
        "trace_manager.cc",
    ],
    hdrs = [
        "profiling_app.h",
        "trace_manager.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/perfd/common",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":atrace",
        ":perfetto",
        ":simpleperf",
        "//tools/base/transport/native/utils",
        "@perfetto_repo//:cc_proto",
    ],
)

cc_library(
    name = "atrace",
    srcs = [
        "atrace/atrace.cc",
        "atrace/atrace_manager.cc",
    ],
    hdrs = [
        "atrace/atrace.h",
        "atrace/atrace_manager.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/perfd/common",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":trace_utils",
    ],
)

cc_library(
    name = "perfetto",
    srcs = [
        "perfetto/perfetto.cc",
        "perfetto/perfetto_manager.cc",
    ] + select_android(
        [
            "perfetto/perfetto.android.cc",
        ],
        [
            "perfetto/perfetto.host.cc",
        ],
    ),
    hdrs = [
        "perfetto/fake_perfetto.h",
        "perfetto/perfetto.h",
        "perfetto/perfetto_manager.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/external/perfetto_repo",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":trace_utils",
        "@perfetto_repo//:cc_proto",
    ],
)

cc_library(
    name = "simpleperf",
    srcs = [
        "simpleperf/simpleperf.cc",
        "simpleperf/simpleperf_manager.cc",
    ],
    hdrs = [
        "simpleperf/fake_simpleperf.h",
        "simpleperf/simpleperf.h",
        "simpleperf/simpleperf_manager.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":trace_utils",
    ],
)

cc_test(
    name = "perfd_trace_manager_tests",
    srcs = [
        "trace_manager_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    linkopts = [
        "-ldl",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":perfetto",
        ":simpleperf",
        ":trace_manager",
        "//tools/base/profiler/native/perfd/common:perfd_atrace_tests",
        "//tools/base/transport/native/utils",
        "@googletest//:gtest_main",
        "@perfetto_repo//:cc_proto",
    ],
)

cc_test(
    name = "perfd_atrace_tests",
    srcs = [
        "atrace/atrace_manager_test.cc",
        "atrace/fake_atrace.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    linkopts = [
        "-ldl",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":atrace",
        "//tools/base/transport/native/utils",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "perfd_perfetto_tests",
    srcs = [
        "perfetto/fake_perfetto.h",
        "perfetto/perfetto_manager_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    linkopts = [
        "-ldl",  # Needed for dladdr referenced in TermincationService
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":perfetto",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "perfd_simpleperf_tests",
    srcs = [
        "simpleperf/fake_simpleperf.h",
        "simpleperf/simpleperf_manager_test.cc",
        "simpleperf/simpleperf_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    linkopts = [
        "-ldl",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":simpleperf",
        "@googletest//:gtest_main",
    ],
)
