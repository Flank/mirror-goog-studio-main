load("//tools/base/bazel:utils.bzl", "platform_filegroup")

filegroup(
    name = "licenses",
    srcs = glob(
        include = ["*/licenses/**"],
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
    jars = glob(["*/build-tools/26.0.0/lib/dx.jar"]),
)

java_binary(
    name = "dx-preview",
    main_class = "com.android.dx.command.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":dxlib-preview"],
)

filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/26.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/minimum",
    srcs = [":build-tools/25.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/26.0.0",
    srcs = glob(
        include = ["*/build-tools/26.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle:__pkg__",
    ],
)

filegroup(
    name = "build-tools/25.0.2",
    srcs = glob(
        include = ["*/build-tools/25.0.2/**"],
    ),
)

filegroup(
    name = "build-tools/25.0.0",
    srcs = glob(
        include = ["*/build-tools/25.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle:__pkg__",
    ],
)

filegroup(
    name = "build-tools/24.0.3",
    srcs = glob(
        include = ["*/build-tools/24.0.3/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__pkg__",
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
    name = "navigation-runtime_latest",
    srcs = [":navigation-runtime_0.0.1-SNAPSHOT"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "navigation-runtime_0.0.1-SNAPSHOT",
    srcs = glob(
        include = [
            "*/extras/m2repository/com/android/support/navigation/navigation-runtime/0.0.1-SNAPSHOT/**",
        ],
    ),
)

filegroup(
    name = "constraint-layout_latest",
    srcs = [":constraint-layout_1.0.2"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "constraint-layout_1.0.2",
    srcs = glob(
        include = [
            "*/extras/m2repository/com/android/support/constraint/constraint-layout/1.0.2/**",
            "*/extras/m2repository/com/android/support/constraint/constraint-layout-solver/1.0.2/**",
        ],
    ),
)

filegroup(
    name = "support_latest",
    srcs = [":support_25.3.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "support_25.3.1",
    srcs = glob([
        "*/extras/android/m2repository/com/android/support/*/25.3.1/**",
    ]),
)

filegroup(
    name = "uiautomator_latest",
    srcs = [":uiautomator_2.1.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "uiautomator_2.1.1",
    srcs = glob([
        "*/extras/android/m2repository/com/android/support/test/uiautomator/uiautomator-v18/2.1.1/**",
    ]),
)

filegroup(
    name = "gms_latest",
    srcs = [":gms_9.6.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gms_9.6.1",
    srcs = glob(["*/extras/google/m2repository/com/google/android/gms/*/9.6.1/**"]),
)

filegroup(
    name = "databinding_latest",
    srcs = glob(["*/extras/android/m2repository/com/android/databinding/*/1.3.1/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "multidex",
    srcs = glob(["*/extras/android/m2repository/com/android/support/multidex*/1.0.1/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-26"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest-preview",
    srcs = [":platforms/android-26"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "platforms/latest_jar",
    jars = glob(["*/platforms/android-26/android.jar"]),
    neverlink = 1,
    visibility = [
        "//tools/base/build-system/instant-run-instrumentation:__pkg__",
        "//tools/base/instant-run/instant-run-server:__pkg__",
        "//tools/base/profiler/app:__pkg__",
    ],
)

filegroup(
    name = "typos",
    srcs = glob(["*/tools/support/typos-*.txt"]),
    visibility = ["//visibility:public"],
)

# Version-specific rule left private in hopes we can depend on platforms/latest instead.
platform_filegroup(
    name = "platforms/android-26",
)

platform_filegroup(
    name = "platforms/android-24",
    visibility = [
        "//tools/base/build-system/gradle:__pkg__",
        "//tools/base/build-system/integration-test:__pkg__",
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
    name = "espresso_latest",
    srcs = [":espresso-2.2.2"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "espresso-2.2.2",
    srcs = glob(
        include = [
            "*/extras/android/m2repository/com/android/support/test/espresso/espresso-core/2.2.2/**",
            "*/extras/android/m2repository/com/android/support/test/espresso/espresso-idling-resource/2.2.2/**",
        ],
    ),
)

filegroup(
    name = "test-runner_latest",
    srcs = [":test-runner-0.5"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "test-runner-0.5",
    srcs = glob(
        include = [
            "*/extras/android/m2repository/com/android/support/test/exposed-instrumentation-api-publish/0.5/**",
            "*/extras/android/m2repository/com/android/support/test/rules/0.5/**",
            "*/extras/android/m2repository/com/android/support/test/runner/0.5/**",
        ],
    ),
)

filegroup(
    name = "wearable_latest",
    srcs = [":wearable-2.0.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "wearable-2.0.1",
    srcs = glob(
        include = [
            "*/extras/google/m2repository/com/google/android/*/wearable/2.0.1/**",
        ],
    ),
)

filegroup(
    name = "docs",
    srcs = glob(["*/docs/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk-bundle",
    srcs = glob(["*/ndk-bundle/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "cmake",
    srcs = glob(
        include = ["*/cmake/**"],
        exclude = ["*/cmake/**/Help/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "sources",
    srcs = glob(["*/sources/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "instant-apps-sdk",
    srcs = glob(
        include = ["*/extras/google/instantapps/**"],
    ),
    visibility = ["//visibility:public"],
)
