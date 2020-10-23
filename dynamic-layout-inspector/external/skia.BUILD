# TODO: ensure building with clang
# TODO: use info from external/skia/public.bzl?

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mac",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

cc_library(
    name = "libskia",
    srcs = glob([
        "src/**/*.h",
        "src/**/*.inc",
        "src/c/*.cpp",
        "src/core/*.cpp",
        "src/codec/*.cpp",
        "src/effects/*.cpp",
        "src/effects/imagefilters/*.cpp",
        "src/image/*.cpp",
        "src/images/*.cpp",
        "src/lazy/*.cpp",
        "src/opts/*.cpp",
        "src/pathops/*.cpp",
        "src/ports/SkFontHost*.cpp",
        "src/ports/SkImageEncoder*.cpp",
        "src/sfnt/*.cpp",
        "src/sksl/*.cpp",
        "src/sksl/ir/*.cpp",
        "src/shaders/*.cpp",
        "src/shaders/gradients/*.cpp",
        "src/utils/*.cpp",
        "src/utils/*.inc",
        "third_party/etc1/*.cpp",
        "third_party/etc1/*.h",
        "third_party/gif/*.cpp",
        "third_party/gif/*.h",
        "third_party/skcms/*.cc",
        "third_party/skcms/**/*.h",
    ], exclude = [
        "src/core/SkPicture_none.cpp",
        "src/codec/SkRawCodec.cpp",
        "src/codec/SkWuffsCodec.cpp",
        "src/codec/SkJpeg*.cpp",
        "src/codec/SkWebp*.cpp",
        "src/image/*_Gpu.cpp",
        "src/image/*_GpuYUVA.cpp",
        "src/image/*_GpuBase.cpp",
        "src/images/SkJpeg*.cpp",
        "src/images/SkJPEG*.cpp",
        "src/images/SkWebp*.cpp",
        "src/utils/SkLua*.cpp",
        "src/utils/SkShaperJSONWriter.cpp",
        "src/sksl/SkSLMain.cpp",
    ]) + [
        "src/pdf/SkDocument_PDF_None.cpp",
        "src/ports/SkFontMgr_custom.cpp",
        "src/ports/SkFontMgr_custom_empty.cpp",
        "src/ports/SkImageGenerator_skia.cpp",
        "src/ports/SkDiscardableMemory_none.cpp",
        "src/ports/SkGlobalInitialization_default.cpp",
        "src/ports/SkMemory_malloc.cpp",
        "src/ports/SkOSFile_stdio.cpp",
    ] + select({
        "windows": [
            "src/ports/SkOSFile_win.cpp",
            "src/ports/SkOSLibrary_win.cpp",
            "src/ports/SkTLS_win.cpp",
            "src/ports/SkDebug_win.cpp",
            "src/ports/SkFontMgr_custom_empty_factory.cpp",
        ],
        "mac": [
            "src/ports/SkOSFile_posix.cpp",
            "src/ports/SkTLS_pthread.cpp",
            "src/ports/SkDebug_stdio.cpp",
            "src/ports/SkOSLibrary_posix.cpp",
        ] + glob(["src/utils/mac/*.cpp"]),
        "//conditions:default": [
            "src/ports/SkOSFile_posix.cpp",
            "src/ports/SkTLS_pthread.cpp",
            "src/ports/SkDebug_stdio.cpp",
            "src/ports/SkOSLibrary_posix.cpp",
            "src/ports/SkFontMgr_custom_empty_factory.cpp",
        ],
    }),
    hdrs = glob(["include/**/*.h"]),
    copts = [
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-DFORTIFY_SOURCE=1",
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
    ] + select({
        "windows": ["/DSK_BUILD_FOR_WIN"],  # TODO: anything else needed here?
        "mac": ["-DSK_BUILD_FOR_MAC"],  # TODO: anything else needed here?
        "//conditions:default": [
            "-DSK_BUILD_FOR_UNIX",
            "-mssse3",
            "-Wno-implicit-fallthrough",
            "-Wno-missing-field-initializers",
            "-Wno-thread-safety-analysis",
            "-Wno-unused-parameter",
            "-Wno-unused-variable",
            "-fvisibility=hidden",
            "-fexceptions",
            "-std=c++17",
        ],
    }),
    linkopts = select({"mac": [
        "-framework CoreGraphics",
        "-framework CoreText",
    ], "//conditions:default": []}),
    includes = [
        "include/atlastext/",
        "include/c/",
        "include/codec/",
        "include/core/",
        "include/docs/",
        "include/effects/",
        "include/encode/",
        "include/gpu/",
        "include/pathops/",
        "include/ports/",
        "include/private/",
        "include/svg/",
        "include/utils/",
        "include/third_party/skcms/",
        "include/third_party/vulkan/",
        "src/c/",
        "src/codec/",
        "src/core/",
        "src/effects/",
        "src/fonts/",
        "src/image/",
        "src/images/",
        "src/lazy/",
        "src/opts/",
        "src/pathops/",
        "src/pdf/",
        "src/ports/",
        "src/sfnt/",
        "src/shaders/",
        "src/shaders/gradients/",
        "src/sksl/",
        "src/utils/",
        "src/utils/win/",
        "src/xml/",
        "third_party/etc1/",
        "third_party/gif/",
        "third_party/skcms/",
    ] + select({"mac": ["include/utils/mac"], "//conditions:default": []}),
    visibility = ["//visibility:public"],
    deps = [
        "@freetype_repo//:libft2",
        "@libpng_repo//:libpng",
        "@skia_extra//:skia_includes",
    ],
)
