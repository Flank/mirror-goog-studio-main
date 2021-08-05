# Package ziplinker contains a binary for creating ZIP files while preserving symlinks

java_library(
    name = "lnzipper_lib",
    srcs = ["src/main/java/com/android/tools/bazel/lnzipper/LnZipper.java"],
    deps = [
        "//prebuilts/tools/common/m2:commons-cli.commons-cli.1.4",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "lnzipper_test",
    srcs = ["src/test/java/com/android/tools/bazel/lnzipper/LnZipperTest.java"],
    test_class = "com.android.tools.bazel.lnzipper.LnZipperTest",
    deps = [
        ":lnzipper_lib",
        "//prebuilts/tools/common/m2:com.google.truth.truth.0.42",
        "//prebuilts/tools/common/m2:commons-cli.commons-cli.1.4",
        "//prebuilts/tools/common/m2:junit.junit.4.12",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
    ],
)

java_binary(
    name = "lnzipper",
    main_class = "com.android.tools.bazel.lnzipper.LnZipper",
    visibility = [
        "//prebuilts/studio/intellij-sdk:__pkg__",
        "//tools/adt/idea/studio:__pkg__",
    ],
    runtime_deps = [
        ":lnzipper_lib",
    ],
)
