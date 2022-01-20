config_setting(
    name = "darwin",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

cc_library(
    name = "libpng",
    srcs = [
        "intel/filter_sse2_intrinsics.c",
        "intel/intel_init.c",
        "png.c",
        "pngerror.c",
        "pngget.c",
        "pngmem.c",
        "pngpread.c",
        "pngread.c",
        "pngrio.c",
        "pngrtran.c",
        "pngrutil.c",
        "pngset.c",
        "pngtrans.c",
        "pngwio.c",
        "pngwrite.c",
        "pngwtran.c",
        "pngwutil.c",
    ] + glob(["*.h"], exclude = ["png.h"]),
    hdrs = ["png.h"],
    copts = select({
        "windows": [],
        "darwin": [
            "-std=gnu89",
            "-Wall",
            "-Werror",
            "-Wno-unused-parameter",
        ],
        "//conditions:default": [
            "-std=gnu89",
            "-Wall",
            "-Werror",
            "-Wno-unused-parameter",
            "-Wno-unused-but-set-variable",
        ],
    }),
    includes = [
        ".",
    ],
    visibility = ["//visibility:public"],
    deps = ["@zlib_repo//:zlib"],
)
