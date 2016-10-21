cc_library(
    name = "testutils",
    srcs = glob([
        "**/*bazel.cc",
        "**/*.h",
    ]),
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    visibility = ["//visibility:public"],
)
