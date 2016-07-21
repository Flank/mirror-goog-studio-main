exports_files([
  "kotlin/bin/kotlin",
  "kotlin/bin/kotlin.bat",
  "kotlin/bin/kotlinc",
  "kotlin/bin/kotlinc-js",
  "kotlin/bin/kotlinc-js.bat",
  "kotlin/bin/kotlinc-jvm",
  "kotlin/bin/kotlinc-jvm.bat",
  "kotlin/bin/kotlinc.bat",
  "kotlin/lib/android-compiler-plugin.jar",
  "kotlin/lib/kotlin-android-sdk-annotations.jar",
  "kotlin/lib/kotlin-ant.jar",
  "kotlin/lib/kotlin-build-common.jar",
  "kotlin/lib/kotlin-compiler.jar",
  "kotlin/lib/kotlin-daemon-client.jar",
  "kotlin/lib/kotlin-jdk-annotations.jar",
  "kotlin/lib/kotlin-jslib-sources.jar",
  "kotlin/lib/kotlin-jslib.jar",
  "kotlin/lib/kotlin-preloader.jar",
  "kotlin/lib/kotlin-reflect.jar",
  "kotlin/lib/kotlin-runner.jar",
  "kotlin/lib/kotlin-runtime-sources.jar",
  "kotlin/lib/kotlin-runtime.jar",
], ["//visibility:public"])

filegroup(
    name = "kotlin-sdk",
    srcs = glob(["kotlin/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "groovy-sdk",
    srcs = glob(["groovy/**"], exclude_directories=0),
    visibility = ["//visibility:public"],
)

exports_files([
  "groovy/bin/groovyc",
], ["//visibility:public"])

java_import(
  name = "prebuilts/studio-profiler-grpc-1.0-jarjar",
  jars = [
      "prebuilts/studio-profiler-grpc-1.0-jarjar.jar",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "test_runner",
  srcs = glob(["tests/**/*.java"]),
  visibility = ["//visibility:public"],
  deps = ["//prebuilts/tools/common/m2:repository/junit/junit/4.12/junit-4.12"],
)
