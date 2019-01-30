load("//tools/base/bazel/sdk:sdk_utils.bzl", "platform_filegroup", "sdk_glob", "sdk_path")

filegroup(
    name = "licenses",
    srcs = sdk_glob(
        include = ["licenses/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/latest-preview",
    srcs = [":build-tools/26.0.0"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "dxlib-preview",
    jars = sdk_path(["build-tools/26.0.0/lib/dx.jar"]),
)

java_binary(
    name = "dx-preview",
    main_class = "com.android.dx.command.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":dxlib-preview"],
)

filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/28.0.3"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/minimum",
    srcs = [":build-tools/25.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/28.0.3",
    srcs = sdk_glob(
        include = ["build-tools/28.0.3/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/28.0.0",
    srcs = sdk_glob(
        include = ["build-tools/28.0.0/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/27.0.3",
    srcs = sdk_glob(
        include = ["build-tools/27.0.3/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/27.0.1",
    srcs = sdk_glob(
        include = ["build-tools/27.0.1/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

filegroup(
    name = "build-tools/27.0.0",
    srcs = sdk_glob(
        include = ["build-tools/27.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

filegroup(
    name = "build-tools/26.0.2",
    srcs = sdk_glob(
        include = ["build-tools/26.0.2/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/26.0.0",
    srcs = sdk_glob(
        include = ["build-tools/26.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle-core:__pkg__",
    ],
)

filegroup(
    name = "build-tools/25.0.2",
    srcs = sdk_glob(
        include = ["build-tools/25.0.2/**"],
    ),
)

filegroup(
    name = "build-tools/25.0.0",
    srcs = sdk_glob(
        include = ["build-tools/25.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle-core:__pkg__",
    ],
)

filegroup(
    name = "build-tools/24.0.3",
    srcs = sdk_glob(
        include = ["build-tools/24.0.3/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__pkg__",
    ],
)

filegroup(
    name = "platform-tools",
    srcs = sdk_glob(
        include = ["platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-28"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest-preview",
    srcs = [":platforms/android-28"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "platforms/latest_jar",
    jars = sdk_path(["platforms/android-28/android.jar"]),
    neverlink = 1,
    visibility = [
        "//tools/base/deploy/agent/instrumentation:__pkg__",
        "//tools/base/instant-run/instant-run-server:__pkg__",
        "//tools/base/profiler/app:__pkg__",
    ],
)

filegroup(
    name = "tools/support-annotations",
    srcs = sdk_glob(
        include = ["tools/support/annotations.jar"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "typos",
    srcs = sdk_glob(["tools/support/typos-*.txt"]),
    visibility = ["//visibility:public"],
)

# Version-specific rule left private in hopes we can depend on platforms/latest instead.
platform_filegroup(
    name = "platforms/android-27",
)

platform_filegroup(
    name = "platforms/android-28",
    visibility = ["//visibility:public"],  # TODO: revert visibility when platforms/latest becomes android-28
)

platform_filegroup(
    name = "platforms/android-25",
    visibility = ["//tools/adt/idea/android-uitests:__pkg__"],
)

platform_filegroup(
    name = "platforms/android-24",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/base/build-system/gradle-core:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
        "//tools/data-binding:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-23",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-21",
    visibility = ["//tools/base/build-system/integration-test:__subpackages__"],
)

platform_filegroup(
    name = "platforms/android-19",
    visibility = ["//tools/base/build-system/integration-test:__subpackages__"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-latest",
    srcs = ["add-ons/addon-google_apis-google-24"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-24",
    srcs = sdk_glob(["add-ons/addon-google_apis-google-24/**"]),
)

filegroup(
    name = "docs",
    srcs = sdk_glob(["docs/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk-bundle",
    srcs = sdk_glob(
        include = ["ndk-bundle/**"],
        exclude = [
            "ndk-bundle/platforms/android-19/**",
            "ndk-bundle/platforms/android-21/**",
        ],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk",
    srcs = sdk_glob(
        include = ["ndk/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "cmake",
    srcs = sdk_glob(
        include = ["cmake/**"],
        exclude = ["cmake/**/Help/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "sources",
    srcs = sdk_glob(["sources/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "instant-apps-sdk",
    srcs = sdk_glob(
        include = ["extras/google/instantapps/**"],
    ),
    visibility = ["//visibility:public"],
)
