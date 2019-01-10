cc_library(
    name = "testutils",
    srcs = [
        "utils.bazel.cc",
        "utils.h",
    ],
    copts = [
        "-Itools/base/transport/daemon",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)
