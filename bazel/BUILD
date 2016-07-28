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

