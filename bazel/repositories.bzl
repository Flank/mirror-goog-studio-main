_repositories = [
    {
        "name": "freetype_repo",
        "build_file": "tools/base/dynamic-layout-inspector/external/freetype.BUILD",
        "path": "external/freetype",
    },
    {
        "name": "skia_repo",
        "build_file": "tools/base/dynamic-layout-inspector/external/skia.BUILD",
        "path": "external/skia",
    },
    {
        # files in tools/base that need to be referenced by the skia_repo BUILD
        "name": "skia_extra",
        "path": "tools/base/dynamic-layout-inspector/external/skia-extra",
    },
    {
        "name": "libpng_repo",
        "build_file": "tools/base/dynamic-layout-inspector/external/libpng.BUILD",
        "path": "external/libpng",
    },
    {
        "name": "gmock_repo",
        "build_file": "tools/base/profiler/native/external/gmock.BUILD",
        "path": "external/googletest/googlemock",
    },
    {
        "name": "gtest_repo",
        "build_file": "tools/base/profiler/native/external/gtest.BUILD",
        "path": "external/googletest/googletest",
    },
    {
        "name": "slicer_repo",
        "build_file": "tools/base/profiler/native/external/slicer.BUILD",
        "path": "external/dexter/slicer",
    },
    {
        "name": "perfetto_repo",
        "build_file": "tools/base/profiler/native/external/perfetto.BUILD",
        "path": "external/perfetto",
    },
    {
        "name": "protobuf_repo",
        "path": "external/protobuf",
    },
    {
        "name": "nanopb_repo",
        "path": "external/nanopb-c",
    },
    {
        "name": "zlib_repo",
        "path": "external/zlib",
    },
    {
        "name": "grpc_repo",
        "path": "external/grpc-grpc",
    },
    {
        "name": "androidndk",
        "api_level": 21,
    },
    {
        "name": "gflags_repo",
        "path": "external/gflags",
    },
]

_binds = {
    "gtest_main": "@gtest_repo//:gtest_main",
    "gtest": "@gtest_repo//:gtest",
    "gmock_main": "@gmock_repo//:gmock_main",
    "slicer": "@slicer_repo//:slicer",
    "protobuf_clib": "@protobuf_repo//:protoc_lib",
    "nanopb": "@nanopb_repo//:nanopb",
    "zlib": "@zlib_repo//:zlib",
    "protobuf_headers": "@protobuf_repo//:protobuf_headers",
    "protobuf": "@protobuf_repo//:protobuf",
    "protoc": "@protobuf_repo//:protoc",
    "grpc_cpp_plugin": "@grpc_repo//:grpc_cpp_plugin",
    "grpc++_unsecure": "@grpc_repo//:grpc++_unsecure",
    "madler_zlib": "@zlib_repo//:zlib",  # Needed for grpc
}

def setup_external_repositories(prefix = ""):
    for _repo in _repositories:
        repo = dict(_repo)
        if repo["name"] == "androidndk":
            native.android_ndk_repository(**repo)
        else:
            repo["path"] = prefix + repo["path"]
            if "build_file" in repo:
                repo["build_file"] = prefix + repo["build_file"]
                native.new_local_repository(**repo)
            else:
                native.local_repository(**repo)

    for name, actual in _binds.items():
        native.bind(name = name, actual = actual)
