load("@bazel_tools//tools/python:toolchain.bzl", "py_runtime_pair")

py_runtime(
    name = "local_py3_runtime",
    interpreter_path = "/usr/bin/python3",
    python_version = "PY3",
    stub_shebang = "#!/usr/bin/python3",
)

py_runtime_pair(
    name = "local_py_runtime_pair",
    py2_runtime = None,
    py3_runtime = ":local_py3_runtime",
)

toolchain(
    name = "python_toolchain",
    exec_compatible_with = [
        "@platforms//os:macos",
        "//tools/base/bazel/platforms:macos_latest",
    ],
    toolchain = ":local_py_runtime_pair",
    toolchain_type = "@bazel_tools//tools/python:toolchain_type",
)

py_runtime(
    name = "local_py3_runtime_macos10.13",
    interpreter_path = "/usr/local/bin/python3",
    python_version = "PY3",
    stub_shebang = "#!/usr/local/bin/python3",
)

py_runtime_pair(
    name = "local_py_runtime_pair_macos10.13",
    py2_runtime = None,
    py3_runtime = ":local_py3_runtime_macos10.13",
)

toolchain(
    name = "python_toolchain_10.13",
    exec_compatible_with = ["//tools/base/bazel/platforms:macos_10.13"],
    toolchain = ":local_py_runtime_pair_macos10.13",
    toolchain_type = "@bazel_tools//tools/python:toolchain_type",
)

filegroup(
    name = "extra_files",
    srcs = [
        "wrapped_ar",
    ],
    visibility = ["//visibility:public"],
)
