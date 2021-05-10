load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

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
    name = "studio-profiler",
    srcs = [
        ":common-srcs-excluding-energy",
        ":echo-sample-srcs",
        ":supportlib-srcs",
    ],
    resource_jars = select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": ["//tools/base/profiler/native/agent:supportjni"],
    }),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
    ],
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
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        # TODO(b/77923456): Track version changes and revisit.
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-location/11.8.0:jar",
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
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
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
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp/2.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp3/okhttp/3.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okio/okio/1.6.0:jar",
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
    cmd = "$(location //tools/base/bazel:unzipper) $(location :perfa_okhttp_dex.jar) " +
          " classes.dex:$(location :perfa_okhttp.dex)",
    tools = ["//tools/base/bazel:unzipper"],
)
