java_import(
  name = "prebuilts/studio-profiler-grpc-1.0-jarjar",
  jars = [
      "prebuilts/studio-profiler-grpc-1.0-jarjar.jar",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "bazel",
  srcs = glob(["src/**/*.java"]),
  deps = ["//prebuilts/tools/common/m2:repository/org/codehaus/groovy/groovy-all/2.3.6/groovy-all-2.3.6"],
)

java_binary(
  name = "groovy_stub_gen",
  runtime_deps = [":bazel"],
  main_class = "com.android.tools.groovy.GroovyStubGenerator",
  visibility = ["//visibility:public"],
)

java_binary(
  name = "groovyc",
  runtime_deps = [":bazel"],
  main_class = "com.android.tools.groovy.GroovyCompiler",
  visibility = ["//visibility:public"],
)

java_binary(
  name = "unzipper",
  main_class = "com.android.tools.utils.Unzipper",
  runtime_deps = [":bazel"],
  visibility = ["//visibility:public"],
)

genrule(
  name = "kotlin-compiler",
  srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.0.0-release-IJ143-78.zip"],
  outs = ["kotlin-compiler.jar"],
  cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-compiler.jar:$@",
  tools = [":unzipper"],
)

genrule(
  name = "kotlin-runtime",
  srcs = ["//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-1.0.0-release-IJ143-78.zip"],
  outs = ["kotlin-runtime.jar"],
  cmd = "$(location :unzipper) $< Kotlin/kotlinc/lib/kotlin-runtime.jar:$@",
  tools = [":unzipper"],
  visibility = ["//visibility:public"],
)

java_binary(
  name = "kotlinc",
  main_class = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
  runtime_deps = [":kotlin-compiler", ":kotlin-runtime"],
  visibility = ["//visibility:public"],
)

test_suite(
  name = "all_tests",
  tests = [
    "//tools/adt/idea:android-common_tests",
    "//tools/analytics-library:analytics-publisher_tests",
    "//tools/analytics-library:analytics-shared_tests",
    "//tools/analytics-library:analytics-tracker_tests",
    "//tools/base/apkparser:binary-resources_tests",
    "//tools/base/asset-studio:assetstudio_tests",
    "//tools/base/build-system:builder-test-api_tests",
    "//tools/base/build-system:manifest-merger_tests",
    "//tools/base/common:common_tests",
    "//tools/base/device_validator:dvlib_tests",
    "//tools/base/draw9patch:draw9patch_tests",
    "//tools/base/instant-run:instant-run-client_tests",
    "//tools/base/layoutlib-api:layoutlib-api_tests",
    "//tools/base/ninepatch:ninepatch_tests",
    "//tools/base/perflib:perflib_tests",
    "//tools/base/pixelprobe:pixelprobe_tests",
    "//tools/base/repository:repository_tests",
    "//tools/base/sdk-common:sdk-common_tests",
    "//tools/base/sdklib:sdklib_tests",
    "//tools/base/testutils:testutils_tests",
    "//tools/data-binding:db-compilerCommon_tests",
    "//tools/idea:extensions_tests",
    "//tools/idea:forms-compiler_tests",
    "//tools/idea:lang-api_tests",
  ],
)
