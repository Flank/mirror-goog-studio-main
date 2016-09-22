package(default_visibility = ["//visibility:public"])

cc_library(
    name = "gmock",
    srcs = glob(
        ["src/*.cc"],
        exclude = [
            "src/gmock-all.cc",
            "src/gmock_main.cc",
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
    deps = [
        "//external:gtest",
    ],
)

cc_library(
    name = "gmock_main",
    srcs = [
        "src/gmock_main.cc",
    ],
    deps = [
        ":gmock",
    ],
)
