def _impl(repository_ctx):
    s = "bazel_version = \"" + native.bazel_version + "\""
    repository_ctx.file("bazel_version.bzl", s)
    repository_ctx.file("BUILD", "")

# Helper rule for getting the bazel version. Required by com_google_absl.
bazel_version_repository = repository_rule(
    implementation = _impl,
    local = True,
)

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
        "archive": "//prebuilts/tools/common/external-src-archives/protobuf/3.9.0:protobuf-3.9.0.tar.gz",
        "strip_prefix": "protobuf-3.9.0",
        "repo_mapping": {
            "@zlib": "@zlib_repo",
        },
    },
    {
        "name": "com_google_absl",
        "archive": "//prebuilts/tools/common/external-src-archives/google_absl/LTS_2020_02_25:lts_2020_02_25.zip",
        "strip_prefix": "abseil-cpp-lts_2020_02_25",
        "repo_mapping": {
            "@upb_lib": "@upb",
        },
    },
    {
        "name": "upb",
        "archive": "//prebuilts/tools/common/external-src-archives/upb/1.0.0:upb-d8f3d6f9d415b31f3ce56d46791706c38fa311bc.tar.gz",
        "strip_prefix": "upb-d8f3d6f9d415b31f3ce56d46791706c38fa311bc",
    },
    # Perfetto Dependencies:
    # These are external dependencies to build Perfetto (from external/perfetto)
    {
        # https://github.com/google/perfetto/blob/063034c1deea22dced25d8714fd525e3a8a120d3/bazel/deps.bzl#L59
        "name": "perfetto-jsoncpp-1.0.0",
        "archive": "//prebuilts/tools/common/external-src-archives/jsoncpp/1.9.3:jsoncpp-1.9.3.tar.gz",
        "strip_prefix": "jsoncpp-1.9.3",
        "build_file": "@perfetto//bazel:jsoncpp.BUILD",
    },
    {
        "name": "perfetto-linenoise-c894b9e",
        "archive": "//prebuilts/tools/common/external-src-archives/linenoise/c894b9e:linenoise.git-c894b9e.tar.gz",
        "build_file": "@perfetto//bazel:linenoise.BUILD",
    },
    {
        "name": "perfetto-sqlite-amalgamation-3250300",
        "archive": "//prebuilts/tools/common/external-src-archives/sqlite-amalgamation/3250300:sqlite-amalgamation-3250300.zip",
        "strip_prefix": "sqlite-amalgamation-3250300",
        "build_file": "@perfetto//bazel:sqlite.BUILD",
    },
    {
        "name": "perfetto-sqlite-src-3250300",
        "archive": "//prebuilts/tools/common/external-src-archives/sqlite-src/3250300:sqlite-src-3250300.zip",
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

def _local_archive_impl(ctx):
    """Implementation of local_archive rule."""

    # Extract archive to the root of the repository.
    path = ctx.path(ctx.attr.archive)
    download_info = ctx.extract(path, "", ctx.attr.strip_prefix)

    # Set up WORKSPACE to create @{name}// repository:
    ctx.file("WORKSPACE", 'workspace(name = "{}")\n'.format(ctx.name))

    # Link optional BUILD file:
    if ctx.attr.build_file:
        ctx.delete("BUILD.bazel")
        ctx.symlink(ctx.attr.build_file, "BUILD.bazel")

# We're using a custom repository_rule instead of a regular macro (calling
# http_archive for example) because we need access to the repository_ctx object
# in order to proper resolve the path of the archives we want to extract to
# set up the repos.
#
# http_archive works nicely with absolute paths and urls, but fails to resolve
# path to labels or proper resolve relative paths to the workspace root.
local_archive = repository_rule(
    implementation = _local_archive_impl,
    attrs = {
        "archive": attr.label(
            mandatory = True,
            allow_single_file = True,
            doc = "Label for the archive that contains the target.",
        ),
        "strip_prefix": attr.string(doc = "Optional path prefix to strip from the extracted files."),
        "build_file": attr.label(
            allow_single_file = True,
            doc = "Optional label for a BUILD file to be used when setting the repository.",
        ),
    },
)

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
        local_archive(**repo)

def _setup_binds():
    for name, actual in _binds.items():
        native.bind(name = name, actual = actual)
