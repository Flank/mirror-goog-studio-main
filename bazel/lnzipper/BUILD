# Package ziplinker contains a binary for creating ZIP files while preserving symlinks

java_library(
    name = "lnzipper_lib",
    srcs = ["src/main/java/com/android/tools/bazel/lnzipper/LnZipper.java"],
    deps = [
        "//prebuilts/tools/common/m2/repository/commons-cli/commons-cli/1.4:jar",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "lnzipper_test",
    srcs = ["src/test/java/com/android/tools/bazel/lnzipper/LnZipperTest.java"],
    test_class = "com.android.tools.bazel.lnzipper.LnZipperTest",
    deps = [
        ":lnzipper_lib",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar",
        "//prebuilts/tools/common/m2/repository/commons-cli/commons-cli/1.4:jar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/zipflinger",
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
