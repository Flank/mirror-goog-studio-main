load("//tools/base/bazel:bazel.bzl", "platform_filegroup")

filegroup(
    name = "licenses",
    srcs = glob(
        include = ["*/licenses/**"],
    ),
    visibility = ["//visibility:public"],
)

# TODO: Bump up to 25.0.0 once we fix AS tests.
filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/24.0.3"],
    visibility = ["//visibility:public"],
)

# TODO: Migrate the packages below that depend on specific versions.
filegroup(
    name = "build-tools/25.0.0",
    srcs = glob(
        include = ["*/build-tools/25.0.0/**"],
    ),
)

filegroup(
    name = "build-tools/24.0.3",
    srcs = glob(
        include = ["*/build-tools/24.0.3/**"],
    ),
)

filegroup(
    name = "build-tools/24.0.1",
    srcs = glob(
        include = ["*/build-tools/24.0.1/**"],
    ),
)

filegroup(
    name = "build-tools/23.0.0",
    srcs = glob(
        include = ["*/build-tools/23.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "build-tools/22.0.1",
    srcs = glob(
        include = ["*/build-tools/22.0.1/**"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "build-tools/21.0.0",
    srcs = glob(
        include = ["*/build-tools/21.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "build-tools/20.0.0",
    srcs = glob(
        include = ["*/build-tools/20.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "platform-tools",
    srcs = glob(
        include = ["*/platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-25"],
    visibility = ["//visibility:public"],
)

# Version-specific rule left private in hopes we can depend on platforms/latest instead.
# TODO: Migrate the packages below that depend on specific versions.
platform_filegroup(
    name = "platforms/android-25",
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "add-ons/addon-google_apis-google-24",
    srcs = glob(["*/add-ons/addon-google_apis-google-24/**"]),
)
