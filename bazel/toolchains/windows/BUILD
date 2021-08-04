load("@bazel_tools//tools/python:toolchain.bzl", "py_runtime_pair")

package(default_visibility = ["//visibility:public"])

java_runtime(
    name = "windows_jdk8",
    srcs = [],
    java_home = "C:/openjdk",
)

py_runtime(
    name = "prebuilt_python3",
    files = ["@//prebuilts/python/windows-x86"],
    interpreter = "@//prebuilts/python/windows-x86:x64/python.exe",
    python_version = "PY3",
)

py_runtime_pair(
    name = "prebuilt_python",
    py2_runtime = None,
    py3_runtime = ":prebuilt_python3",
)

toolchain(
    name = "python_toolchain",
    exec_compatible_with = ["@platforms//os:windows"],
    toolchain = ":prebuilt_python",
    toolchain_type = "@bazel_tools//tools/python:toolchain_type",
)
