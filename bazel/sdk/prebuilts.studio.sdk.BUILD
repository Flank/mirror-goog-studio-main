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
    srcs = [":build-tools/30.0.2"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/29.0.2",
    srcs = sdk_glob(
        include = ["build-tools/29.0.2/**"],
    ),
    visibility = [
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/base/build-system/previous-versions:__pkg__",
    ],
)

filegroup(
    name = "build-tools/28.0.3",
    srcs = sdk_glob(
        include = ["build-tools/28.0.3/**"],
    ),
    visibility = [
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
        "//prebuilts/studio/buildbenchmarks:__pkg__",
    ],
)

filegroup(
    name = "build-tools/minimum",
    srcs = [":build-tools/25.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/30.0.2",
    srcs = sdk_glob(
        include = ["build-tools/30.0.2/**"],
    ),
    visibility = ["//visibility:private"],
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
    name = "platforms/latest_build_only",
    srcs = [":platforms/android-30_build_only"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-30"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest-preview",
    srcs = [":platforms/android-30"],  # Currently there isn't a preview available
    visibility = ["//visibility:public"],
)

# Use this target to compile against.
# Note: these stubbed classes will not be available at runtime.
java_import(
    name = "platforms/latest_jar",
    jars = sdk_path(["platforms/android-30/android.jar"]),
    neverlink = 1,
    visibility = [
        "//tools/base/app-inspection/agent:__pkg__",
        "//tools/base/app-inspection/inspectors:__subpackages__",
        "//tools/base/deploy/agent/runtime:__pkg__",
        "//tools/base/profiler/app:__pkg__",
        "//tools/base/dynamic-layout-inspector/agent:__subpackages__",
        "//tools/base/experimental/live-sql-inspector:__pkg__",
    ],
)

# Use this target for tests that need the presence of the android classes during test runs.
# Note: these are stubbed classes.
java_import(
    name = "platforms/latest_runtime_jar",
    jars = sdk_path(["platforms/android-30/android.jar"]),
    testonly = 1,
    visibility = [
        "//tools/base/dynamic-layout-inspector/agent:__subpackages__",
        "//tools/base/app-inspection/inspectors:__subpackages__",
    ],
)

# Version-specific rule left private in hopes we can depend on platforms/latest instead.
platform_filegroup(
    name = "platforms/android-30",
    #visibility = ["//visibility:private"],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-29",
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-28",
    visibility = [
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/vendor/google/lldb-integration-tests:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-27",
    # TODO: Restrict the visibility of this group. Although the comment above says "private", the default
    # visibility is public.
)

platform_filegroup(
    name = "platforms/android-25",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/vendor/google/android-apk:__subpackages__",
    ],
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
    name = "emulator",
    srcs = sdk_glob(
        include = ["emulator/**"],
    ),
    visibility = ["//visibility:public"],
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
        include = ["ndk/21.4.7075529/**"],
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
