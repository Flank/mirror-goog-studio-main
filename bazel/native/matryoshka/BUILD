load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS")

cc_library(
    name = "libmatryoshka",
    srcs = [
        "doll.cpp",
    ],
    hdrs = [
        "doll.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "test_exec_input",
    srcs = [
        "test.cpp",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [":libmatryoshka"],
)

genrule(
    name = "test_exec",
    srcs = [":test_exec_input"],
    outs = ["a.out"],
    cmd = "$(location //tools/base/bazel:matryoshka) -i $(location :test_exec_input) -o $(location a.out)" +
          " -p a.out=$(location :test_exec_input) -p b.out=$(location :test_exec_input)",
    tools = ["//tools/base/bazel:matryoshka"],
)

sh_test(
    name = "simple_test",
    size = "medium",
    srcs = ["simple_test.sh"],
    data = [":test_exec"],
    tags = [
        "no_mac",  # b/120893915
        "no_windows",
    ],
)
