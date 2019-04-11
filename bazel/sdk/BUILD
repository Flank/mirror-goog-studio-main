java_binary(
    name = "dev-sdk-updater",
    srcs = ["src/com/android/tools/utils/DevSdkUpdater.java"],
    main_class = "com.android.tools.utils.DevSdkUpdater",
    deps = [
        "//tools/base/bazel:utils",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
    ],
)

java_binary(
    name = "remote-sdk-updater",
    srcs = ["src/com/android/tools/utils/RemoteSdkUpdater.java"],
    main_class = "com.android.tools.utils.RemoteSdkUpdater",
    deps = [
        "//tools/base/annotations",
        "//tools/base/bazel:utils",
        "//tools/base/common:tools.common",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

java_library(
    name = "combine_notices_lib",
    srcs = ["src/com/android/tools/utils/CombineNotices.java"],
)

java_binary(
    name = "combine_notices",
    main_class = "com.android.tools.utils.CombineNotices",
    visibility = ["//visibility:public"],
    runtime_deps = [":combine_notices_lib"],
)

java_test(
    name = "CombineNoticesTest",
    srcs = ["testSrc/com/android/tools/utils/CombineNoticesTest.java"],
    test_class = "com.android.tools.utils.CombineNoticesTest",
    deps = [
        ":combine_notices_lib",
        "//prebuilts/tools/common/m2/repository/com/google/jimfs/jimfs/1.1:jar",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)
