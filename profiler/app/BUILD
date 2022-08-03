load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "supportlib-srcs",
    srcs = glob(["supportlib/src/main/**/*.java"]),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "echo-sample-srcs",
    srcs = glob(["echo-sample/src/main/**/*.java"]),
    visibility = ["//visibility:private"],
)

# Since Energy profiler doesn't support compile-time instrumentation, don't include the src
# to avoid adding its dependencies.
filegroup(
    name = "common-srcs-excluding-energy",
    srcs = glob(
        include = ["common/src/main/**/*.java"],
        exclude = ["common/src/main/java/com/android/tools/profiler/support/energy/**/*.java"],
    ),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "common-srcs",
    srcs = glob(["common/src/main/**/*.java"]),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "perfa-srcs",
    srcs = glob(["perfa/src/main/**/*.java"]),
    visibility = ["//visibility:private"],
)

java_library(
    name = "studio-profiler-core",
    srcs = [
        ":common-srcs-excluding-energy",
        ":echo-sample-srcs",
        ":supportlib-srcs",
    ],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
    ],
)

merge_jars(
    name = "studio-profiler",
    out = "studio-profiler.jar",
    jars = ["libstudio-profiler-core.jar"] + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": ["//tools/base/profiler/native/agent:supportjni"],
    }),
)

java_library(
    name = "perfa_java_core",
    srcs = [
        ":common-srcs",
        ":echo-sample-srcs",
        ":perfa-srcs",
    ],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        # TODO(b/77923456): Track version changes and revisit.
        "//prebuilts/tools/common/m2:com.google.android.gms.play-services-location.11.8.0",
        "@maven//:androidx.annotation.annotation",
    ],
)

java_jarjar(
    name = "perfa_java",
    srcs = [
        ":perfa_java_core",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)

dex_library(
    name = "perfa",
    jars = [
        ":perfa_java",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/app-inspection/agent",
    ],
)

java_library(
    name = "perfa_okhttp_java",
    srcs = glob([
        "perfa-okhttp/src/main/**/*.java",
    ]),
    deps = [
        ":perfa_java",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
        "@maven//:com.squareup.okhttp.okhttp",
        "@maven//:com.squareup.okhttp3.okhttp",
        "@maven//:com.squareup.okio.okio",
    ],
)

dex_library(
    name = "perfa_okhttp_dex_jar",
    jars = [":perfa_okhttp_java"],
    output = "perfa_okhttp_dex.jar",
)

# D8 does not allow generating multiple classes to a single dex file because
# of multidex. We are going to tell it to output to a zip file first and
# then extract classes.dex to it as our perfa_okhttp.dex. If this dex file
# over flows, add classes02.dex to it (and whatever needs to be done to upload
# to the device as well)
genrule(
    name = "perfa_okhttp",
    srcs = [":perfa_okhttp_dex.jar"],
    outs = ["perfa_okhttp.dex"],
    # Assumes everything fits into classes.dex, add classes02.dex should when it is full
    cmd = "$(location //tools/base/bazel:unzipper) $(location :perfa_okhttp_dex.jar) classes.dex:$(location :perfa_okhttp.dex)",
    tools = ["//tools/base/bazel:unzipper"],
)
