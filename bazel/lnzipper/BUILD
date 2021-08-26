# Package ziplinker contains a binary for creating ZIP files while preserving symlinks

java_library(
    name = "lnzipper_lib",
    srcs = ["src/main/java/com/android/tools/bazel/lnzipper/LnZipper.java"],
    deps = [
        "//tools/base/zipflinger",
        "@maven//:commons-cli.commons-cli",
    ],
)

java_test(
    name = "lnzipper_test",
    srcs = ["src/test/java/com/android/tools/bazel/lnzipper/LnZipperTest.java"],
    test_class = "com.android.tools.bazel.lnzipper.LnZipperTest",
    deps = [
        ":lnzipper_lib",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:commons-cli.commons-cli",
        "@maven//:junit.junit",
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
