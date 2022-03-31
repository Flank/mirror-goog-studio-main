load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/adt/idea/kotlin-integration:version.bzl", "KOTLIN_IDE_VERSION")

setup_external_repositories()

register_toolchains(
    "@native_toolchain//:cc-toolchain-x64_linux",
    "@native_toolchain//:cc-toolchain-darwin",
    "@native_toolchain//:cc-toolchain-x64_windows-clang-cl",
    "//tools/base/bazel/toolchains/darwin:python_toolchain",
    "//tools/base/bazel/toolchains/darwin:python_toolchain_10.13",
    "//prebuilts/studio/jdk:runtime_toolchain_definition",
    "//prebuilts/studio/jdk:jdk11_toolchain_java8_definition",
    "//prebuilts/studio/jdk:jdk11_toolchain_java11_definition",
)

new_local_repository(
    name = "studio_jdk",
    path = "prebuilts/studio/jdk",
    build_file = "prebuilts/studio/jdk/BUILD.studio_jdk",
)

local_repository(
    name = "blaze",
    path = "tools/vendor/google3/blaze",
    repo_mapping = {
     "@local_jdk": "@studio_jdk",
    },
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

# Bazel cannot auto-detect python on Windows yet
# See: https://github.com/bazelbuild/bazel/issues/7844
register_toolchains("@windows_toolchains//:python_toolchain")

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

load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
    "custom_exec_properties",
)

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

# Sdk components when needed by gradle managed devices
# TODO(b/219103375) use a single 29 system image
# Not for use in Presubmit
http_file(
    name = "system_image_android-29_default_x86_zip",
    urls = ["https://dl.google.com/android/repository/sys-img/android/x86-29_r08-linux.zip"],
    sha256 = "3fa56afb1d1eb0d27f0a33f72bfa15146c0328e849181e80d21cc1bff3907621",
    downloaded_file_path = "x86-29_r08-linux.zip",
)
# Not for use in Presubmit
http_file(
    name = "emulator_zip",
    urls = ["https://dl.google.com/android/repository/emulator-linux_x64-7920983.zip"],
    sha256 = "5690099ab213a6265bc025d1d2218055e6b9d69414972c13cf2ef1c98a9c4565",
    downloaded_file_path = "emulator-linux_x64-7920983.zip",
)
# Not for use in Presubmit
http_file(
    name = "sdk_patcher_zip",
    urls = ["https://dl.google.com/android/repository/3534162-studio.sdk-patcher.zip"],
    sha256 = "18f9b8f27ea656e06b05d8f14d881df8e19803c9221c0be3e801632bcef18bed",
    downloaded_file_path = "3534162-studio.sdk-patcher.zip",
)
# Not for use in Presubmit
http_file(
    name = "build_tools_zip",
    urls = ["https://dl.google.com/android/repository/build-tools_r30.0.3-linux.zip"],
    sha256 = "24593500aa95d2f99fb4f10658aae7e65cb519be6cd33fa164f15f27f3c4a2d6",
    downloaded_file_path = "build-tools_r30.0.3-linux.zip",
)
# Not for use in Presubmit
http_file(
    name = "platform_32_zip",
    urls = ["https://dl.google.com/android/repository/platform-32_r01.zip"],
    sha256 = "01d8da1c900e70fcf5da39767d5444e39928935b1a5927055ce749fc348ca7ae",
    downloaded_file_path = "platform-32_r01.zip",
)
# Not for use in Presubmit
http_file(
    name = "platform_tools_zip",
    urls = ["https://dl.google.com/android/repository/platform-tools_r31.0.3-linux.zip"],
    sha256 = "e6cb61b92b5669ed6fd9645fad836d8f888321cd3098b75588a54679c204b7dc",
    downloaded_file_path = "platform-tools_r31.0.3-linux.zip",
)
# Not for use in Presubmit
http_file(
    name = "sdk_tools_zip",
    urls = ["https://dl.google.com/android/repository/sdk-tools-linux-4333796.zip"],
    sha256 = "92ffee5a1d98d856634e8b71132e8a95d96c83a63fde1099be3d86df3106def9",
    downloaded_file_path = "sdk-tools-linux-4333796.zip",
)

# An empty local repository which must be overridden according to the instructions at
# go/agp-profiled-benchmarks if running the "_profiled" AGP build benchmarks.
local_repository(
    name = "yourkit_controller",
    path = "tools/base/yourkit-controller",
)

new_local_repository(
    name = "maven",
    path = "prebuilts/tools/common/m2",
    build_file = "tools/base/bazel/maven/BUILD.maven",
)
