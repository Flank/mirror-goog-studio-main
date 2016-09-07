filegroup(
    name = "platform-tools",
    srcs = glob(
        include = ["*/platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-24"],
    visibility = ["//visibility:public"],
)

# This contains only top-level files, no skins or resources. This way we limit
# the number of symlinks required in the sandbox, working around the limit on
# symlinks number (as well as improving performance somewhat).
filegroup(
    name = "platforms/latest.minimal",
    srcs = [":platforms/android-24.minimal"],
    visibility = ["//visibility:public"],
)

# Version-specific rule left private in hopes we can depend on platforms/latest instead.
# TODO: Migrate the packages below that depend on specific versions.
filegroup(
    name = "platforms/android-24",
    srcs = glob(
        include = ["*/platforms/android-24/**"],
    ),
)

filegroup(
    name = "platforms/android-24.minimal",
    srcs = glob(
        include = ["*/platforms/android-24/*"],
    ),
)

filegroup(
    name = "platforms/android-21",
    srcs = glob(
        include = ["*/platforms/android-21/**"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "platforms/android-21.minimal",
    srcs = glob(
        include = ["*/platforms/android-21/*"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "platforms/android-19",
    srcs = glob(
        include = ["*/platforms/android-19/**"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "platforms/android-19.minimal",
    srcs = glob(
        include = ["*/platforms/android-19/*"],
    ),
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
    ],
)

filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/24.0.1"],
    visibility = ["//visibility:public"],
)

# TODO: Migrate the packages below that depend on specific versions.
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
