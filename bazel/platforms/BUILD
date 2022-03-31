load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
)
load("@bazel_skylib//lib:dicts.bzl", "dicts")

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
    exec_properties = dicts.add(
        create_rbe_exec_properties_dict(
            container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu18-as@sha256:b472f2ff3080b759d9abd112930170034603f8301cbce25e609be4733f5c0c74",
            docker_add_capabilities = "SYS_NICE",
            docker_network = "off",
            docker_privileged = True,  # This is required to support emulators/nested KVM.
            docker_use_urandom = True,
            labels = {"machine-size": "default"},
            os_family = "Linux",
        ),
        # cache-silo-key: this is essentially a "salt" added to the remote cache key.
        # Change it to a new value when we want to create a new cache from scratch.
        {"cache-silo-key": "20211108"},
    ),
)

platform(
    name = "windows",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    exec_properties = create_rbe_exec_properties_dict(
        container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as@sha256:1ad1aa4e016b0b9ee41e5c0d587c3fc51033485b0242fb7ee9fdd242c7598879",
        labels = {"machine-size": "default"},
        os_family = "Windows",
    ),
)

constraint_setting(
    name = "macos_version",
)

constraint_value(
    name = "macos_latest",
    constraint_setting = ":macos_version",
)

platform(
    name = "macos",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:macos",
        ":macos_latest",
    ],
)

constraint_value(
    name = "macos_10.13",
    constraint_setting = ":macos_version",
)

platform(
    name = "macpro10.13",
    constraint_values = [":macos_10.13"],
    parents = [":macos"],
)
