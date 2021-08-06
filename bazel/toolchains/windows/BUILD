load("@bazel_tools//tools/python:toolchain.bzl", "py_runtime_pair")

package(default_visibility = ["//visibility:public"])

java_runtime(
    name = "windows_jdk8",
    srcs = [],
    java_home = "C:/openjdk",
)

py_runtime(
    name = "python2_fixed_path",
    interpreter_path = "C:/Python27/python.exe",
    python_version = "PY2",
)

py_runtime(
    name = "python3_fixed_path",
    interpreter_path = "C:/python3/python.exe",
    python_version = "PY3",
)

py_runtime_pair(
    name = "python_fixed_path",
    py2_runtime = ":python2_fixed_path",
    py3_runtime = ":python3_fixed_path",
)

toolchain(
    name = "python_toolchain",
    exec_compatible_with = ["@platforms//os:windows"],
    toolchain = ":python_fixed_path",
    toolchain_type = "@bazel_tools//tools/python:toolchain_type",
)
