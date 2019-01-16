load("//tools/base/bazel:android.bzl", "dex_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "studio-profiler",
    srcs = glob(
        include = [
            "supportlib/src/main/**/*.java",
            "common/src/main/**/*.java",
            # Need to include this here as platforms/latest_jar is not visible outside this package.
            "echo-sample/src/main/**/*.java",
        ],
        # Since Energy profiler doesn't support compile-time instrumentation, don't include the src
        # to avoid adding its dependencies.
        exclude = ["common/src/main/java/com/android/tools/profiler/support/energy/**/*.java"],
    ),
    resource_jars = select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": ["//tools/base/profiler/native/agent:supportjni"],
    }),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

java_library(
    name = "perfa_java",
    srcs = glob([
        "perfa/src/main/**/*.java",
        "common/src/main/**/*.java",
        # Need to include this here as platforms/latest_jar is not visible outside this package.
        "echo-sample/src/main/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        # TODO(b/77923456): Track version changes and revisit.
        "//prebuilts/tools/common/m2/repository/com/google/android/gms/play-services-location/11.8.0:jar",
    ],
)

dex_library(
    name = "perfa",
    jars = [":perfa_java"],
)

java_library(
    name = "perfa_okhttp_java",
    srcs = glob([
        "perfa-okhttp/src/main/**/*.java",
    ]),
    deps = [
        ":perfa_java",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp/2.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp3/okhttp/3.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okio/okio/1.6.0:jar",
    ],
)

dex_library(
    name = "perfa_okhttp",
    jars = [":perfa_okhttp_java"],
    output = "perfa_okhttp.dex",
)
