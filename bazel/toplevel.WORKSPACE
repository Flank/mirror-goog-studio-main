load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

setup_external_repositories()

register_toolchains(
  "@native_toolchain//:cc-toolchain-x64_linux",
  "@native_toolchain//:cc-toolchain-darwin",
  "@native_toolchain//:cc-toolchain-x64_windows-clang-cl",
)

local_repository(
    name = "blaze",
    path = "tools/vendor/google3/blaze",
)

load("@blaze//:binds.bzl", "blaze_binds")

blaze_binds()

local_repository(
    name = "io_bazel_rules_kotlin",
    path = "tools/external/bazelbuild-rules-kotlin",
)

local_repository(
    name = "windows_toolchains",
    path = "tools/base/bazel/toolchains/windows",
)

http_archive(
    name = "bazel_toolchains",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/1.1.3.tar.gz",
        "https://github.com/bazelbuild/bazel-toolchains/archive/1.1.3.tar.gz",
    ],
    strip_prefix = "bazel-toolchains-1.1.3",
    sha256 = "83352b6e68fa797184071f35e3b67c7c8815efadcea81bb9cdb6bbbf2e07d389",
)

load(
    "@bazel_toolchains//repositories:repositories.bzl",
    bazel_toolchains_repositories = "repositories",
)

bazel_toolchains_repositories()

setup_external_sdk(
    name = "externsdk",
)

## Coverage related workspaces
# Coverage reports construction
local_repository(
    name = "cov",
    path = "tools/base/bazel/coverage",
)
# Coverage results processing
load("@cov//:results.bzl", "setup_testlogs_loop_repo")
setup_testlogs_loop_repo()
# Coverage baseline construction
load("@cov//:baseline.bzl", "setup_bin_loop_repo")
setup_bin_loop_repo()

load("@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
     "create_rbe_exec_properties_dict",
     "custom_exec_properties")
custom_exec_properties(
    name = "exec_properties",
    constants = {
        "LARGE_MACHINE": create_rbe_exec_properties_dict(
	    labels = {"machine-size": "large"},
        ),
    },
)

# Download system images when needed by avd.
http_archive(
    name = "system_image_android-28_default_x86",
    url = "https://dl.google.com/android/repository/sys-img/android/x86-28_r04.zip",
    sha256 = "7c3615c55b64713fe56842a12fe6827d6792cb27a9f95f9fa3aee1ff1be47f16",
    strip_prefix = "x86",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
)
http_archive(
    name = "system_image_android-29_default_x86_64",
    url = "https://dl.google.com/android/repository/sys-img/android/x86_64-29_r06.zip",
    sha256 = "5d866d9925ad7b142c89bbffc9ce9941961e08747d6f64e28b5158cc44ad95cd",
    strip_prefix = "x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
)

# An empty local repository which must be overridden according to the instructions at
# go/agp-profiled-benchmarks if running the "_profiled" AGP build benchmarks.
local_repository(
    name = "yourkit_controller",
    path = "tools/base/yourkit-controller",
)
