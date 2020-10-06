package(default_visibility = ["//visibility:public"])

cc_library(
    name = "statsd",
    srcs = [
        "pulled_atoms/bytes_transfer.cc",
        "statsd_subscriber.cc",
    ],
    hdrs = [
        "statsd_subscriber.h",
    ] + glob(["*/*.h"]),
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
        "-std=c++11",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd/statsd/proto:statsd_proto",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
    ],
)

cc_test(
    name = "statsd_test",
    srcs = [
        "statsd_subscriber_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":statsd",
        "@googletest//:gtest_main",
    ],
)
