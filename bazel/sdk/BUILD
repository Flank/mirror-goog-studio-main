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
