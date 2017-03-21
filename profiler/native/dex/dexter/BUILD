cc_binary(
    name = "dexter",
    srcs = [
        "dexter.cc",
        "dexter.h",
        "dissasembler.cc",
        "dissasembler.h",
        "experimental.cc",
        "experimental.h",
        "main.cc",
    ],
    copts = [
        "-Itools/base/profiler/native/dex",
    ],
    deps = [
        "//tools/base/profiler/native/dex/slicer",
        "@zlib_repo//:zlib",
    ],
)

py_test(
    name = "dexter_tests",
    srcs = ["dexter_tests.py"],
    args = ["-cmd=$(location dexter)"],
    data = [
        "//tools/base/profiler/native/dex/dexter",
        "//tools/base/profiler/native/testdata:dex",
    ],
)
