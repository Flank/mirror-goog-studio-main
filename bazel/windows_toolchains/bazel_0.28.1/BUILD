# Copyright 2018 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This becomes the BUILD file for @local_config_cc// under Windows.

package(default_visibility = ["//visibility:public"])

load(":windows_cc_toolchain_config.bzl", "cc_toolchain_config")

cc_library(
    name = "malloc",
)

filegroup(
    name = "empty",
    srcs = [],
)

# Hardcoded toolchain, legacy behaviour.
cc_toolchain_suite(
    name = "toolchain",
    toolchains = {
        "x64_windows|msvc-cl": ":cc-compiler-x64_windows",
        "x64_windows": ":cc-compiler-x64_windows",
    },
)

cc_toolchain(
    name = "cc-compiler-x64_windows",
    all_files = ":empty",
    ar_files = ":empty",
    as_files = ":empty",
    compiler_files = ":empty",
    dwp_files = ":empty",
    linker_files = ":empty",
    objcopy_files = ":empty",
    strip_files = ":empty",
    supports_param_files = 1,
    toolchain_config = ":msvc_x64",
    toolchain_identifier = "msvc_x64",
)

cc_toolchain_config(
    name = "msvc_x64",
    abi_libc_version = "local",
    abi_version = "local",
    compiler = "msvc-cl",
    cpu = "x64_windows",
    cxx_builtin_include_directories = [
        "C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC\\INCLUDE",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.10240.0\\ucrt",
        "C:\\Program Files (x86)\\Windows Kits\\8.1\\include\\shared",
        "C:\\Program Files (x86)\\Windows Kits\\8.1\\include\\um",
        "C:\\Program Files (x86)\\Windows Kits\\8.1\\include\\winrt",
        "C:\\botcode\\w",
    ],
    dbg_mode_debug_flag = "/DEBUG:FULL",
    fastbuild_mode_debug_flag = "/DEBUG:FASTLINK",
    host_system_name = "local",
    msvc_cl_path = "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/cl.exe",
    msvc_env_include = "C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC\\INCLUDE;C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.10240.0\\ucrt;C:\\Program Files (x86)\\Windows Kits\\8.1\\include\\shared;C:\\Program Files (x86)\\Windows Kits\\8.1\\include\\um;C:\\Program Files (x86)\\Windows Kits\\8.1\\include\\winrt;",
    msvc_env_lib = "C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC\\LIB\\amd64;C:\\Program Files (x86)\\Windows Kits\\10\\lib\\10.0.10240.0\\ucrt\\x64;C:\\Program Files (x86)\\Windows Kits\\8.1\\lib\\winv6.3\\um\\x64;",
    msvc_env_path = "C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC\\BIN\\amd64;C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319;C:\\Windows\\Microsoft.NET\\Framework64\\;C:\\Program Files (x86)\\Windows Kits\\8.1\\bin\\x64;C:\\Program Files (x86)\\Windows Kits\\8.1\\bin\\x86;;C:\\Windows\\system32",
    msvc_env_tmp = "C:\\Users\\ContainerAdministrator\\AppData\\Local\\Temp",
    msvc_lib_path = "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/lib.exe",
    msvc_link_path = "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/link.exe",
    msvc_ml_path = "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/ml64.exe",
    target_libc = "msvcrt",
    target_system_name = "local",
    tool_paths = {
        "ar": "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/lib.exe",
        "ml": "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/ml64.exe",
        "cpp": "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/cl.exe",
        "gcc": "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/cl.exe",
        "gcov": "wrapper/bin/msvc_nop.bat",
        "ld": "C:/Program Files (x86)/Microsoft Visual Studio 14.0/VC/bin/amd64/link.exe",
        "nm": "wrapper/bin/msvc_nop.bat",
        "objcopy": "wrapper/bin/msvc_nop.bat",
        "objdump": "wrapper/bin/msvc_nop.bat",
        "strip": "wrapper/bin/msvc_nop.bat",
    },
    toolchain_identifier = "msvc_x64",
)

toolchain(
    name = "cc-toolchain-x64_windows",
    exec_compatible_with = [
        "@bazel_tools//platforms:x86_64",
        "@bazel_tools//platforms:windows",
    ],
    target_compatible_with = [
        "@bazel_tools//platforms:x86_64",
        "@bazel_tools//platforms:windows",
    ],
    toolchain = ":cc-compiler-x64_windows",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)

filegroup(
    name = "link_dynamic_library",
    srcs = ["link_dynamic_library.sh"],
)
