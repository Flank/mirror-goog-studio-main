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
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd/statsd/proto:statsd_proto",
        "//tools/base/transport/daemon",
        "//tools/base/transport/utils",
    ],
)

cc_test(
    name = "statsd_test",
    srcs = [
        "statsd_subscriber_test.cc",
    ],
    copts = [
        "-Itools/base/transport",
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":statsd",
        "//external:gmock_main",
    ],
)
