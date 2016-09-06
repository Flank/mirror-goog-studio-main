package(default_visibility = ["//visibility:public"])

cc_library(
    name = "gtest",
    srcs = glob(
        ["src/*.cc"],
        exclude = [
            "src/gtest-all.cc",
            "src/gtest_main.cc",
        ],
    ),
    hdrs = glob([
        "include/**/*.h",
        "src/*.h",
    ]),
    includes = [
        "include",
    ],
    linkopts = ["-lpthread"],
)

cc_library(
    name = "gtest_main",
    srcs = [
        "src/gtest_main.cc",
    ],
    deps = [
        ":gtest",
    ],
)
