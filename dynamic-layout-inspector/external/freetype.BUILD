config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

srcs = glob(["src/**/*.h"]) + [
    "src/autofit/autofit.c",
    "src/base/ftbase.c",
    "src/base/ftbbox.c",
    "src/base/ftbitmap.c",
    "src/base/ftdebug.c",
    "src/base/ftfstype.c",
    "src/base/ftgasp.c",
    "src/base/ftglyph.c",
    "src/base/ftinit.c",
    "src/base/ftmm.c",
    "src/base/ftstroke.c",
    "src/base/ftsystem.c",
    "src/base/fttype1.c",
    "src/cff/cff.c",
    "src/cid/type1cid.c",
    "src/gzip/ftgzip.c",
    "src/psaux/psaux.c",
    "src/pshinter/pshinter.c",
    "src/psnames/psnames.c",
    "src/raster/raster.c",
    "src/sfnt/sfnt.c",
    "src/smooth/smooth.c",
    "src/truetype/truetype.c",
    "src/type1/type1.c",
]

cc_library(
    name = "libft2",
    srcs = srcs,
    textual_hdrs = glob(["src/**/*.c"], exclude = srcs),
    hdrs = glob(["include/**/*.h"]),
    copts = ["-DFT2_BUILD_LIBRARY"] + select({
        "windows": [],  #TODO: anything needed?
        "//conditions:default": [
            "-W",
            "-Wall",
            "-Werror",
            "-Wno-implicit-fallthrough",
            "-DDARWIN_NO_CARBON",
            "-O2",
            "-Wno-unused-parameter",
            "-Wno-unused-variable",
        ],
    }),
    includes = [
        "include",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "@libpng_repo//:libpng",
        "@zlib_repo//:zlib",
    ],
)
