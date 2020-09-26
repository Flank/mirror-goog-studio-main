cc_library(
    name = "testutils",
    srcs = glob([
        "**/*bazel.cc",
        "**/*.h",
    ]),
    hdrs = glob(["**/*.h"]),
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    include_prefix = "test",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)
