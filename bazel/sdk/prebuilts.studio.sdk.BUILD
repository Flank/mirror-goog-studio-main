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
    visibility = [
        "//tools/adt/idea/android:__pkg__",
        "//tools/adt/idea/designer:__pkg__",
        "//tools/base/build-system/builder:__pkg__",
        "//tools/base/build-system/gradle:__pkg__",
        "//tools/base/build-system/gradle-core:__pkg__",
    ],
)

filegroup(
    name = "build-tools/24.0.3",
    srcs = glob(
        include = ["*/build-tools/24.0.3/**"],
    ),
)

filegroup(
    name = "platform-tools",
    srcs = glob(
        include = ["*/platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "constraint-layout_latest",
    srcs = [":constraint-layout_1.0.0-beta4"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "constraint-layout_1.0.0-beta4",
    srcs = glob(
        include = [
            "*/extras/m2repository/com/android/support/constraint/constraint-layout/1.0.0-beta4/**",
            "*/extras/m2repository/com/android/support/constraint/constraint-layout-solver/1.0.0-beta4/**",
        ],
    ),
)

filegroup(
    name = "support_latest",
    srcs = [":support_25.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "support_25.0.0",
    srcs = glob(
        include = ["*/extras/android/m2repository/com/android/support/*/25.0.0/**"],
    ),
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
)

platform_filegroup(
    name = "platforms/android-24",
    visibility = [
        "//tools/base/build-system/gradle:__pkg__",
        "//tools/data-binding:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-23",
    visibility = ["//tools/base/build-system/integration-test:__pkg__"],
)

platform_filegroup(
    name = "platforms/android-21",
    visibility = ["//tools/base/build-system/integration-test:__pkg__"],
)

platform_filegroup(
    name = "platforms/android-19",
    visibility = ["//tools/base/build-system/integration-test:__pkg__"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-latest",
    srcs = ["add-ons/addon-google_apis-google-24"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-24",
    srcs = glob(["*/add-ons/addon-google_apis-google-24/**"]),
)

filegroup(
    name = "_android_jar",
    srcs = glob(["*/platforms/android-25/android.jar"]),
)

java_import(
    name = "android_jar",
    jars = [":_android_jar"],
    neverlink = 1,
    visibility = ["//visibility:public"],
)
