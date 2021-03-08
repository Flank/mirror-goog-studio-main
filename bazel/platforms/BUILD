load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
)

package(default_visibility = ["//visibility:public"])

platform(
    name = "linux",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
        "@bazel_tools//tools/cpp:clang",
        "@bazel_toolchains//constraints:xenial",
        "@bazel_toolchains//constraints/sanitizers:support_msan",
    ],
    exec_properties = create_rbe_exec_properties_dict(
        container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu18-as@sha256:b472f2ff3080b759d9abd112930170034603f8301cbce25e609be4733f5c0c74",
        docker_add_capabilities = "SYS_NICE",
        docker_network = "off",
        docker_privileged = True,  # This is required to support emulators/nested KVM.
        docker_use_urandom = True,
        labels = {"machine-size": "default"},
        os_family = "Linux",
    ),
)

platform(
    name = "windows",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    exec_properties = create_rbe_exec_properties_dict(
        container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as@sha256:64a7c97ac08d75b9efde179993adb55986869369af320d8fbe1b4dc16e678805",
        labels = {"machine-size": "default"},
        os_family = "Windows",
    ),
)

platform(
    name = "macos",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:macos",
    ],
)
