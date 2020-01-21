load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Bazel repository mapped to git repositories.
_git = [
    {
        "name": "native_toolchain",
        "build_file": "tools/base/bazel/toolchains/clang.BUILD",
        "path": "prebuilts/clang",
    },
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
        "name": "googletest",
        "path": "external/googletest",
    },
    {
        "name": "slicer_repo",
        "build_file": "tools/base/profiler/native/external/slicer.BUILD",
        "path": "external/dexter/slicer",
    },
    {
        "name": "perfetto",
        "path": "external/perfetto",
        "repo_mapping": {
            "@com_google_protobuf": "@com_google_protobuf",
        },
    },
    {
        "name": "perfetto_cfg",
        "path": "tools/base/bazel/perfetto_cfg",
        "build_file_content": "",
    },
    # TODO: Migrate users of @perfetto_repo to @perfetto
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

# Bazel repository mapped to archive files, containing the sources.
_archives = [
    {
        # Offical proto rules relies on a hardcoded "@com_google_protobuf", so we cannot
        # name this as protobuf-3.9.0 or similar.
        "name": "com_google_protobuf",
        "url": "prebuilts/tools/common/external-src-archives/protobuf/3.9.0/protobuf-3.9.0.tar.gz",
        "sha256": "2ee9dcec820352671eb83e081295ba43f7a4157181dad549024d7070d079cf65",
        "strip_prefix": "protobuf-3.9.0",
        "repo_mapping": {
            "@zlib": "@zlib_repo",
        },
    },
    # Perfetto Dependencies:
    # These are external dependencies to build Perfetto (from external/perfetto)
    {
        "name": "perfetto-jsoncpp-1.0.0",
        "url": "prebuilts/tools/common/external-src-archives/jsoncpp/1.0.0/jsoncpp-1.0.0.tar.gz",
        "sha256": "e7eeb9b96d10cfd2f6205a09899f9800931648f652e09731821854c9ce0c7a1a",
        "strip_prefix": "jsoncpp-1.0.0",
        "build_file": "@perfetto//bazel:jsoncpp.BUILD",
    },
    {
        "name": "perfetto-linenoise-c894b9e",
        "url": "prebuilts/tools/common/external-src-archives/linenoise/c894b9e/linenoise.git-c894b9e.tar.gz",
        "sha256": "988a6922eedb9a3de2554801d11562d8a4a3df633c53a5dbc5fb1468b03ebcb2",
        "build_file": "@perfetto//bazel:linenoise.BUILD",
    },
    {
        "name": "perfetto-sqlite-amalgamation-3250300",
        "url": "prebuilts/tools/common/external-src-archives/sqlite-amalgamation/3250300/sqlite-amalgamation-3250300.zip",
        "sha256": "2ad5379f3b665b60599492cc8a13ac480ea6d819f91b1ef32ed0e1ad152fafef",
        "strip_prefix": "sqlite-amalgamation-3250300",
        "build_file": "@perfetto//bazel:sqlite.BUILD",
    },
    {
        "name": "perfetto-sqlite-src-3250300",
        "url": "prebuilts/tools/common/external-src-archives/sqlite-src/3250300/sqlite-src-3250300.zip",
        "sha256": "c7922bc840a799481050ee9a76e679462da131adba1814687f05aa5c93766421",
        "strip_prefix": "sqlite-src-3250300",
        "build_file": "@perfetto//bazel:sqlite.BUILD",
    },
    # End Perfetto Dependencies.
]

_binds = {
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
    "grpc-all-java": "//tools/base/third_party:io.grpc_grpc-all",
}

def setup_external_repositories(prefix = ""):
    _setup_git_repos(prefix)
    _setup_archive_repos(prefix)
    _setup_binds()

def _setup_git_repos(prefix = ""):
    for _repo in _git:
        repo = dict(_repo)
        if repo["name"] == "androidndk":
            native.android_ndk_repository(**repo)
        else:
            repo["path"] = prefix + repo["path"]
            if "build_file" in repo:
                repo["build_file"] = prefix + repo["build_file"]
                native.new_local_repository(**repo)
            elif "build_file_content" in repo:
                native.new_local_repository(**repo)
            else:
                native.local_repository(**repo)

def _setup_archive_repos(prefix = ""):
    for _repo in _archives:
        repo = dict(_repo)
        repo["url"] = "file:///" + prefix + repo["url"]
        http_archive(**repo)

def _setup_binds():
    for name, actual in _binds.items():
        native.bind(name = name, actual = actual)
